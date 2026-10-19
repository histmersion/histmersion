// A stand-in for the OCR converter while the documentation screenshots are taken: it answers the
// server's health probe and refuses every conversion. The demo collection is loaded through the API,
// so nothing is ever converted; without it the About window would report the converter as down (and
// the server must never be pointed at a real installation's converter).
//
//   node docs/demo/converter-stub.mjs <port>
import { createServer } from 'node:http';

const port = Number(process.argv[2]);
if (!port) { console.error('usage: node docs/demo/converter-stub.mjs <port>'); process.exit(1); }
createServer((req, res) => {
  const ok = req.method === 'GET' && req.url === '/health';
  res.writeHead(ok ? 200 : 503, { 'content-type': 'application/json' });
  res.end(JSON.stringify(ok ? { status: 'ok' } : { error: 'the documentation demo converts nothing' }));
}).listen(port, '127.0.0.1');
