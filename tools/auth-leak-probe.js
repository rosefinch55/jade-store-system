// 认证残留探针：同一条 keep-alive 连接上，先制造一次鉴权失败，再不带令牌连打两个接口。
// 为什么需要它：preHandle 抛异常时 Spring 不会回调本拦截器的 afterCompletion，
// CurrentUser（ThreadLocal）不会被清，下一个请求可能被当成上一个账号处理（2026-10-26 线上实测 10/12 轮命中）。
// 只发 GET，不写数据，可以直接对线上跑：node tools/auth-leak-probe.js [baseUrl] [轮数] [账号:口令]
const http = require('http'), https = require('https');
const BASE = process.argv[2] || 'https://203.0.113.10:8080';
const ROUNDS = Number(process.argv[3] || 12);
const [USER, PASS] = (process.argv[4] || 'lishifuyudiao:123456').split(':');
const u = new URL(BASE);
const lib = u.protocol === 'https:' ? https : http;
const agent = new lib.Agent({ keepAlive: true, maxSockets: 1, rejectUnauthorized: false });

function req(path, auth) {
  return new Promise((resolve, reject) => {
    const headers = { Accept: 'application/json' };
    if (auth) headers.Authorization = 'Bearer ' + auth;
    const r = lib.request({ host: u.hostname, port: u.port || (u.protocol === 'https:' ? 443 : 80),
                            path, method: 'GET', headers, agent, rejectUnauthorized: false }, res => {
      let b = ''; res.on('data', d => b += d);
      res.on('end', () => resolve({ code: res.statusCode, sock: res.socket, body: b }));
    });
    r.on('error', reject); r.end();
  });
}
function post(path, obj) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify(obj);
    const r = lib.request({ host: u.hostname, port: u.port || (u.protocol === 'https:' ? 443 : 80),
                            path, method: 'POST', rejectUnauthorized: false, agent,
                            headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) } },
      res => { let b = ''; res.on('data', d => b += d); res.on('end', () => { try { resolve(JSON.parse(b)); } catch (e) { reject(new Error(b.slice(0, 80))); } }); });
    r.on('error', reject); r.write(body); r.end();
  });
}

(async () => {
  const login = await post('/api/auth/login', { username: USER, password: PASS });
  if (!login.data || !login.data.token) { console.error('登录失败：' + JSON.stringify(login).slice(0, 120)); process.exit(1); }
  const tok = login.data.token;
  // 探针用「本角色没权限的接口」制造 403：/api/transfers 只放 BOSS/SALE
  let reusedRounds = 0, leaked = 0;
  for (let i = 1; i <= ROUNDS; i++) {
    const a = await req('/api/transfers', tok);        // 期望 403：鉴权失败一次
    const b = await req('/api/auth/me', null);         // 裸打，期望 401
    const c = await req('/api/sales?size=1', null);    // 裸打，期望 401
    const same = (b.sock === a.sock && c.sock === a.sock);
    if (same) reusedRounds++;
    const hit = [b, c].filter(x => x.code === 200);
    if (hit.length) leaked++;
    console.log(`round ${String(i).padStart(2)}  失败响应=${a.code}  同连接=${same ? '是' : '否'}  裸打 me=${b.code} sales=${c.code}` +
                (hit.length ? `   <<< 泄漏：拿到了 ${JSON.parse(hit[0].body).data.username || ''}` : ''));
  }
  console.log(`\n同一条连接轮数 ${reusedRounds}/${ROUNDS}，出现身份泄漏 ${leaked}/${ROUNDS}`);
  console.log(leaked === 0 ? '结论：鉴权失败没有把身份留给后续请求' : '结论：存在认证残留，检查拦截器是否清了 CurrentUser');
  agent.destroy();
  process.exit(leaked === 0 ? 0 : 1);
})();
