// 全页面巡检截图：管理端 15 页、客户侧 4 面、外部端 4 角色
const p = require('puppeteer-core');
const https = require('https');
const fs = require('fs');
const path = require('path');

const BASE = process.argv[2] || 'https://120.26.19.193:8080';
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const OUT = process.argv[3] || './_audit-out';
const sleep = (ms) => new Promise(r => setTimeout(r, ms));

function apiLogin(u, pw) {
  return new Promise((res, rej) => {
    const data = JSON.stringify({ username: u, password: pw });
    const r = https.request({ host: '120.26.19.193', port: 8080, path: '/api/auth/login', method: 'POST', rejectUnauthorized: false, headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) } },
      (s) => { let b = ''; s.on('data', c => b += c); s.on('end', () => { try { res(JSON.parse(b).data); } catch (e) { rej(e); } }); });
    r.on('error', rej); r.write(data); r.end();
  });
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const b = await p.launch({ executablePath: EDGE, headless: 'new', args: ['--no-sandbox'], ignoreHTTPSErrors: true });
  const problems = [];
  const shots = [];

  const newPage = async (w, h, mobile) => {
    const pg = await b.newPage();
    await pg.setViewport({ width: w, height: h, isMobile: !!mobile });
    pg.on('pageerror', e => problems.push({ where: pg._name || '?', err: e.message.slice(0, 100) }));
    pg.on('console', m => { if (m.type() === 'error') problems.push({ where: pg._name || '?', err: 'console: ' + m.text().slice(0, 100) }); });
    return pg;
  };

  // ---------- 管理端 ----------
  const admin = await apiLogin('admin', '123456');
  const pg = await newPage(1440, 900, false);
  await pg.goto(BASE + '/index.html', { waitUntil: 'domcontentloaded' });
  await pg.evaluate(d => { localStorage.setItem('jade_token', d.token); localStorage.setItem('jade_user', JSON.stringify(d.user)); }, admin);
  await pg.goto(BASE + '/index.html', { waitUntil: 'networkidle2' });
  await sleep(1800);

  const labels = ['经营看板', '经营日历', '货品档案', '成交与退货', '分期管理', '销售与周转', '客户档案', '预定与留货', '同行调货', '审核与结算', '寄售合同', '加工与上门', '账号与角色', '基础数据', '操作日志'];
  for (const name of labels) {
    pg._name = 'admin/' + name;
    const ok = await pg.evaluate((t) => {
      const m = [...document.querySelectorAll('.el-menu-item')].find(x => x.innerText.trim() === t);
      if (m) { m.click(); return true; }
      return false;
    }, name);
    await sleep(1500);
    const txt = await pg.evaluate(() => document.querySelector('.main, .el-main') ? document.querySelector('.main, .el-main').innerText.length : 0);
    const file = path.join(OUT, 'admin-' + name + '.png');
    await pg.screenshot({ path: file, fullPage: false });
    shots.push({ page: 'admin/' + name, clicked: ok, textLen: txt, file });
  }
  await pg.close();

  // ---------- 客户侧 ----------
  const m = await newPage(430, 900, true);
  m._name = 'portal';
  await m.goto(BASE + '/portal.html', { waitUntil: 'networkidle2' });
  await sleep(800);
  await m.screenshot({ path: path.join(OUT, 'client-portal.png') });
  shots.push({ page: 'client/portal', clicked: true, textLen: 1, file: 'client-portal.png' });

  m._name = 'mini-list';
  await m.goto(BASE + '/mini.html', { waitUntil: 'networkidle2' });
  await sleep(1800);
  await m.screenshot({ path: path.join(OUT, 'client-list.png') });
  shots.push({ page: 'client/列表', clicked: true, textLen: 1, file: 'client-list.png' });

  m._name = 'mini-detail';
  await m.evaluate(() => { const c = document.querySelector('.card'); if (c) c.dispatchEvent(new MouseEvent('click', { bubbles: true })); });
  await sleep(1800);
  await m.screenshot({ path: path.join(OUT, 'client-detail.png') });
  shots.push({ page: 'client/详情', clicked: true, textLen: 1, file: 'client-detail.png' });

  m._name = 'mini-query';
  await m.goto(BASE + '/mini.html?tab=query', { waitUntil: 'networkidle2' });
  await sleep(1500);
  await m.screenshot({ path: path.join(OUT, 'client-query.png') });
  shots.push({ page: 'client/查预约', clicked: true, textLen: 1, file: 'client-query.png' });
  await m.close();

  // ---------- 外部端 ----------
  const externals = [
    { u: 'yizhuoyimo', label: '供应商-申请', nav: '提交出让申请' },
    { u: 'lishifuyudiao', label: '师傅-任务', nav: '加工任务' },
    { u: 'shinian', label: '商户-价目', nav: '调货价目' },
  ];
  for (const e of externals) {
    const u = await apiLogin(e.u, '123456');
    const x = await newPage(430, 900, true);
    x._name = 'ext/' + e.label;
    await x.goto(BASE + '/external.html', { waitUntil: 'domcontentloaded' });
    await x.evaluate(d => { localStorage.setItem('jade_token', d.token); localStorage.setItem('jade_user', JSON.stringify(d.user)); }, u);
    await x.goto(BASE + '/external.html', { waitUntil: 'networkidle2' });
    await sleep(1800);
    await x.evaluate((t) => {
      const c = [...document.querySelectorAll('*')].find(el => el.children.length === 0 && el.innerText.trim() === t);
      if (c) c.click();
    }, e.nav);
    await sleep(1800);
    await x.screenshot({ path: path.join(OUT, 'ext-' + e.label + '.png') });
    shots.push({ page: 'ext/' + e.label, clicked: true, textLen: 1, file: 'ext-' + e.label + '.png' });
    await x.close();
  }

  await b.close();
  fs.writeFileSync(path.join(OUT, 'result.json'), JSON.stringify({ shots, problems }, null, 1));
  console.log('shots', shots.length, 'problems', problems.length);
  problems.slice(0, 10).forEach(p => console.log(' -', p.where, p.err));
})();
