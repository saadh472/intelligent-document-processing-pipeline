import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { Simulate } from 'react-dom/test-utils';
import AuthWorkspace, { useWorkspaceAuth } from './AuthWorkspace';
import { authService, onSessionExpired } from '../services/api';
import { webSocketService } from '../services/websocket';

jest.mock('../services/api', () => ({
  authService: { session: jest.fn(), login: jest.fn(), logout: jest.fn() },
  onSessionExpired: jest.fn(),
}));
jest.mock('../services/websocket', () => ({ webSocketService: { disconnect: jest.fn() } }));

const anonymous = { authenticated: false, csrfToken: 'anonymous-token' };
const authenticated = { authenticated: true, username: 'admin', csrfToken: 'authenticated-token' };
let container;
let root;
let expire;

const Child = () => {
  const { username, signOut, signingOut, signOutError } = useWorkspaceAuth();
  return <div>Private documents for {username}<button disabled={signingOut} onClick={signOut}>Sign out</button>{signOutError}</div>;
};
const render = async () => { await act(async () => root.render(<AuthWorkspace><Child /></AuthWorkspace>)); };
const input = async (name, value) => {
  const field = container.querySelector(`input[name="${name}"]`);
  await act(async () => Simulate.change(field, { target: { value } }));
};
const submit = async () => { await act(async () => container.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))); };

beforeEach(() => {
  global.IS_REACT_ACT_ENVIRONMENT = true;
  jest.resetAllMocks();
  authService.session.mockResolvedValue(anonymous);
  onSessionExpired.mockImplementation(callback => { expire = callback; return jest.fn(); });
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
});
afterEach(() => { act(() => root.unmount()); container.remove(); });

test('does not mount document content while session verification is pending', async () => {
  authService.session.mockReturnValue(new Promise(() => {}));
  await render();
  expect(container.textContent).toContain('Checking your session');
  expect(container.textContent).not.toContain('Private documents');
  expect(container.querySelector('form')).toBeNull();
});

test('shows a recoverable connection error rather than an unusable login form', async () => {
  authService.session.mockRejectedValueOnce(new Error('offline'));
  await render();
  expect(container.textContent).toContain('could not reach the workspace');
  expect(container.querySelector('form')).toBeNull();
  await act(async () => container.querySelector('button').click());
  expect(container.querySelector('form')).not.toBeNull();
  expect(authService.session).toHaveBeenCalledTimes(2);
});

test('invalid credentials keep protected content hidden and show an actionable error', async () => {
  authService.login.mockRejectedValue({ response: { status: 401 } });
  await render();
  await input('username', 'admin');
  await input('password', 'wrong-password');
  await submit();
  expect(authService.login).toHaveBeenCalledWith('admin', 'wrong-password');
  expect(container.textContent).toContain('username or password is incorrect');
  expect(container.textContent).not.toContain('Private documents');
});

test('successful sign-in mounts the workspace and prevents duplicate concurrent submissions', async () => {
  let complete;
  authService.login.mockReturnValue(new Promise(resolve => { complete = resolve; }));
  await render();
  await input('username', ' admin ');
  await input('password', 'test-password');
  await submit();
  await submit();
  expect(authService.login).toHaveBeenCalledTimes(1);
  expect(container.querySelector('input[name="password"]').disabled).toBe(true);
  await act(async () => complete(authenticated));
  expect(container.textContent).toContain('Private documents for admin');
  expect(container.querySelector('input[name="password"]')).toBeNull();
});

test('session expiry unmounts protected content and disconnects live updates', async () => {
  authService.session.mockResolvedValue(authenticated);
  await render();
  await act(async () => expire());
  expect(container.textContent).not.toContain('Private documents');
  expect(container.textContent).toContain('session has expired');
  expect(webSocketService.disconnect).toHaveBeenCalled();
});

test('sign-out failure leaves the session visible and permits a deliberate retry', async () => {
  authService.session.mockResolvedValue(authenticated);
  authService.logout.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(anonymous);
  await render();
  await act(async () => container.querySelector('button').click());
  expect(container.textContent).toContain('Private documents');
  expect(container.textContent).toContain('Could not sign out');
  await act(async () => container.querySelector('button').click());
  expect(container.textContent).not.toContain('Private documents');
  expect(container.textContent).toContain('signed out securely');
});

test('revalidates the session when returning to the tab', async () => {
  authService.session.mockResolvedValueOnce(authenticated).mockResolvedValueOnce(anonymous);
  await render();
  await act(async () => window.dispatchEvent(new Event('focus')));
  expect(container.textContent).not.toContain('Private documents');
  expect(container.textContent).toContain('session has ended');
});

test('an older session check cannot restore the workspace after sign-out', async () => {
  let finishRefresh;
  authService.session.mockResolvedValueOnce(authenticated).mockReturnValueOnce(new Promise(resolve => { finishRefresh = resolve; }));
  authService.logout.mockResolvedValue(anonymous);
  await render();
  await act(async () => window.dispatchEvent(new Event('focus')));
  await act(async () => container.querySelector('button').click());
  await act(async () => finishRefresh(authenticated));
  expect(container.textContent).not.toContain('Private documents');
  expect(container.textContent).toContain('signed out securely');
});
