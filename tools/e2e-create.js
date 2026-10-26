// 只测建档：用真实鼠标事件选下拉项
const puppeteer = require('puppeteer-core');
const BASE = 'http://127.0.0.1:8080';
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
(async () => {
  const login = await fetch(BASE + '/api/auth/login', { method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: '123456' }) }).then(r => r.json());
  const b = await puppeteer.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: 'new', args: ['--no-sandbox'] });
  const p = await b.newPage();
  await p.setViewport({ width: 1600, height: 1000 });
  await p.goto(BASE, { waitUntil: 'networkidle2' });
  await p.evaluate((t, u) => { localStorage.setItem('jade_token', t); localStorage.setItem('jade_user', u); },
    login.data.token, JSON.stringify(login.data.user));
  await p.reload({ waitUntil: 'networkidle2' });
  await sleep(2000);

  await p.evaluate(() => [...document.querySelectorAll('.el-menu-item')].find(e => e.innerText.includes('货品管理'))?.click());
  await sleep(1600);
  const before = await p.evaluate(() => parseInt((document.querySelector('.toolbar span')?.innerText || '0').match(/\d+/)?.[0] || 0));

  await p.evaluate(() => [...document.querySelectorAll('.toolbar button')].find(e => e.innerText.includes('登记入库'))?.click());
  await sleep(1200);

  // 品名
  await p.click('.el-dialog .el-form-item input');
  await p.keyboard.type('E2E验收挂件' + Date.now().toString().slice(-5));
  await sleep(300);

  // 类别：点开下拉（第 2 个 el-select 是类别，第 1 个是器型）
  const catBox = await p.evaluateHandle(() => {
    const items = [...document.querySelectorAll('.el-dialog .el-form-item')];
    const cat = items.find(i => i.innerText.includes('类别'));
    return cat.querySelector('.el-select');
  });
  await catBox.click();
  await sleep(900);
  // 真实点击第一个选项
  const opt = await p.$('.el-select-dropdown__item');
  if (opt) { await opt.click(); await sleep(600); console.log('类别已选'); }
  else console.log('选项未出现');

  // 数字：克重、成本、最低售价
  const nums = await p.$$('.el-dialog .el-input-number input');
  const setNum = async (idx, v) => {
    if (!nums[idx]) return;
    await nums[idx].click({ clickCount: 3 });
    await p.keyboard.type(v);
    await p.keyboard.press('Tab');
    await sleep(200);
  };
  await setNum(0, '48.8');   // 克重
  await setNum(1, '7000');   // 成本
  await setNum(2, '10500');  // 最低售价
  await sleep(700);
  await p.screenshot({ path: 'E:/cc/系统分析与设计/玉器门店系统/_ui/e2e-create-filled.png' });

  await p.evaluate(() => [...document.querySelectorAll('.el-dialog__footer button')].find(e => e.innerText.includes('提交'))?.click());
  await sleep(2600);
  const msg = await p.evaluate(() => document.querySelector('.el-message')?.innerText.trim() || '');
  const after = await p.evaluate(() => parseInt((document.querySelector('.toolbar span')?.innerText || '0').match(/\d+/)?.[0] || 0));
  console.log('提交结果:', msg || '(无提示)');
  console.log('货品数', before, '->', after, after > before ? '✓ 新增成功' : '✗ 未新增');
  await p.screenshot({ path: 'E:/cc/系统分析与设计/玉器门店系统/_ui/e2e-create-result.png' });
  await b.close();
})();
