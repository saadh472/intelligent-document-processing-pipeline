import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';

jest.mock('./components/Dashboard', () => () => <main>Document workspace</main>);
jest.mock('./components/AuthWorkspace', () => ({ children }) => <section>Session gate{children}</section>);

let container;
let root;
beforeEach(() => {
  global.IS_REACT_ACT_ENVIRONMENT = true;
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
});
afterEach(() => { act(() => root.unmount()); container.remove(); window.history.replaceState(null, '', '/'); });

test.each(['/index.html', '/missing-page'])('redirects %s to the authenticated workspace instead of a blank screen', async pathname => {
  window.history.replaceState(null, '', pathname);
  await act(async () => root.render(<App />));
  expect(window.location.pathname).toBe('/');
  expect(container.textContent).toContain('Session gate');
  expect(container.textContent).toContain('Document workspace');
});
