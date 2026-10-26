// 前端页面巡检：注入 token 后逐页截图，收集控制台报错。
// 用法：node tools/ui-check.js [baseUrl]
process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0'; // 自签证书，本地工具链专用
const puppeteer = require('puppeteer-core');
const fs = require('fs');

const BASE = process.argv[2] || 'https://203.0.113.10:8080';
const OUT = require('path').join(__dirname, '..', '_ui'); // 跟着仓库走，不再写死某个盘符的软链路径
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';

const MENUS = [
  ['dashboard', '概览'], ['calendar', '经营日历'], ['goods', '货品管理'], ['sales', '成交与退货'],
  ['installments', '分期管理'], ['stats', '销售与周转'], ['customers', '客户档案'],
  ['transfers', '同行调货'], ['approval', '审核与结算'], ['consign', '寄售合同'],
  ['process', '加工与上门'], ['users', '账号与角色'], ['base', '基础数据'], ['oplog', '操作日志'],
];

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const login = await fetch(BASE + '/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: '123456' }),
  }).then(r => r.json());
  if (login.code !== 0) { console.error('登录失败', login); process.exit(1); }

  const browser = await puppeteer.launch({
    ignoreHTTPSErrors: true,
    executablePath: EDGE, headless: 'new',
    args: ['--no-sandbox', '--disable-gpu', '--window-size=1600,1000'],
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1600, height: 1000 });

  const errors = [];
  await page.evaluateOnNewDocument(() => {
    const orig = console.error;
    console.error = (...a) => orig(...a.map(x => (x && x.stack) ? x.stack : x));
  });
  page.on('console', m => { if (m.type() === 'error') Promise.all(m.args().map(a => a.jsonValue().catch(() => ''))).then(vs => errors.push('[console] ' + JSON.stringify(vs).slice(0, 260) + ' @' + JSON.stringify(m.location() || {}))).catch(() => {}); });
  page.on('pageerror', e => errors.push('[pageerror] ' + String(e).slice(0, 200)));
  page.on('response', async r => {
    if (r.status() >= 400 && !r.url().includes('favicon')) {
      let body = '';
      try { body = (await r.text()).slice(0, 150); } catch (e) {}
      errors.push(`[${r.status()}] ${r.url().replace(BASE, '')} :: ${body}`);
    }
  });
  page.on('requestfailed', r => {
    const u = r.url();
    if (!u.includes('favicon')) errors.push('[requestfailed] ' + u.slice(-80));
  });

  // 根路径已是门户页，管理端在 /index.html
  await page.goto(BASE + '/index.html', { waitUntil: 'networkidle2', timeout: 30000 });
  await page.evaluate((t, u) => {
    localStorage.setItem('jade_token', t);
    localStorage.setItem('jade_user', u);
  }, login.data.token, JSON.stringify(login.data.user));
  await page.reload({ waitUntil: 'networkidle2' });
  await new Promise(r => setTimeout(r, 2500));

  const results = [];
  for (const [key, label] of MENUS) {
    const ok = await page.evaluate((k) => {
      const items = [...document.querySelectorAll('.el-menu-item')];
      const hit = items.find(e => (e.textContent || '').trim() === k);
      if (hit) { hit.click(); return true; }
      return false;
    }, label);
    await new Promise(r => setTimeout(r, 1800));
    const file = `${OUT}/${key}.png`;
    await page.screenshot({ path: file });
    // 页面是否渲染出内容
    const empty = await page.evaluate(() => {
      const p = document.querySelector('.page');
      return !p || p.innerText.trim().length < 10;
    });
    results.push({ key, label, clicked: ok, empty, file });
    console.log(`  ${empty ? 'EMPTY' : 'OK   '} ${label}`);
  }

  await browser.close();
  fs.writeFileSync(`${OUT}/report.json`, JSON.stringify({ results, errors }, null, 1));
  console.log('\n控制台报错 ' + errors.length + ' 条');
  [...new Set(errors)].slice(0, 15).forEach(e => console.log('  ' + e));
  const bad = results.filter(r => !r.clicked || r.empty);
  if (bad.length) console.log('\n异常页面: ' + bad.map(b => b.label).join('、'));
  process.exit(bad.length || errors.length ? 1 : 0);
})();
