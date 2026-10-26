// 页面级断言：三个前台页面在手机与桌面视口下都要渲染出来，且没有 JS 异常。
// 覆盖 portal / mini / external——这三页一直没有 pageerror 兜底。
// 用法：node tools/page-check.js [baseUrl]
const puppeteer = require('puppeteer-core');

const BASE = process.argv[2] || 'https://203.0.113.10:8080';
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';

let pass = 0, fail = 0;
const check = (name, ok, detail) => {
  if (ok) { pass++; console.log('  PASS  ' + name + (detail ? '  → ' + detail : '')); }
  else { fail++; console.log('  FAIL  ' + name + '  → ' + (detail || '')); }
};
const wait = (ms) => new Promise((r) => setTimeout(r, ms));

const CASES = [
  { path: '/', name: '门户', expect: '三个入口' },
  { path: '/mini.html', name: '客户看板', expect: '在售' },
  { path: '/external.html', name: '外部协同', expect: '外部账号登录' }
];

const VIEWPORTS = [
  { name: '手机 390×844', width: 390, height: 844, mobile: true },
  { name: '桌面 1280×900', width: 1280, height: 900, mobile: false }
];

(async () => {
  const browser = await puppeteer.launch({
    executablePath: EDGE, headless: 'new',
    args: ['--no-sandbox'], ignoreHTTPSErrors: true
  });

  for (const vp of VIEWPORTS) {
    console.log('\n== ' + vp.name + ' ==');
    for (const c of CASES) {
      const page = await browser.newPage();
      await page.setViewport({ width: vp.width, height: vp.height, isMobile: vp.mobile });

      const errors = [];
      page.on('pageerror', (e) => errors.push('异常 ' + (e.message || '').slice(0, 80)));
      page.on('console', (m) => {
        if (m.type() !== 'error') return;
        const t = m.text();
        // 只关心脚本错误；图片 404 之类不影响结构，单独算资源问题
        if (/Failed to load resource/.test(t)) errors.push('资源 ' + t.slice(0, 60));
        else errors.push('控制台 ' + t.slice(0, 80));
      });

      const res = await page.goto(BASE + c.path, { waitUntil: 'networkidle2', timeout: 45000 });
      await wait(1200);

      // 走隧道时 serveo 会先插一屏警告，点掉再断言
      try {
        const hit = await page.evaluate(() => {
          const a = [...document.querySelectorAll('a,button')]
            .find((x) => /Continue to Site/i.test(x.textContent || ''));
          if (a) { a.click(); return true; }
          return false;
        });
        if (hit) await wait(2600);
      } catch (e) { /* 点击后跳转会让上下文失效，忽略 */ }
      await wait(1200);

      const text = await page.evaluate(() => document.body.innerText.replace(/\s+/g, ' '));
      // 304 说明命中协商缓存，同样算正常
      const ok = res && res.status() < 400 && text.includes(c.expect);
      check(c.name + ' 渲染', ok, ok ? '' : '状态 ' + (res && res.status()) + '，正文 ' + text.slice(0, 40));

      const hard = errors.filter((e) => !e.startsWith('资源'));
      check(c.name + ' 无脚本异常', hard.length === 0, hard.slice(0, 2).join(' / '));

      const soft = errors.filter((e) => e.startsWith('资源'));
      if (soft.length) console.log('        提示：资源加载告警 ' + soft.slice(0, 2).join(' / '));

      await page.close();
    }
  }

  console.log('\n合计 ' + (pass + fail) + ' 项，通过 ' + pass + '，失败 ' + fail);
  await browser.close();
  process.exit(fail ? 1 : 0);
})().catch((e) => { console.error('走查异常：', e.message); process.exit(1); });
