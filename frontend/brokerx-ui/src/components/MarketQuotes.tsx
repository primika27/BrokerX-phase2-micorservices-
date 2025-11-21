
import React, { useState, useEffect } from 'react';
import MarketDataService from '../services/MarketDataService';
import '../main/App.css'; 


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

interface MarketQuotesProps {
  userToken: string | null;
}

const MarketQuotes: React.FC<MarketQuotesProps> = ({ userToken }) => {
  const [quotes, setQuotes] = useState<Record<string, MarketQuote>>({});
  const [subscribedSymbols] = useState<string[]>(['AAPL', 'TSLA', 'GOOGL']);
  const [connectionStatus, setConnectionStatus] = useState<'connecting' | 'connected' | 'error' | 'disconnected'>('disconnected');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!userToken) {
      setError('Token JWT manquant');
      return;
    }

    let isSubscribed = true;

    const connectAndSubscribe = async () => {
      try {
        setConnectionStatus('connecting');
        setError(null);
        
        console.log('Connexion WebSocket BrokerX...');
        
        // Connexion WebSocket
        await MarketDataService.connect(userToken);
        
        if (!isSubscribed) return;
        
        setConnectionStatus('connected');
        console.log('Connecté au MarketData WebSocket');
        
        // Abonnement aux cotations avec type explicite
        MarketDataService.subscribeToQuotes(
          subscribedSymbols,
          (newQuote: MarketQuote) => {
            if (isSubscribed) {
              console.log('Nouvelle cotation reçue:', newQuote);
              setQuotes(prevQuotes => ({
                ...prevQuotes,
                [newQuote.symbol]: newQuote
              }));
            }
          }
        );

      } catch (error) {
        if (isSubscribed) {
          const errorMessage = error instanceof Error ? error.message : 'Erreur inconnue';
          setError('Erreur WebSocket: ' + errorMessage);
          setConnectionStatus('error');
          console.error('Erreur WebSocket:', error);
        }
      }
    };

    connectAndSubscribe();

    // Nettoyage à la déconnexion (SEULEMENT si le token change ou si le composant est vraiment démonté)
    return () => {
      isSubscribed = false;
      // NE PAS déconnecter automatiquement - laissons la connexion ouverte
      console.log('MarketQuotes cleanup - connexion maintenue');
    };
  }, [userToken, subscribedSymbols]);

  //  Fonctions d'affichage
  const formatPrice = (price: number): string => {
    return price.toFixed(2);
  };

  const formatTime = (timestamp: string): string => {
    return new Date(timestamp).toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: true,
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone
    });
  };

  const getConnectionStatusDisplay = () => {
    const statusConfig = {
      connecting: { text: 'Connexion...', className: 'status-connecting' },
      connected: { text: 'Connecté', className: 'status-connected' },
      error: { text: 'Erreur', className: 'status-error' },
      disconnected: { text: ' Déconnecté', className: 'status-disconnected' }
    };
    
    return statusConfig[connectionStatus];
  };

  //  Gestion des erreurs
  if (error) {
    return (
      <div className="market-quotes">
        <div className="header">
          <div className="connection-status status-error"> Erreur</div>
        </div>
        <div className="error-message">
           {error}
        </div>
      </div>
    );
  }

  const statusDisplay = getConnectionStatusDisplay();

  return (
    <div className="market-quotes">
      <div className="header">
        <h2>Données de Marché en Temps Réel</h2>
        <div className={`connection-status ${statusDisplay.className}`}>
          {statusDisplay.text}
        </div>
      </div>

      <div className="quotes-grid">
        {subscribedSymbols.map(symbol => {
          const quote = quotes[symbol];
          
          if (!quote) {
            return (
              <div key={symbol} className="quote-card loading">
                <h3>{symbol}</h3>
                <div className="loading-text">⏳ Loading...</div>
              </div>
            );
          }

          const isPositive = quote.change >= 0;
          const changeClass = isPositive ? 'change-positive' : 'change-negative';
          const changeSymbol = isPositive ? '+' : '';

          return (
            <div key={symbol} className="quote-card">
              <div className="quote-header">
                <h3>{quote.symbol}</h3>
                <div className="timestamp">{formatTime(quote.timestamp)}</div>
              </div>
              
              <div className="price-section">
                <div className="last-price">
                  <span className="label">Last Price:</span>
                  <span className="value">${formatPrice(quote.last)}</span>
                </div>
                <div className={`change-section ${changeClass}`}>
                  <span className="label">Change:</span>
                  <span className="value">{changeSymbol}{formatPrice(quote.change)} ({changeSymbol}{quote.changePercent.toFixed(2)}%)</span>
                </div>
              </div>
              
              <div className="bid-ask-section">
                <div className="bid">
                  <span className="label">Bid:</span>
                  <span className="value">${formatPrice(quote.bid)}</span>
                </div>
                <div className="ask">
                  <span className="label">Ask:</span>
                  <span className="value">${formatPrice(quote.ask)}</span>
                </div>
              </div>
              
              <div className="volume-section">
                <span className="label">Volume:</span>
                <span className="value">{quote.volume.toLocaleString('fr-FR')}</span>
              </div>
            </div>
          );
        })}
      </div>

      {connectionStatus === 'connected' && (
        <div className="info-section">
        </div>
      )}
    </div>
  );
};

export default MarketQuotes;