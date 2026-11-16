import SockJS from 'sockjs-client';
import { Client, Message } from 'stompjs';
import { ProcessingEvent } from '../types/document';
import { authService, getCsrfToken } from './api';

type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'reconnecting';

class WebSocketService {
  private stompClient: Client | null = null;
  private subscribers = new Set<(event: ProcessingEvent) => void>();
  private connectionState: ConnectionState = 'disconnected';
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 10;
  private reconnectDelay = 1000; // Start with 1 second
  private reconnectTimer: NodeJS.Timeout | null = null;
  private connectionListeners: Set<(state: ConnectionState) => void> = new Set();
  private isManualDisconnect = false;
  private connectionGeneration = 0;
  private socket: { close(code?: number, reason?: string): void } | null = null;

  connect() {
    if (this.connectionState === 'connected' || this.connectionState === 'connecting') {
      return;
    }

    this.isManualDisconnect = false;
    const generation = ++this.connectionGeneration;
    this.connectionState = 'connecting';
    this.notifyConnectionState();

    // First check if backend is reachable
    this.checkBackendAvailability().then((available) => {
      if (this.isManualDisconnect || generation !== this.connectionGeneration) return;
      if (!available) {
        console.warn('Backend not available, will retry connection');
        this.handleConnectionFailure();
        return;
      }

      this.attemptConnection();
    }).catch(() => {
      this.handleConnectionFailure();
    });
  }

  private async checkBackendAvailability(): Promise<boolean> {
    try {
      // Session validation precedes every connection/reconnection attempt.
      return (await authService.session()).authenticated;
    } catch (error) {
      console.warn('Backend availability check error:', error);
      return false;
    }
  }

  private attemptConnection() {
    if (this.isManualDisconnect) return;
    try {
      const wsUrl = process.env.REACT_APP_WS_URL || '/ws';
      const socket = new SockJS(wsUrl);
      this.socket = socket;
      // @ts-ignore - stompjs doesn't have proper TypeScript definitions
      const Stomp = require('stompjs');
      const client = Stomp.over(socket);

      if (!client) {
        console.error('Failed to create STOMP client');
        this.handleConnectionFailure();
        return;
      }

      // Enable debug for troubleshooting (can be disabled later)
      // Set to null to disable, or use a function to log
      client.debug = null; // Disable verbose logging by default

      this.stompClient = client;

      // Add error handler for socket
      socket.onerror = (error) => {
        console.error('Socket error:', error);
        this.handleConnectionFailure();
      };

      socket.onclose = (event) => {
        if (!this.isManualDisconnect && this.connectionState !== 'disconnected') {
          console.log('Socket closed:', event.code, event.reason);
          this.handleConnectionFailure();
        }
      };

      socket.onopen = () => {
        console.log('SockJS connection opened');
      };

      this.stompClient.connect({ 'X-CSRF-TOKEN': getCsrfToken() }, () => {
        console.log('WebSocket connected successfully');
        this.connectionState = 'connected';
        this.reconnectAttempts = 0;
        this.reconnectDelay = 1000;
        this.notifyConnectionState();

        // Subscribe to general processing topic
        this.stompClient!.subscribe('/topic/processing', (message: Message) => {
          try {
            const event: ProcessingEvent = JSON.parse(message.body);
            this.subscribers.forEach(callback => callback(event));
          } catch (error) {
            console.error('Error parsing WebSocket message:', error);
          }
        });
      }, (error: any) => {
        console.error('STOMP connection error:', error);
        this.handleConnectionFailure();
      });
    } catch (error) {
      console.error('Error creating WebSocket connection:', error);
      this.handleConnectionFailure();
    }
  }

  private handleConnectionFailure() {
    if (this.isManualDisconnect) {
      return;
    }

    this.connectionState = 'reconnecting';
    this.notifyConnectionState();

    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('Max reconnection attempts reached. Backend may not be running on http://localhost:8080');
      this.connectionState = 'disconnected';
      this.notifyConnectionState();
      return;
    }

    this.reconnectAttempts++;
    // Use exponential backoff with max delay of 10 seconds (instead of 30)
    const delay = Math.min(this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1), 10000);

    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
    }

    this.reconnectTimer = setTimeout(() => {
      const generation = this.connectionGeneration;
      console.log(`Reconnection attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts} - Checking backend availability...`);
      // Re-check backend availability before attempting connection
      this.checkBackendAvailability().then((available) => {
        if (this.isManualDisconnect || generation !== this.connectionGeneration) return;
        if (available) {
          console.log('Backend is available, attempting WebSocket connection...');
          this.attemptConnection();
        } else {
          console.warn(`Backend not available (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts}), will retry in ${delay}ms...`);
          this.handleConnectionFailure();
        }
      }).catch(() => {
        this.handleConnectionFailure();
      });
    }, delay);
  }

  onConnectionStateChange(callback: (state: ConnectionState) => void) {
    this.connectionListeners.add(callback);
    // Immediately notify of current state
    callback(this.connectionState);
    return () => {
      this.connectionListeners.delete(callback);
    };
  }

  getConnectionState(): ConnectionState {
    return this.connectionState;
  }

  private notifyConnectionState() {
    this.connectionListeners.forEach(callback => callback(this.connectionState));
  }

  subscribeToAll(callback: (event: ProcessingEvent) => void) {
    this.subscribers.add(callback);
    return () => this.subscribers.delete(callback);
  }

  disconnect() {
    this.isManualDisconnect = true;
    this.connectionGeneration++;

    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }

    if (this.stompClient) {
      if (this.stompClient.connected) {
        try {
          this.stompClient.disconnect(() => {
            console.log('WebSocket disconnected');
          });
        } catch (error) {
          console.log('WebSocket disconnect error (ignored):', error);
        }
      }
      this.stompClient = null;
    }
    if (this.socket) {
      try { this.socket.close(); } catch { /* Already closed. */ }
      this.socket = null;
    }

    this.connectionState = 'disconnected';
    this.notifyConnectionState();
    this.subscribers.clear();
  }
}

export const webSocketService = new WebSocketService();
