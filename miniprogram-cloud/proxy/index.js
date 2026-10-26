// 云函数 proxy：把小程序请求原样转发到门店服务器。
//
// 为什么需要它：小程序线上版只能请求「已备案的 HTTPS 域名」，而门店用公网 IP + 自签证书。
// 走 wx.cloud.callFunction 时，小程序不发起任何公网请求，域名与证书两道限制都不适用。
//
// 安全：
//  1. 只允许 /api/ 前缀，避免这个函数被当成任意请求的中转；
//  2. 用自建 CA 做证书固定（ca 参数），不关校验、不信任其它证书；
//  3. 目标主机写死，不接受调用方传 host。
const https = require('https');

const HOST = '120.26.19.193';
const PORT = 8080;
const TIMEOUT_MS = 12000;

// 门店服务器证书的签发者（deploy/tls/jade-ca.crt）。更换证书时同步替换。
const CA = `-----BEGIN CERTIFICATE-----
MIIDYTCCAkmgAwIBAgIUKuBMtayV6gjTmE3beTVPY+C1Pj0wDQYJKoZIhvcNAQEL
BQAwQDELMAkGA1UEBhMCQ04xEzARBgNVBAoMCkphZGUgU3RvcmUxHDAaBgNVBAMM
E0phZGUgU3RvcmUgTG9jYWwgQ0EwHhcNMjYxMDIzMTcyMjQ0WhcNMzYxMDIwMTcy
MjQ0WjBAMQswCQYDVQQGEwJDTjETMBEGA1UECgwKSmFkZSBTdG9yZTEcMBoGA1UE
AwwTSmFkZSBTdG9yZSBMb2NhbCBDQTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCC
AQoCggEBANy2pc8bLENsRkvnCsMZ6gaejch6ySP2xgD5Bxa4Phhfs/Q65zigNF2I
HAaaxOraPmdooYmVBy9JbD2cEJnMkUSFHdjPHEzFx5wD/zspCRoPqJMUl+cGcKB3
ustGiHpYijVdwCQhk/i4+og78DLcfQzDqGFKiXFRtDh1FVzWeC6qk+dNpmSFwK3c
RjjOhYOvN1mEKWN4knJeGt5kI/3jzLhGp4fbulJZbetnH3l/5DM6XBYGgUixONyp
7BBImhkU5jqrz8PgFJJkQcApAGRqK4UwycwSCatrxMvp7KMUdO/DypeAlZhqgOjS
zUgP1CnthHBCXLzXV0zJ0FtGk8pj0JsCAwEAAaNTMFEwHQYDVR0OBBYEFJNaS2Bx
GeNwHbonP9MVKU17FjXlMB8GA1UdIwQYMBaAFJNaS2BxGeNwHbonP9MVKU17FjXl
MA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQELBQADggEBAEqiK7vTMKrmssdw
3wYIEYAIln3bmaCbQcjhLki90RwQYey5vScmmGONSjvf3qUjO/CjdZv35qtVEJjZ
3id80h3MWGLGhJXf1AdIy8PfdfJ7Xm3OO1Kgs8fLDdvH2Wd1v4LcWUXqI8UKKUAH
JT+MKgjyomMN9yWOrmt5CwWKrPvuTz4cg/1LikbdPdDVn33gml9WoO/v2zeh5nhz
jbRMvsdkBT4vNUcrn4w5jqBytWto5wmV7ugKd2RnntZcqBRedFD3f0B1oo1AfiT0
pKc8m15j688dlf6uBJjxWgn1VXh3gAhDLD9mxfuqWZOQ7hq/SfM/Z6iLx903Wi8h
UKp3K1Y=
-----END CERTIFICATE-----`;

function fail(statusCode, message) {
  return { statusCode, data: { code: statusCode === 400 ? 400 : -1, message } };
}

exports.main = async (event) => {
  const path = event && event.path;
  if (typeof path !== 'string' || path.indexOf('/api/') !== 0) {
    return fail(400, '只允许转发 /api/ 开头的请求');
  }

  const method = String(event.method || 'GET').toUpperCase();
  const payload = method === 'GET' || method === 'HEAD' ? null : JSON.stringify(event.body || {});
  const headers = Object.assign({ 'Content-Type': 'application/json' }, event.headers || {});
  if (payload) headers['Content-Length'] = Buffer.byteLength(payload);

  return new Promise((resolve) => {
    const req = https.request({
      host: HOST,
      port: PORT,
      path,
      method,
      headers,
      ca: CA,           // 只认这张根证书，等价于证书固定
      timeout: TIMEOUT_MS
    }, (res) => {
      let buf = '';
      res.setEncoding('utf8');
      res.on('data', (c) => { buf += c; });
      res.on('end', () => {
        let data;
        try {
          data = JSON.parse(buf);
        } catch (e) {
          data = { code: -1, message: '后端返回的不是 JSON' };
        }
        resolve({ statusCode: res.statusCode, data });
      });
    });

    req.on('timeout', () => {
      req.destroy();
      resolve(fail(504, '后端响应超时'));
    });
    req.on('error', (e) => {
      resolve(fail(502, '后端不可达：' + (e && e.message)));
    });

    if (payload) req.write(payload);
    req.end();
  });
};
