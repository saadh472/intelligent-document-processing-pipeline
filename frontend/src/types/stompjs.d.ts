// Type definitions for stompjs
declare module 'stompjs' {
  export interface Client {
    connect(headers: any, connectCallback: () => void, errorCallback?: (error: any) => void): void;
    subscribe(destination: string, callback: (message: Message) => void): any;
    disconnect(disconnectCallback?: () => void): void;
    connected: boolean;
    over(socket: any): Client;
  }

  export interface Message {
    body: string;
  }

  export function over(socket: any): Client;
}
