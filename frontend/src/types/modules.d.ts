// Type declarations for modules without TypeScript definitions

declare module 'd3' {
  export * from 'd3';
}

declare module 'sockjs-client' {
  class SockJS {
    constructor(url: string, protocols?: string | string[], options?: any);
    onopen: ((event: any) => void) | null;
    onmessage: ((event: any) => void) | null;
    onclose: ((event: any) => void) | null;
    onerror: ((event: any) => void) | null;
    send(data: string): void;
    close(): void;
  }
  export = SockJS;
}
