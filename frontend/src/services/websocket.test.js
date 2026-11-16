import SockJS from 'sockjs-client';
import { webSocketService } from './websocket';
import { authService } from './api';

jest.mock('sockjs-client', () => jest.fn());
jest.mock('./api', () => ({ authService: { session: jest.fn() }, getCsrfToken: () => 'csrf-token' }));
jest.mock('stompjs', () => ({ over: jest.fn() }));

beforeEach(() => { webSocketService.disconnect(); jest.clearAllMocks(); });
afterEach(() => webSocketService.disconnect());

test('does not open a socket for an anonymous session', async () => {
  authService.session.mockResolvedValue({ authenticated: false });
  webSocketService.connect();
  await new Promise(resolve => setTimeout(resolve, 0));
  expect(SockJS).not.toHaveBeenCalled();
});

test('a session check resolving after sign-out cannot reopen the socket', async () => {
  let complete;
  authService.session.mockReturnValue(new Promise(resolve => { complete = resolve; }));
  webSocketService.connect();
  webSocketService.disconnect();
  complete({ authenticated: true });
  await new Promise(resolve => setTimeout(resolve, 0));
  expect(SockJS).not.toHaveBeenCalled();
});
