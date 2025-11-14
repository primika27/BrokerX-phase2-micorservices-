import { Client } from '@stomp/stompjs';
import type { IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

// Types pour les notifications d'ordres
export interface OrderUpdate {
  orderId: number;
  symbol: string;
  orderType: string;
  quantity: number;
  price: number;
  status: string;
  previousStatus: string;
  timestamp: string;
  message: string;
}

// Type pour les callbacks
type OrderUpdateCallback = (update: OrderUpdate) => void;

class OrderNotificationService {
  private client: Client | null = null;
  private isConnected: boolean = false;
  private subscription: StompSubscription | null = null;
  private callback: OrderUpdateCallback | null = null;

  /**
   * Connect to OrderService WebSocket for real-time order updates
   */
  async connect(token: string): Promise<void> {
    // Avoid multiple connections
    if (this.isConnected && this.client) {
      console.log('OrderNotification WebSocket already connected, reusing existing connection');
      return;
    }

    return new Promise((resolve, reject) => {
      // Use SockJS for fallback support
      const socket = new SockJS('http://localhost:8080/api/orders/ws/order-updates');

      this.client = new Client({
        webSocketFactory: () => socket,
        connectHeaders: {
          Authorization: `Bearer ${token}`,
        },
        debug: (str) => {
          console.log('[OrderNotification STOMP]:', str);
        },
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        onConnect: () => {
          console.log('OrderNotification WebSocket connected successfully');
          this.isConnected = true;
          resolve();
        },
        onStompError: (frame) => {
          console.error('OrderNotification STOMP error:', frame.headers['message']);
          console.error('Details:', frame.body);
          reject(new Error(frame.headers['message']));
        },
        onWebSocketError: (event) => {
          console.error('OrderNotification WebSocket error:', event);
          reject(event);
        },
        onDisconnect: () => {
          console.log('OrderNotification WebSocket disconnected');
          this.isConnected = false;
        }
      });

      this.client.activate();
    });
  }

  /**
   * Subscribe to order updates
   * User-specific subscription: /user/queue/order-updates
   */
  subscribeToOrderUpdates(callback: OrderUpdateCallback): void {
    if (!this.client || !this.isConnected) {
      console.error('Cannot subscribe: client not connected');
      return;
    }

    if (this.subscription) {
      console.log('Already subscribed to order updates');
      return;
    }

    this.callback = callback;

    // Subscribe to user-specific order updates
    this.subscription = this.client.subscribe('/user/queue/order-updates', (message: IMessage) => {
      try {
        const update: OrderUpdate = JSON.parse(message.body);
        console.log('Received order update:', update);
        
        if (this.callback) {
          this.callback(update);
        }
      } catch (error) {
        console.error('Failed to parse order update:', error);
      }
    });

    // Notify server that we want to subscribe
    this.client.publish({
      destination: '/app/orders/subscribe',
      body: JSON.stringify({ subscribe: true })
    });

    console.log('Subscribed to order updates');
  }

  /**
   * Unsubscribe from order updates
   */
  unsubscribe(): void {
    if (this.subscription) {
      this.subscription.unsubscribe();
      this.subscription = null;
      console.log('Unsubscribed from order updates');
    }
  }

  /**
   * Disconnect from WebSocket
   */
  disconnect(): void {
    this.unsubscribe();
    
    if (this.client) {
      this.client.deactivate();
      this.client = null;
    }
    
    this.isConnected = false;
    this.callback = null;
    console.log('OrderNotification WebSocket disconnected');
  }

  /**
   * Check if connected
   */
  isActive(): boolean {
    return this.isConnected && this.client !== null;
  }
}

// Export singleton instance
const orderNotificationService = new OrderNotificationService();
export default orderNotificationService;
