// The map-style study's prototype server (docs/research-map-styles.md). Not shipped.
//
// Serves this directory (the page, the styles), the pinned npm packages (MapLibre, the fonts) and passes
// /api/* and /web/* through to a Histmersion server — the base-map tiles come from there, same origin, so
// the page needs no CORS and makes no request to any other host.
//
//   HISTMERSION_SERVER_URL=http://127.0.0.1:18080 node serve.mjs [port]
import { createServer, request } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = fileURLToPath(new URL('.', import.meta.url));
const upstream = new URL(process.env.HISTMERSION_SERVER_URL || 'http://127.0.0.1:18080');
const port = Number(process.argv[2] || process.env.PORT || 18500);

const types = { '.html': 'text/html; charset=utf-8', '.mjs': 'text/javascript', '.js': 'text/javascript',
  '.json': 'application/json', '.css': 'text/css', '.woff2': 'font/woff2', '.png': 'image/png', '.map': 'application/json' };

// what may be read from disk: this directory's own files and the packages' dist folders
const allowed = p => !p.includes('..') && (p.startsWith('node_modules/') ? /^node_modules\/(maplibre-gl\/dist|@maplibre\/maplibre-gl-leaflet|maplibre-contour\/dist|leaflet\/dist|@fontsource\/noto-sans\/files)\//.test(p) : !p.startsWith('.'));

createServer(async (req, res) => {
  const url = new URL(req.url, 'http://x');
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/web/')) {
    const up = request({ host: upstream.hostname, port: upstream.port, path: req.url, method: req.method,
      headers: { ...req.headers, host: upstream.host } }, r => { res.writeHead(r.statusCode, r.headers); r.pipe(res); });
    up.on('error', e => { res.writeHead(502); res.end(String(e)); });
    req.pipe(up);
    return;
  }
  let p = normalize(decodeURIComponent(url.pathname)).replace(/^\/+/, '') || 'index.html';
  if (p.startsWith('fonts/')) p = 'node_modules/@fontsource/noto-sans/files/' + p.slice(6);
  if (!allowed(p)) { res.writeHead(404); res.end(); return; }
  try {
    const f = join(here, p);
    if (!(await stat(f)).isFile()) throw new Error('not a file');
    res.writeHead(200, { 'content-type': types[extname(f)] || 'application/octet-stream', 'cache-control': 'max-age=3600' });
    res.end(await readFile(f));
  } catch {
    res.writeHead(404); res.end();
  }
}).listen(port, '127.0.0.1', () => console.log(`map-style prototype on http://127.0.0.1:${port}/ → ${upstream.origin}`));
