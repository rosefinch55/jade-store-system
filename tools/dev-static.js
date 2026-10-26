// 本地静态预览：静态文件取本地 static 目录，/api 代理到线上服务器，方便改样式时立刻看效果。
// 用法：node tools/dev-static.js [端口]
process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0'; // 自签证书，本地工具链专用
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = Number(process.argv[2] || 8081);
const UPSTREAM = process.env.JADE_UPSTREAM || 'https://203.0.113.10:8080';
const ROOT = path.resolve(__dirname, '../server/src/main/resources/static');

const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.json': 'application/json; charset=utf-8',
  '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.gif': 'image/gif',
  '.svg': 'image/svg+xml', '.woff2': 'font/woff2', '.ico': 'image/x-icon',
};

http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://x');
  if (url.pathname.startsWith('/api')) {
    try {
      const chunks = [];
      for await (const c of req) chunks.push(c);
      const body = chunks.length ? Buffer.concat(chunks) : undefined;
      const up = await fetch(UPSTREAM + req.url, {
        method: req.method,
        headers: Object.assign({}, req.headers, { host: '203.0.113.10:8080' }),
        body: req.method === 'GET' || req.method === 'HEAD' ? undefined : body,
      });
      const buf = Buffer.from(await up.arrayBuffer());
      res.writeHead(up.status, { 'Content-Type': up.headers.get('content-type') || 'application/json' });
      res.end(buf);
    } catch (e) {
      res.writeHead(502, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ code: -1, message: 'proxy error: ' + e.message }));
    }
    return;
  }
  let p = path.join(ROOT, decodeURIComponent(url.pathname));
  if (url.pathname === '/') p = path.join(ROOT, 'portal.html');
  if (!p.startsWith(ROOT)) { res.writeHead(403); res.end('forbidden'); return; }
  fs.readFile(p, (err, buf) => {
    if (err) {
      fs.readFile(path.join(ROOT, 'index.html'), (e2, b2) => {
        if (e2) { res.writeHead(404); res.end('not found'); return; }
        res.writeHead(200, { 'Content-Type': MIME['.html'] });
        res.end(b2);
      });
      return;
    }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(p)] || 'application/octet-stream' });
    res.end(buf);
  });
}).listen(PORT, () => console.log('dev static on http://127.0.0.1:' + PORT));
