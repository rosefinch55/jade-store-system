#!/usr/bin/env node
/**
 * 小程序页面逻辑模拟器：用 node 跑真实的页面 js，wx.* 全部打桩，
 * 请求走真实后端，用来在没有微信开发者工具的情况下验证数据流与校验逻辑。
 * 用法：node tools/mp-sim.js [baseUrl]
 */
const path = require('path');
process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0'; // 自签证书，本地工具链专用
const http = require('http');
const https = require('https');

const BASE = process.argv[2] || 'https://120.26.19.193:8080';
const MP_ROOT = path.resolve(__dirname, '..', 'miniprogram');
// 每次跑用不同手机号，避免「同号当日重复预约」把用例打挂
const PHONE = '138' + String(Date.now()).slice(-8);

let currentPage = null;
const navLog = [];
const modalLog = [];

function setByPath(obj, keyPath, value) {
  // 支持 a.b 与 items[0].imgUrl
  const parts = keyPath.replace(/\[(\d+)\]/g, '.$1').split('.');
  let cur = obj;
  for (let i = 0; i < parts.length - 1; i++) {
    const k = parts[i];
    if (cur[k] === undefined || cur[k] === null) cur[k] = /^\d+$/.test(parts[i + 1]) ? [] : {};
    cur = cur[k];
  }
  cur[parts[parts.length - 1]] = value;
}

function makePage(cfg) {
  const page = Object.assign({}, cfg);
  page.data = JSON.parse(JSON.stringify(cfg.data || {}));
  page.setData = function (patch) {
    for (const k in patch) setByPath(page.data, k, patch[k]);
  };
  return page;
}

function request(opts) {
  const url = new URL(opts.url);
  const secure = url.protocol === 'https:';
  const mod = secure ? https : http;
  const method = (opts.method || 'GET').toUpperCase();
  const body = method === 'GET' ? null : JSON.stringify(opts.data || {});
  if (process.env.MP_SIM_DEBUG && method !== 'GET') console.error('[req]', method, url.pathname, body);
  const req = mod.request({
    hostname: url.hostname,
    port: url.port || (secure ? 443 : 80),
    path: url.pathname + url.search,
    method: method,
    rejectUnauthorized: false, // 服务器用自签证书，模拟器不做校验
    headers: Object.assign({ 'Content-Type': 'application/json' }, opts.header || {})
  }, (res) => {
    let buf = '';
    res.on('data', (c) => (buf += c));
    res.on('end', () => {
      let parsed;
      try { parsed = JSON.parse(buf); } catch (e) { parsed = { code: -1, message: '非 JSON 响应' }; }
      opts.success && opts.success({ statusCode: res.statusCode, data: parsed });
    });
  });
  req.on('error', (e) => opts.fail && opts.fail(e));
  if (body) req.write(body);
  req.end();
}

const _store = {};
global.wx = {
  request: request,
  getStorageSync: (k) => (k in _store ? _store[k] : ''),
  setStorageSync: (k, v) => { _store[k] = v; },
  removeStorageSync: (k) => { delete _store[k]; },
  showModal: (o) => { modalLog.push(o); o.success && o.success({ confirm: true }); },
  showToast: () => {},
  navigateTo: (o) => navLog.push('navigateTo ' + o.url),
  redirectTo: (o) => navLog.push('redirectTo ' + o.url),
  reLaunch: (o) => navLog.push('reLaunch ' + o.url),
  navigateBack: () => navLog.push('navigateBack'),
  stopPullDownRefresh: () => {},
  setNavigationBarTitle: () => {}
};

let appInstance = null;
global.App = (cfg) => { appInstance = cfg; };
global.getApp = () => appInstance;
global.Page = (cfg) => { currentPage = makePage(cfg); };

function loadPage(rel, options) {
  const file = path.join(MP_ROOT, rel);
  delete require.cache[require.resolve(file)];
  require(file);
  const page = currentPage;
  page.onLoad && page.onLoad(options || {});
  return page;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const results = [];
function check(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log((ok ? '  PASS  ' : '  FAIL  ') + name + (detail ? '  → ' + detail : ''));
}

(async () => {
  require(path.join(MP_ROOT, 'app.js'));
  check('app.js 注册', !!appInstance, 'BASE=' + (appInstance && appInstance.globalData.base));

  // ---------- 首页 ----------
  const idx = loadPage('pages/index/index.js', {});
  await sleep(1800);
  check('首页 精选位有数据', idx.data.featured.length > 0, idx.data.featured.length + ' 条');
  check('首页 全部位有数据', idx.data.latest.length > 0, idx.data.latest.length + ' 条');
  check('首页 总数为在柜', idx.data.total > 0, 'total=' + idx.data.total);
  const f0 = idx.data.latest[0] || {};
  check('首页 图片补全成绝对地址', !!f0.imgUrl && f0.imgUrl.indexOf('http') === 0, f0.imgUrl);
  check('首页 参考价已格式化', /^¥[\d,]+$/.test(f0.priceText || ''), f0.priceText + ' (raw=' + f0.priceUpper + ')');

  // ---------- 全部在售 ----------
  const gd = loadPage('pages/goods/goods.js', {});
  await sleep(1800);
  check('列表 首屏有数据', gd.data.items.length > 0, gd.data.items.length + ' 条');
  check('列表 分类已加载', gd.data.cats.length > 0, gd.data.cats.length + ' 类');
  check('列表 hasMore 判定', typeof gd.data.hasMore === 'boolean', 'hasMore=' + gd.data.hasMore);
  const catId = gd.data.cats.length ? gd.data.cats[0].id : 0;
  gd.pickCat({ currentTarget: { dataset: { id: catId } } });
  await sleep(1500);
  check('列表 按类别筛选', gd.data.catId === catId && gd.data.items.length > 0,
    '类别 ' + catId + ' → ' + gd.data.items.length + ' 条');

  // ---------- 详情 ----------
  const no = f0.goodsNo;
  const dt = loadPage('pages/detail/detail.js', { goodsNo: no });
  await sleep(1500);
  check('详情 取到货品', !!dt.data.g && dt.data.g.name === f0.name, dt.data.g && dt.data.g.name);
  check('详情 展示参考价', /^¥[\d,]+$/.test((dt.data.g || {}).priceText || ''), (dt.data.g || {}).priceText);
  check('详情 不含成本/底价字段',
    dt.data.g && dt.data.g.cost === undefined && dt.data.g.minPrice === undefined
    && dt.data.g.transferPrice === undefined,
    'cost=' + (dt.data.g || {}).cost + ' minPrice=' + (dt.data.g || {}).minPrice);

  // ---------- 预约 ----------
  const rv = loadPage('pages/reserve/reserve.js', { goodsNo: no });
  await sleep(300);
  const d = rv.data;
  check('预约 日期窗口起点为今天', /^\d{4}-\d{2}-\d{2}$/.test(d.date), d.date);
  check('预约 默认时段不早于窗口起点', d.time >= d.minText.slice(11), d.time + ' vs 起 ' + d.minText.slice(11));
  check('预约 时段按半小时对齐', d.slots.every(x => /:(00|30)$/.test(x)), '共 ' + d.slots.length + ' 档');
  check('预约 提示文案含上界', d.minText.length > 0 && d.maxText.length > 0, d.minText + ' ~ ' + d.maxText);

  const maxDate = d.maxDate;
  rv.onDate({ detail: { value: maxDate } });
  const lastSlot = rv.data.slots[rv.data.slots.length - 1];
  // 末日若整日落在窗口内，档位按营业结束 20:00 收口
  check('预约 末日档位收口在营业结束', rv.data.slots.length > 0 && lastSlot <= '20:00',
    'last=' + lastSlot + ' 共 ' + rv.data.slots.length + ' 档');

  rv.onDate({ detail: { value: d.minDate } });
  check('预约 回到今天从窗口起点开始', rv.data.slots[0] >= d.minText.slice(11),
    rv.data.slots[0] + ' vs 起 ' + d.minText.slice(11));

  rv.onName({ detail: { value: '模拟测试' } });
  rv.onPhone({ detail: { value: PHONE.slice(0, 10) } });
  rv.submit();
  check('预约 手机号不足 11 位被拦', rv.data.err.indexOf('11 位') >= 0, rv.data.err);

  rv.onPhone({ detail: { value: PHONE } });
  // 用「今天 00:00」构造必然早于窗口的时间，避免依赖当前钟点
  rv.onDate({ detail: { value: d.minDate } });
  rv.setData({ time: '00:00' });
  rv.submit();
  check('预约 早于窗口被拦', rv.data.err.indexOf('最早可约') >= 0, rv.data.err);

  // 合法提交（当天不在窗口内时改选明天，保证用例稳定）
  const tomorrow = new Date(Date.now() + 24 * 3600 * 1000);
  const tStr = tomorrow.getFullYear() + '-' + String(tomorrow.getMonth() + 1).padStart(2, '0')
    + '-' + String(tomorrow.getDate()).padStart(2, '0');
  rv.onDate({ detail: { value: tStr } });
  const slotIdx = rv.data.slots.indexOf('15:00');
  rv.onSlot({ detail: { value: slotIdx >= 0 ? slotIdx : 0 } });
  rv.submit();
  await sleep(2000);
  const okModal = modalLog.find((m) => m.title === '预约成功');
  check('预约 合法时间提交成功', !!okModal, okModal ? okModal.content.replace(/\n/g, ' | ') : rv.data.err);

  // 客户自助预定已下线（改由柜台登记），此处不再模拟 pages/book

  // ---------- 查预约 ----------
  const mine = loadPage('pages/mine/mine.js', {});
  mine.onTail({ detail: { value: PHONE.slice(-4) } });
  mine.query();
  await sleep(1500);
  check('查预约 返回列表', mine.data.queried && mine.data.results.length > 0,
    mine.data.results.length + ' 条');
  const r0 = mine.data.results[0] || {};
  check('查预约 字段完整', !!r0.resvNo && !!r0.expectSlot, r0.resvNo + ' / ' + r0.expectSlot);

  const failed = results.filter((r) => !r.ok);
  console.log('\n测试手机号 ' + PHONE);
  console.log('合计 ' + results.length + ' 项，失败 ' + failed.length + ' 项');
  process.exit(failed.length ? 1 : 0);
})().catch((e) => {
  console.error('模拟器异常：', e);
  process.exit(2);
});
