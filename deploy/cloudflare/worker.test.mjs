import assert from 'node:assert/strict';
import { test } from 'node:test';
import worker from './_worker.js';

const env = {
  RECORDER_ORIGIN: 'https://record-demo.onrender.com',
  EVIDENCE_ORIGIN: 'https://evidence-demo.onrender.com',
  GATEWAY_ACCESS_TOKEN: 'test-token',
  ASSETS: { fetch: async () => new Response('static asset') },
};
const id = '00000000-0000-0000-0000-000000000001';
const request = (path, method = 'GET') =>
  new Request('https://bancada-pos.pages.dev' + path, { method });

test('serves assets and only forwards supported API requests', async (context) => {
  const fetchMock = context.mock.method(globalThis, 'fetch', async () =>
    new Response(null, { status: 204 }),
  );
  assert.equal(await (await worker.fetch(request('/'), env)).text(), 'static asset');
  assert.equal((await worker.fetch(request('/api/actuator/metrics'), env)).status, 404);
  assert.equal((await worker.fetch(request('/api/comparisons', 'POST'), env)).status, 204);
  assert.equal(fetchMock.mock.calls[0].arguments[0], env.RECORDER_ORIGIN + '/api/comparisons');
  assert.equal(fetchMock.mock.calls[0].arguments[1].headers['X-Bancada-Gateway'], 'test-token');
  await worker.fetch(request('/api/comparisons/' + id + '/report'), env);
  assert.equal(fetchMock.mock.calls[1].arguments[0], env.EVIDENCE_ORIGIN + '/api/comparisons/' + id + '/report');
});

test('waits for both services and treats a wake-up page as unavailable', async (context) => {
  const mocked = context.mock.method(globalThis, 'fetch', async () => Response.json({ status: 'UP' }));
  assert.equal((await worker.fetch(request('/api/readiness'), env)).status, 204);
  assert.equal(mocked.mock.callCount(), 2);
  mocked.mock.mockImplementation(async () => new Response('<html>', {
    headers: { 'Content-Type': 'text/html' },
  }));
  assert.equal((await worker.fetch(request('/api/readiness'), env)).status, 503);
  assert.equal((await worker.fetch(request('/api/comparisons', 'POST'), env)).status, 503);
});
