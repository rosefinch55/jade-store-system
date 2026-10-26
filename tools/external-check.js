// 外部协同页（H5）四步走查：登录 → 供应商提交 → 师傅任务 → 商户价目
// 用法：node tools/staff-check.js [baseUrl]
const puppeteer = require('puppeteer-core');

const BASE = process.argv[2] || 'https://203.0.113.10:8080';
const OUT = require('path').join(__dirname, '..', '_ui'); // 跟着仓库走
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';

let pass = 0, fail = 0;
const check = (n, c, d) => {
  if (c) { pass++; console.log('  PASS  ' + n + (d ? '  → ' + d : '')); }
  else { fail++; console.log('  FAIL  ' + n + '  → ' + (d || '')); }
};
const wait = (ms) => new Promise((r) => setTimeout(r, ms));

async function login(page, u, p) {
  await page.goto(BASE + '/external.html', { waitUntil: 'networkidle2' });
  await wait(600);
  const inputs = await page.$$('input.inp');
  await inputs[0].type(u);
  await inputs[1].type(p);
  await page.click('.btn.mt16');
  await wait(2400);
}

(async () => {
  const browser = await puppeteer.launch({
    executablePath: EDGE, headless: 'new', args: ['--no-sandbox'], ignoreHTTPSErrors: true
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 390, height: 844, isMobile: true, deviceScaleFactor: 2 });

  // ---------- 供应商 ----------
  console.log('== 供应商 ==');
  await login(page, 'yizhuoyimo', '123456');
  const homeTxt = await page.evaluate(() => document.body.innerText);
  check('登录进入工作台', /工作台/.test(homeTxt), homeTxt.split('\n')[2] || '');
  check('显示供应商卡片', /提交出让申请/.test(homeTxt) && /预约上门/.test(homeTxt));
  await page.screenshot({ path: OUT + '/v-staff-home.png' });

  await page.evaluate(() => {
    const c = [...document.querySelectorAll('.card')].find((x) => /提交出让申请/.test(x.innerText));
    c && c.click();
  });
  await wait(900);
  await page.evaluate(() => {
    const inputs = document.querySelectorAll('input.inp');
    const set = (el, v) => {
      const d = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
      d.set.call(el, v); el.dispatchEvent(new Event('input', { bubbles: true }));
    };
    set(inputs[0], '和田玉手把件（H5 用例）');
    set(inputs[1], '2');
    set(inputs[2], '3200');
  });
  await wait(300);
  await page.screenshot({ path: OUT + '/v-staff-supplier.png' });
  await page.evaluate(() => {
    const b = [...document.querySelectorAll('.btn')].find((x) => /提交出让申请/.test(x.innerText));
    b && b.click();
  });
  await wait(1800);
  const afterSubmit = await page.evaluate(() => document.body.innerText);
  check('提交后给出申请号', /已提交，申请号 CR\d+/.test(afterSubmit), (afterSubmit.match(/CR\d+/) || [''])[0]);

  await page.evaluate(() => {
    const t = [...document.querySelectorAll('.tab')].find((x) => /我的申请/.test(x.innerText));
    t && t.click();
  });
  await wait(2000);
  const listTxt = await page.evaluate(() => document.body.innerText);
  check('我的申请列出刚提交的单', /H5 用例/.test(listTxt));
  await page.screenshot({ path: OUT + '/v-staff-applies.png' });

  // 上门预约表单：时段控件必须是半小时步进且限定在营业时段内
  await page.goto(BASE + '/external.html', { waitUntil: 'networkidle2' });
  await wait(1800);
  await page.evaluate(() => {
    const c = [...document.querySelectorAll('.card')].find((x) => /预约上门/.test(x.innerText));
    c && c.click();
  });
  await wait(1200);
  const slotCtl = await page.evaluate(() => {
    const t = document.querySelector('input[type=time]');
    const d = document.querySelector('input[type=date]');
    return t && d ? { step: t.getAttribute('step'), min: t.getAttribute('min'), max: t.getAttribute('max') } : null;
  });
  check('上门时段控件受约束', !!slotCtl && slotCtl.step === '1800'
    && slotCtl.min === '10:00' && slotCtl.max === '19:00', JSON.stringify(slotCtl));

  // ---------- 师傅 ----------
  console.log('\n== 加工师傅 ==');
  await page.evaluate(() => localStorage.clear());
  await login(page, 'lishifuyudiao', '123456');
  check('师傅身份正确', /加工师傅/.test(await page.evaluate(() => document.body.innerText)));
  await page.evaluate(() => {
    const c = [...document.querySelectorAll('.card')].find((x) => /我的加工任务/.test(x.innerText));
    c && c.click();
  });
  await wait(1800);
  const taskTxt = await page.evaluate(() => document.body.innerText);
  check('任务列表有数据', /JG\d+/.test(taskTxt), (taskTxt.match(/JG\d+/) || [''])[0]);
  await page.screenshot({ path: OUT + '/v-staff-tasks.png' });

  const opened = await page.evaluate(() => {
    const b = [...document.querySelectorAll('.btn.sm')].find((x) => /提交完工/.test(x.innerText));
    if (!b) return false;
    b.click(); return true;
  });
  await wait(700);
  if (opened) {
    const dlgTxt = await page.evaluate(() => document.body.innerText);
    check('完工面板三阶段齐全', /加工前/.test(dlgTxt) && /加工中/.test(dlgTxt) && /完工/.test(dlgTxt));
    await page.screenshot({ path: OUT + '/v-staff-delivery.png' });
  } else {
    check('完工面板三阶段齐全', false, '没有「在加工」的任务');
  }

  // ---------- 商户 ----------
  console.log('\n== 合作商户 ==');
  await page.evaluate(() => localStorage.clear());
  await login(page, 'shinian', '123456');
  await page.evaluate(() => {
    const c = [...document.querySelectorAll('.card')].find((x) => /调货价目/.test(x.innerText));
    c && c.click();
  });
  await wait(1800);
  const priceTxt = await page.evaluate(() => document.body.innerText);
  check('价目列表有数据', /调货 \d+ 元/.test(priceTxt), (priceTxt.match(/调货 \d+ 元/) || [''])[0]);
  await page.screenshot({ path: OUT + '/v-staff-prices.png' });

  // ---------- 未登录访问 ----------
  console.log('\n== 未登录 ==');
  await page.evaluate(() => localStorage.clear());
  await page.goto(BASE + '/external.html', { waitUntil: 'networkidle2' });
  await wait(500);
  check('未登录回到登录页', /账号/.test(await page.evaluate(() => document.body.innerText)));
  await page.screenshot({ path: OUT + '/v-staff-login.png' });

  console.log('\n合计 ' + (pass + fail) + ' 项，通过 ' + pass + '，失败 ' + fail);
  await browser.close();
  process.exit(fail ? 1 : 0);
})().catch((e) => { console.error('走查异常：', e.message); process.exit(1); });
