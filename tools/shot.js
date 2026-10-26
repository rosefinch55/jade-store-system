// 视觉核对截图：门户页 / 客户看板（手机 + 桌面）/ 管理端登录 / 管理端首页
// 用法：node tools/shot.js [baseUrl]
process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0'; // 自签证书，本地工具链专用
const puppeteer = require('puppeteer-core');
const fs = require('fs');

const BASE = process.argv[2] || 'http://127.0.0.1:8081';
const OUT = require('path').join(__dirname, '..', '_ui'); // 跟着仓库走
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await puppeteer.launch({
    ignoreHTTPSErrors: true,
    executablePath: EDGE, headless: 'new',
    args: ['--no-sandbox', '--disable-gpu'],
  });

  const shot = async (name, url, w, h, before) => {
    const page = await browser.newPage();
    await page.setViewport({ width: w, height: h, deviceScaleFactor: 1 });
    const errs = [];
    page.on('pageerror', e => errs.push(String(e).slice(0, 160)));
    page.on('console', m => { if (m.type() === 'error') errs.push(m.text().slice(0, 160)); });
    await page.goto(url, { waitUntil: 'networkidle2', timeout: 30000 });
    if (before) await before(page);
    await new Promise(r => setTimeout(r, 1600));
    await page.screenshot({ path: `${OUT}/${name}.png` });
    console.log(`${name}  ${errs.length ? 'ERR: ' + errs.slice(0, 2).join(' | ') : 'ok'}`);
    await page.close();
  };

  await shot('v-portal', BASE + '/', 1440, 900);
  await shot('v-client-m', BASE + '/mini.html', 390, 844);
  await shot('v-client-d', BASE + '/mini.html', 1440, 900);
  await shot('v-login', BASE + '/index.html', 1440, 900);

  const login = await fetch(BASE + '/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: '123456' }),
  }).then(r => r.json());
  await shot('v-admin', BASE + '/index.html', 1440, 900, async (page) => {
    await page.evaluate((t, u) => {
      localStorage.setItem('jade_token', t);
      localStorage.setItem('jade_user', u);
    }, login.data.token, JSON.stringify(login.data.user));
    await page.reload({ waitUntil: 'networkidle2' });
  });

  await browser.close();
})();
