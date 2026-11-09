import SockJS from 'sockjs-client';
import { Client, Message } from 'stompjs';
import { ProcessingEvent } from '../types/document';

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

  connect() {
    if (this.connectionState === 'connected' || this.connectionState === 'connecting') {
      return;
    }

    this.isManualDisconnect = false;
    this.connectionState = 'connecting';
    this.notifyConnectionState();

    // First check if backend is reachable
    this.checkBackendAvailability().then((available) => {
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
      // Try to connect to the API endpoint with a short timeout
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 2000); // 2 second timeout

      try {
        const apiUrl = process.env.REACT_APP_API_URL || 'http://localhost:8080/api';
        const response = await fetch(`${apiUrl}/documents`, {
          method: 'GET',
          mode: 'cors',
          cache: 'no-cache',
          signal: controller.signal,
        });
        clearTimeout(timeoutId);
        // Any response (even 404 or 500) means backend is running
        return true;
      } catch (fetchError: any) {
        clearTimeout(timeoutId);
        if (fetchError.name === 'AbortError') {
          console.warn('Backend availability check timed out');
        } else {
          console.warn('Backend availability check failed:', fetchError.message);
        }
        return false;
      }
    } catch (error) {
      console.warn('Backend availability check error:', error);
      return false;
    }
  }

  private attemptConnection() {
    try {
      const wsUrl = process.env.REACT_APP_WS_URL || 'http://localhost:8080/ws';
      const socket = new SockJS(wsUrl);
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

      this.stompClient.connect({}, () => {
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
      console.log(`Reconnection attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts} - Checking backend availability...`);
      // Re-check backend availability before attempting connection
      this.checkBackendAvailability().then((available) => {
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

    this.connectionState = 'disconnected';
    this.notifyConnectionState();
    this.subscribers.clear();
  }
}

export const webSocketService = new WebSocketService();
