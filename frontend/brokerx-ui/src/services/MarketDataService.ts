import { Client } from '@stomp/stompjs';
import type { IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

// Types pour les données de marché
interface MarketQuote {
  symbol: string;
  bid: number;
  ask: number;
  last: number;
  volume: number;
  change: number;
  changePercent: number;
  timestamp: string;
}

interface OrderBookLevel {
  price: number;
  quantity: number;
}

interface OrderBook {
  symbol: string;
  bids: OrderBookLevel[];
  asks: OrderBookLevel[];
  timestamp: string;
}

interface SubscriptionRequest {
  symbols: string[];
  subscriptionType: 'quotes' | 'orderbook';
  rateLimit: 'standard' | 'premium';
}

// Types pour les callbacks
type QuoteCallback = (quote: MarketQuote) => void;
type OrderBookCallback = (orderBook: OrderBook) => void;

class MarketDataService {
  private client: Client | null = null;
  private isConnected: boolean = false;
  private subscriptions: Map<string, StompSubscription> = new Map();

  // Connexion au service WebSocket BrokerX
  async connect(token: string): Promise<void> {
    // Éviter les reconnexions multiples
    if (this.isConnected && this.client) {
      console.log('WebSocket déjà connecté, réutilisation de la connexion existante');
      return;
    }

    return new Promise((resolve, reject) => {
      // Send JWT token as URL parameter (required by handshake interceptor)
      const socket = new SockJS(`http://localhost:8086/ws/market-data?token=${token}`);
      console.log('Creating SockJS connection to MarketData service with token:', token.substring(0, 20) + '...');
      
      this.client = new Client({
        webSocketFactory: () => socket,
        debug: (str) => {
          console.log('[MarketData STOMP Debug]:', str);
        },
        onConnect: (frame) => {
          console.log('Connecté au MarketData WebSocket:', frame.command);
          this.isConnected = true;
          resolve();
        },
        onStompError: (frame) => {
          console.error('Erreur WebSocket STOMP:', frame);
          console.error('STOMP Error Headers:', frame.headers);
          console.error('STOMP Error Body:', frame.body);
          this.isConnected = false;
          reject(new Error(`Erreur STOMP: ${frame.headers['message'] || frame.body || 'Erreur inconnue'}`));
        },
        onDisconnect: (frame) => {
          console.log('WebSocket déconnecté:', frame);
          this.isConnected = false;
        },
        onWebSocketError: (error) => {
          console.error('Erreur WebSocket:', error);
          console.error('WebSocket Error Type:', typeof error);
          console.error('WebSocket Error Details:', error);
          reject(new Error('Erreur de connexion WebSocket: ' + (error?.message || error)));
        }
      });

      this.client.activate();

      // Timeout de connexion
      setTimeout(() => {
        if (!this.isConnected) {
          reject(new Error('Timeout de connexion WebSocket (10s)'));
        }
      }, 10000);
    });
  }

  // S'abonner aux cotations en temps réel
  subscribeToQuotes(symbols: string[], onQuoteReceived: QuoteCallback): void {
    if (!this.isConnected || !this.client) {
      throw new Error('WebSocket non connecté');
    }

    // Vérifier si on est déjà abonné à ces symboles
    const alreadySubscribed = symbols.some(symbol => 
      this.subscriptions.has(`quotes_${symbol}`)
    );
    
    if (alreadySubscribed) {
      console.log('Déjà abonné aux cotations pour:', symbols);
      return;
    }

    // Envoyer la demande d'abonnement
    const subscriptionRequest: SubscriptionRequest = {
      symbols: symbols,           // ["AAPL", "TSLA", "GOOGL"]
      subscriptionType: 'quotes', // Type de données
      rateLimit: 'standard'       // Limite de taux
    };

    this.client.publish({
      destination: '/app/subscribe',
      body: JSON.stringify(subscriptionRequest)
    });

    // Écouter les cotations reçues
    const subscription = this.client.subscribe('/user/queue/quotes', (message: IMessage) => {
      try {
        const quote: MarketQuote = JSON.parse(message.body);
        console.log('Quote reçue:', quote);
        onQuoteReceived(quote); // Callback vers React
      } catch (error) {
        console.error('Erreur parsing quote:', error);
      }
    });

    // Stocker les abonnements pour nettoyage ultérieur
    for (const symbol of symbols) {
      this.subscriptions.set(`quotes_${symbol}`, subscription);
    }
  }

  // S'abonner au carnet d'ordres
  subscribeToOrderBook(symbol: string, onOrderBookReceived: OrderBookCallback): void {
    if (!this.isConnected || !this.client) {
      throw new Error('WebSocket non connecté');
    }

    const subscriptionRequest: SubscriptionRequest = {
      symbols: [symbol],
      subscriptionType: 'orderbook',
      rateLimit: 'premium'
    };

    this.client.publish({
      destination: '/app/subscribe',
      body: JSON.stringify(subscriptionRequest)
    });

    const subscription = this.client.subscribe('/user/queue/orderbook', (message: IMessage) => {
      try {
        const orderBook: OrderBook = JSON.parse(message.body);
        console.log('OrderBook reçu:', orderBook);
        onOrderBookReceived(orderBook);
      } catch (error) {
        console.error('Erreur parsing orderbook:', error);
      }
    });

    this.subscriptions.set(`orderbook_${symbol}`, subscription);
  }

  // Déconnexion propre
  disconnect(): void {
    try {
      // Désabonner tous les abonnements actifs
      for (const [key, subscription] of this.subscriptions) {
        subscription.unsubscribe();
        console.log(`Désabonnement: ${key}`);
      }
      this.subscriptions.clear();

      // Fermer la connexion WebSocket
      if (this.client) {
        this.client.deactivate();
        console.log('Client WebSocket désactivé');
      }

      this.isConnected = false;
    } catch (error) {
      console.error('Erreur lors de la déconnexion:', error);
    }
  }

  // Getters utilitaires
  get connected(): boolean {
    return this.isConnected;
  }

  get activeSubscriptions(): string[] {
    return Array.from(this.subscriptions.keys());
  }
}

// Export singleton
export default new MarketDataService();