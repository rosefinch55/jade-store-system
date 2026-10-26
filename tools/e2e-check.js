// 端到端：在界面上真实建档 + 登记成交，验证表单闭环
const puppeteer = require('puppeteer-core');
const BASE = 'http://127.0.0.1:8080';
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const sleep = (ms) => new Promise(r => setTimeout(r, ms));

async function clickText(page, sel, text) {
  return page.evaluate((s, t) => {
    const els = [...document.querySelectorAll(s)];
    const hit = els.find(e => (e.innerText || '').trim().includes(t));
    if (hit) { hit.click(); return true; }
    return false;
  }, sel, text);
}

(async () => {
  const login = await fetch(BASE + '/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: '123456' }) }).then(r => r.json());
  const b = await puppeteer.launch({ executablePath: EDGE, headless: 'new', args: ['--no-sandbox'] });
  const p = await b.newPage();
  await p.setViewport({ width: 1600, height: 1000 });
  const errs = [];
  p.on('pageerror', e => errs.push(String(e).slice(0, 150)));

  await p.goto(BASE, { waitUntil: 'networkidle2' });
  await p.evaluate((t, u) => { localStorage.setItem('jade_token', t); localStorage.setItem('jade_user', u); },
    login.data.token, JSON.stringify(login.data.user));
  await p.reload({ waitUntil: 'networkidle2' });
  await sleep(2000);

  // ---- 1. 货品管理 → 登记入库 ----
  await clickText(p, '.el-menu-item', '货品管理');
  await sleep(1500);
  const before = await p.evaluate(() => {
    const t = document.querySelector('.toolbar span')?.innerText || '';
    return parseInt((t.match(/\d+/) || [0])[0]);
  });
  await clickText(p, '.toolbar button', '登记入库');
  await sleep(1000);

  // 填表：品名、类别、克重、来源、成本、最低售价
  const filled = await p.evaluate(() => {
    const dlg = document.querySelector('.el-dialog');
    if (!dlg) return { ok: false, why: 'dialog 未打开' };
    const setVal = (el, v) => {
      const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
      setter.call(el, v);
      el.dispatchEvent(new Event('input', { bubbles: true }));
    };
    // 第一个输入框是品名
    const nameInput = dlg.querySelector('.el-form-item input');
    if (!nameInput) return { ok: false, why: '找不到品名输入框' };
    setVal(nameInput, 'E2E验收手镯' + Date.now().toString().slice(-6));
    return { ok: true, name: nameInput.value };
  });
  console.log('填品名:', JSON.stringify(filled));

  // 类别下拉：点开选第一个
  await p.evaluate(() => {
    const items = [...document.querySelectorAll('.el-dialog .el-form-item')];
    const cat = items.find(i => i.innerText.includes('类别'));
    cat?.querySelector('.el-select__wrapper')?.click();
  });
  await sleep(700);
  await p.evaluate(() => {
    const opts = [...document.querySelectorAll('.el-select-dropdown__item')];
    if (opts[0]) opts[0].click();
  });
  await sleep(600);

  // 数字输入：克重 / 成本 / 最低售价
  await p.evaluate(() => {
    const dlg = document.querySelector('.el-dialog');
    const nums = [...dlg.querySelectorAll('.el-input-number input')];
    const setVal = (el, v) => {
      const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
      setter.call(el, v);
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
      el.dispatchEvent(new Event('blur', { bubbles: true }));
    };
    // 顺序：克重、成本、最低售价、调货价
    if (nums[0]) setVal(nums[0], '45.5');
    if (nums[1]) setVal(nums[1], '6000');
    if (nums[2]) setVal(nums[2], '9000');
  });
  await sleep(800);
  await p.screenshot({ path: 'E:/cc/系统分析与设计/玉器门店系统/_ui/e2e-create-form.png' });

  // 提交
  await clickText(p, '.el-dialog__footer button', '提交');
  await sleep(2500);
  const msg = await p.evaluate(() => {
    const m = document.querySelector('.el-message');
    return m ? m.innerText.trim() : '';
  });
  const after = await p.evaluate(() => {
    const t = document.querySelector('.toolbar span')?.innerText || '';
    return parseInt((t.match(/\d+/) || [0])[0]);
  });
  console.log('提交提示:', msg, '| 提交前', before, '提交后', after, after > before ? '✓ 新增成功' : '✗ 未新增');
  await p.screenshot({ path: 'E:/cc/系统分析与设计/玉器门店系统/_ui/e2e-after-create.png' });

  // ---- 2. 成交与退货 → 登记成交 ----
  await clickText(p, '.el-menu-item', '成交与退货');
  await sleep(1500);
  await clickText(p, '.toolbar button', '登记成交');
  await sleep(1000);
  const saleInfo = await p.evaluate(async () => {
    const dlg = document.querySelector('.el-dialog');
    if (!dlg) return { ok: false };
    // 直接查一个在柜货品编号
    const r = await fetch('/api/goods?status=' + encodeURIComponent('在柜') + '&size=1',
      { headers: { Authorization: 'Bearer ' + localStorage.getItem('jade_token') } }).then(x => x.json());
    const no = r.data.items[0].goodsNo;
    const inp = dlg.querySelector('input');
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
    setter.call(inp, no);
    inp.dispatchEvent(new Event('input', { bubbles: true }));
    return { ok: true, goodsNo: no, minPrice: r.data.items[0].minPrice, name: r.data.items[0].name };
  });
  console.log('成交待填:', JSON.stringify(saleInfo));
  await p.evaluate(() => {
    const inp = document.querySelector('.el-dialog input');
    inp.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', bubbles: true }));
  });
  await sleep(2200);
  await p.screenshot({ path: 'E:/cc/系统分析与设计/玉器门店系统/_ui/e2e-sale-form.png' });
  const found = await p.evaluate(() => {
    const a = document.querySelector('.el-dialog .el-alert__title');
    return a ? a.innerText : '';
  });
  console.log('带出的货品信息:', found);
  if (found) {
    await clickText(p, '.el-dialog__footer button', '提交');
    await sleep(2500);
    const m2 = await p.evaluate(() => {
      const m = document.querySelector('.el-message');
      return m ? m.innerText.trim() : '';
    });
    console.log('成交提交:', m2);
    await p.screenshot({ path: 'E:/cc/系统分析与设计/玉器门店系统/_ui/e2e-after-sale.png' });
  }

  await b.close();
  console.log('页面错误', errs.length, errs.slice(0, 3));
})();
