import { AxiosError } from 'axios';
import api, { authService, documentService, getCsrfToken, onSessionExpired } from './api';

// CRA's Jest27 resolver selects Axios's ESM entry; use its shipped CJS build in this test.
jest.mock('axios', () => jest.requireActual('axios/dist/node/axios.cjs'));

let requests;
let respond;
const reply = (config, data, status = 200) => ({ config, data, status, statusText: 'OK', headers: {} });

beforeEach(() => {
  jest.spyOn(console, 'error').mockImplementation(() => {});
  requests = [];
  respond = config => reply(config, { authenticated: false, csrfToken: 'initial-csrf' });
  api.defaults.adapter = async config => {
    requests.push(config);
    return respond(config);
  };
});
afterEach(() => jest.restoreAllMocks());

test('uses credentialed requests and sends rotated CSRF tokens only on mutations', async () => {
  await authService.session();
  expect(requests[0].withCredentials).toBe(true);
  expect(requests[0].headers.get('X-CSRF-TOKEN')).toBeUndefined();
  respond = config => reply(config, { authenticated: true, username: 'admin', csrfToken: 'rotated-csrf' });
  await authService.login('admin', 'test-password');
  expect(requests[1].headers.get('X-CSRF-TOKEN')).toBe('initial-csrf');
  expect(JSON.parse(requests[1].data)).toEqual({ username: 'admin', password: 'test-password' });
  expect(getCsrfToken()).toBe('rotated-csrf');
  await documentService.processDocument('document-id');
  expect(requests[2].headers.get('X-CSRF-TOKEN')).toBe('rotated-csrf');
  await documentService.getAllDocuments();
  expect(requests[3].headers.get('X-CSRF-TOKEN')).toBeUndefined();
});

test('never replays a failed mutation, including a request with an unknown server outcome', async () => {
  await authService.session();
  respond = config => { throw new AxiosError('Network connection lost', 'ERR_NETWORK', config); };
  await expect(documentService.processDocument('document-id')).rejects.toThrow('Network connection lost');
  expect(requests.filter(request => request.url.includes('/process'))).toHaveLength(1);
});

test('invalidates the UI session after a protected request receives401, but not a bad login', async () => {
  const expired = jest.fn();
  const unsubscribe = onSessionExpired(expired);
  await authService.session();
  respond = config => { throw new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, reply(config, { message: 'Sign in required' }, 401)); };
  await expect(authService.login('admin', 'wrong')).rejects.toBeDefined();
  expect(expired).not.toHaveBeenCalled();
  await expect(documentService.getAllDocuments()).rejects.toBeDefined();
  expect(expired).toHaveBeenCalledTimes(1);
  expect(getCsrfToken()).toBe('');
  unsubscribe();
});

test('refreshes an expired CSRF token without replaying the rejected mutation', async () => {
  await authService.session();
  respond = config => {
    if (config.method === 'get') return reply(config, { authenticated: true, csrfToken: 'refreshed-token' });
    throw new AxiosError('Request denied', 'ERR_BAD_REQUEST', config, null, reply(config, {}, 403));
  };
  await expect(documentService.processDocument('document-id')).rejects.toThrow('Request denied');
  expect(requests.filter(request => request.method === 'post')).toHaveLength(1);
  expect(getCsrfToken()).toBe('refreshed-token');
});
