import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { documentService } from '../services/api';
import PipelineVisualization from './PipelineVisualization';
import { DocumentCategory, DocumentStatus } from '../types/document';

jest.mock('../services/api', () => ({
  documentService: { processDocument: jest.fn() },
}));

jest.mock('d3-selection', () => ({
  select: () => {
    const selection = {};
    for (const method of ['selectAll', 'remove', 'attr', 'append', 'style', 'on', 'text']) {
      selection[method] = () => selection;
    }
    return selection;
  },
}));

const queued = (id) => ({
  id,
  fileName: `${id}.txt`,
  fileType: 'txt',
  mimeType: 'text/plain',
  fileSize: 100,
  uploadedAt: '2026-01-01T12:00:00',
  category: DocumentCategory.OTHER,
  status: DocumentStatus.UPLOADED,
});

let container;
let root;

beforeEach(() => {
  global.IS_REACT_ACT_ENVIRONMENT = true;
  jest.clearAllMocks();
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

test('processes the whole queue once and refreshes after completion', async () => {
  const documents = [queued('first'), queued('second')];
  const finished = jest.fn().mockResolvedValue(undefined);
  documentService.processDocument.mockImplementation(async (id) => ({
    ...queued(id), status: DocumentStatus.COMPLETED,
  }));
  act(() => root.render(
    <PipelineVisualization documents={documents} onDocumentSelect={jest.fn()} onQueueFinished={finished} />,
  ));

  const button = container.querySelector('button');
  await act(async () => {
    button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  });

  expect(documentService.processDocument.mock.calls).toEqual([['first'], ['second']]);
  expect(finished).toHaveBeenCalledTimes(1);
  expect(container.textContent).toContain('Completed 2 of 2 documents.');
});

test('reports one failure and continues with the rest of the queue', async () => {
  const documents = [queued('first'), queued('second')];
  documentService.processDocument
    .mockRejectedValueOnce(new Error('Could not reach the API'))
    .mockResolvedValueOnce({ ...documents[1], status: DocumentStatus.COMPLETED });
  const finished = jest.fn().mockResolvedValue(undefined);
  act(() => root.render(
    <PipelineVisualization documents={documents} onDocumentSelect={jest.fn()} onQueueFinished={finished} />,
  ));

  await act(async () => {
    container.querySelector('button').dispatchEvent(new MouseEvent('click', { bubbles: true }));
  });

  expect(documentService.processDocument).toHaveBeenCalledTimes(2);
  expect(finished).toHaveBeenCalledTimes(1);
  expect(container.textContent).toContain('Completed 1 of 2 documents.');
  expect(container.textContent).toContain('first.txt (Could not reach the API)');
});
