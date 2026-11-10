const comparisons = '/api/comparisons';
const evidenceRoute = /^\/api\/comparisons\/[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}(\/report)?$/i;
const noStore = { 'Cache-Control': 'no-store' };

function unavailable() {
  return new Response(null, { status: 503, headers: noStore });
}

function renderOrigin(value) {
  const origin = new URL(value);
  if (origin.protocol !== 'https:' || !origin.hostname.endsWith('.onrender.com')
      || origin.port || origin.username || origin.password) {
    throw new Error('Expected a Render HTTPS origin.');
  }
  return origin.origin;
}

async function isReady(origin, token) {
  try {
    const response = await fetch(origin + '/actuator/health', {
      headers: { 'X-Bancada-Gateway': token },
      signal: AbortSignal.timeout(8_000),
      redirect: 'error',
    });
    return response.ok && (await response.json()).status === 'UP';
  } catch {
    return false;
  }
}

export default {
  async fetch(request, env) {
    const { pathname } = new URL(request.url);
    if (!pathname.startsWith('/api/')) return env.ASSETS.fetch(request);

    const evidence = evidenceRoute.exec(pathname);
    const isReadiness = pathname === '/api/readiness' && request.method === 'GET';
    const isRecord = pathname === comparisons && request.method === 'POST';
    const isEvidence = evidence && (request.method === 'GET' || (evidence[1] && request.method === 'POST'));
    if (!isReadiness && !isRecord && !isEvidence) {
      return new Response(null, { status: 404, headers: noStore });
    }

    const token = env.GATEWAY_ACCESS_TOKEN;
    if (!token) return unavailable();

    try {
      const recorder = renderOrigin(env.RECORDER_ORIGIN);
      const projection = renderOrigin(env.EVIDENCE_ORIGIN);
      if (isReadiness) {
        const ready = await Promise.all([isReady(recorder, token), isReady(projection, token)]);
        return ready.every(Boolean)
          ? new Response(null, { status: 204, headers: noStore })
          : unavailable();
      }

      // The supported write operations have no request body.
      const upstream = await fetch((isRecord ? recorder : projection) + pathname, {
        method: request.method,
        headers: { 'X-Bancada-Gateway': token, Accept: 'application/json' },
        redirect: 'error',
      });
      const contentType = upstream.headers.get('Content-Type');
      if (contentType?.includes('text/html')) return unavailable();

      const headers = new Headers(noStore);
      if (contentType) headers.set('Content-Type', contentType);
      const attachment = upstream.headers.get('Content-Disposition');
      if (attachment) headers.set('Content-Disposition', attachment);
      return new Response(upstream.body, { status: upstream.status, headers });
    } catch {
      return unavailable();
    }
  },
};
