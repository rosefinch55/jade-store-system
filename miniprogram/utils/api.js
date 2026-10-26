const BASE = 'https://120.26.19.193:8080'; // 自签证书，直连仅在调试模式下能通

/**
 * 统一请求封装。后端返回 {code, message, data}，code=0 表示成功。
 * 网络失败也返回同结构，页面按 code 判断即可，不必各自 catch。
 *
 * 线上版走云函数 proxy 中转：小程序本身不发起公网请求，因此不受「合法域名」与
 * 「HTTPS 证书」两道限制。云开发没开通或调用失败时回落到直连，方便本地调试。
 */
let cloudState = 0; // 0 未探测，1 可用，2 不可用

const TOKEN_KEY = 'jade_token';
const USER_KEY = 'jade_user';

function token() {
  return wx.getStorageSync(TOKEN_KEY) || '';
}

function setSession(tk, user) {
  wx.setStorageSync(TOKEN_KEY, tk || '');
  wx.setStorageSync(USER_KEY, user || null);
}

function user() {
  return wx.getStorageSync(USER_KEY) || null;
}

function clearSession() {
  wx.removeStorageSync(TOKEN_KEY);
  wx.removeStorageSync(USER_KEY);
}

/** 当前账号的主角色，决定工作台露出哪些入口 */
function roleCode() {
  const u = user();
  const roles = (u && u.roles) || [];
  const order = ['SUPPLIER', 'CRAFTSMAN', 'MERCHANT', 'BOSS', 'SALE'];
  for (let i = 0; i < order.length; i++) {
    for (let j = 0; j < roles.length; j++) {
      if (roles[j] && roles[j].code === order[i]) return order[i];
    }
  }
  return '';
}

function probeCloud() {
  if (cloudState) return cloudState === 1;
  if (!wx.cloud || !wx.cloud.init) {
    cloudState = 2;
    return false;
  }
  try {
    wx.cloud.init({ env: wx.cloud.DYNAMIC_CURRENT_ENV, traceUser: false });
    cloudState = 1;
  } catch (e) {
    cloudState = 2;
  }
  return cloudState === 1;
}

function authHeader() {
  const t = token();
  return t ? { Authorization: 'Bearer ' + t } : {};
}

function callCloud(path, o, headers) {
  return new Promise(function (resolve, reject) {
    wx.cloud.callFunction({
      name: 'proxy',
      data: { path: path, method: o.method || 'GET', body: o.data || null, headers: headers },
      success: function (res) {
        const r = res && res.result;
        if (r && r.data && typeof r.data.code === 'number') resolve(r.data);
        else reject(new Error('云函数返回异常'));
      },
      fail: function (e) { reject(e); }
    });
  });
}

function callDirect(path, o, headers) {
  return new Promise(function (resolve) {
    wx.request({
      url: BASE + path,
      method: o.method || 'GET',
      data: o.data || {},
      header: Object.assign({ 'Content-Type': 'application/json' }, headers),
      timeout: 15000,
      success: function (res) {
        if (res && res.data && typeof res.data.code === 'number') {
          resolve(res.data);
        } else {
          resolve({ code: -1, message: '返回格式异常' });
        }
      },
      fail: function () {
        resolve({ code: -1, message: '网络异常，请检查网络后重试' });
      }
    });
  });
}

function req(path, opt) {
  const o = opt || {};
  const headers = authHeader();
  if (probeCloud()) {
    return callCloud(path, o, headers).catch(function () {
      cloudState = 2; // 云函数不可用就不要再试，避免每次请求多一次失败往返
      return callDirect(path, o, headers);
    });
  }
  return callDirect(path, o, headers);
}

/** 登录：成功则把令牌与身份落到本地 */
function login(username, password) {
  return req('/api/auth/login', {
    method: 'POST',
    data: { username: username, password: password }
  }).then(function (r) {
    if (r.code === 0) setSession(r.data.token, r.data.user);
    return r;
  });
}

/** 拼查询串，跳过空值 */
function qs(obj) {
  const parts = [];
  for (const k in obj) {
    const v = obj[k];
    if (v === null || v === undefined || v === '') continue;
    parts.push(k + '=' + encodeURIComponent(v));
  }
  return parts.length ? '?' + parts.join('&') : '';
}

function pad2(n) {
  return n < 10 ? '0' + n : '' + n;
}

/** 数字转 yyyy-MM-dd */
function fmtDate(d) {
  return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate());
}

/** 数字转 HH:mm */
function fmtHm(d) {
  return pad2(d.getHours()) + ':' + pad2(d.getMinutes());
}

/** 金额显示：¥2,340；空值返回空串 */
function money(v) {
  if (v === null || v === undefined || v === '') return '';
  const n = Number(v);
  if (isNaN(n)) return '';
  const s = String(Math.round(n));
  let out = '';
  for (let i = 0; i < s.length; i++) {
    const idx = s.length - i;
    out += s.charAt(i);
    if (idx > 1 && (idx - 1) % 3 === 0) out += ',';
  }
  return '¥' + out;
}

module.exports = {
  BASE: BASE,
  req: req,
  login: login,
  token: token,
  user: user,
  roleCode: roleCode,
  setSession: setSession,
  clearSession: clearSession,
  qs: qs,
  pad2: pad2,
  fmtDate: fmtDate,
  fmtHm: fmtHm,
  money: money
};
