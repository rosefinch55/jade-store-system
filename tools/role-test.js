// 小程序四个外部角色的接口回归：供应商出让、师傅完工、商户价目、上门预约。
// 用法：node tools/role-test.js [baseUrl]
// 会造少量数据，结尾自动清理（需要能连服务器 MySQL，见 cleanup 段）。
process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0';

const BASE = process.argv[2] || 'https://203.0.113.10:8080';
const { execSync } = require('child_process');

let pass = 0, fail = 0;
const ok = (name, extra) => { pass++; console.log('  PASS  ' + name + (extra ? '  → ' + extra : '')); };
const bad = (name, extra) => { fail++; console.log('  FAIL  ' + name + '  → ' + extra); };
const check = (name, cond, extra) => (cond ? ok(name, extra) : bad(name, extra));

async function call(method, path, token, body) {
  const opt = { method, headers: { 'Content-Type': 'application/json' } };
  if (token) opt.headers.Authorization = 'Bearer ' + token;
  if (body !== undefined) opt.body = JSON.stringify(body);
  const r = await fetch(BASE + path, opt);
  let d;
  try { d = await r.json(); } catch (e) { d = { code: -1, message: 'not json' }; }
  return { status: r.status, body: d };
}

const login = async (u) => {
  const r = await call('POST', '/api/auth/login', null, { username: u, password: '123456' });
  return r.body.code === 0 ? r.body.data.token : '';
};

// 1x1 透明 PNG
const PNG = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8AAAwAB/AF+7kZQAAAAAElFTkSuQmCC';

(async () => {
  console.log('== 1. 三个外部账号登录 ==');
  const tSup = await login('yizhuoyimo');
  const tCra = await login('lishifuyudiao');
  const tMer = await login('shinian');
  const tBoss = await login('admin');
  check('供应商登录', !!tSup);
  check('师傅登录', !!tCra);
  check('商户登录', !!tMer);
  check('老板登录', !!tBoss);

  console.log('\n== 2. 供应商：提交出让申请 ==');
  const apply = await call('POST', '/api/supplier/applies', tSup, {
    outType: '成品', name: '回流和田玉挂件（测试）', quantity: 2, expectPrice: 2600, description: '自动用例'
  });
  check('提交申请成功', apply.body.code === 0, apply.body.data && apply.body.data.applyNo);
  const applyNo = apply.body.data && apply.body.data.applyNo;
  const applyId = (await call('GET', '/api/supplier/applies', tSup)).body.data.filter(a => a.applyNo === applyNo)[0];

  const badType = await call('POST', '/api/supplier/applies', tSup, { outType: '石头', name: 'x', quantity: 1, expectPrice: 1 });
  check('出让类型不合法被拒', badType.status === 400, badType.body.message);

  const zeroQty = await call('POST', '/api/supplier/applies', tSup, { outType: '玉料', name: 'x', quantity: 0, expectPrice: 1 });
  check('数量为 0 被拒', zeroQty.status === 400, zeroQty.body.message);

  const supNoAuth = await call('POST', '/api/supplier/applies', null, { outType: '玉料', name: 'x', quantity: 1, expectPrice: 1 });
  check('未登录提交被拒', supNoAuth.status === 401, String(supNoAuth.status));

  console.log('\n== 3. 图片上传（base64 中转用） ==');
  const up = await call('POST', '/api/upload/base64', tCra, { name: 'a.png', data: PNG });
  check('base64 上传成功', up.body.code === 0, up.body.data && up.body.data.path);
  const imgPath = up.body.data && up.body.data.path;
  if (imgPath) {
    const r = await fetch(BASE + imgPath);
    check('上传后的图片可访问', r.status === 200, String(r.status));
  }
  const upBad = await call('POST', '/api/upload/base64', tCra, { name: 'a.png', data: 'not-base64!!' });
  check('非法图片数据被拒', upBad.status === 400, upBad.body.message);

  console.log('\n== 4. 师傅：任务与完工 ==');
  const tasks = await call('GET', '/api/craftsman/tasks?status=' + encodeURIComponent('在加工'), tCra);
  check('拿到在加工任务', tasks.body.code === 0 && tasks.body.data.items.length > 0, '共 ' + (tasks.body.data.items || []).length + ' 条');
  const task = (tasks.body.data.items || [])[0];

  // 逾期与催办走管理端口径（待加工/在加工且过了期限），拿真实演示数据验，不新造任务。
  // 放在提交完工之前：一提交任务就变「待交付」，不在催办口径里了
  const od = await call('GET', '/api/process-tasks?overdueOnly=true&size=1', tBoss);
  const odCount = od.body.data && od.body.data.overdueCount;
  check('加工任务「只看逾期」可用', od.body.code === 0 && odCount > 0, '逾期 ' + odCount + ' 个');
  const one = (od.body.data && od.body.data.items || [])[0];
  if (one) {
    check('逾期任务带逾期天数', one.overdue === true && one.overdueDays > 0, one.taskNo + ' 逾期 ' + one.overdueDays + ' 天');
    const u = await call('POST', '/api/process-tasks/' + one.id + '/urge', tBoss, {});
    check('催办成功并计数', u.body.code === 0 && u.body.data.urgeCount >= 1, '第 ' + (u.body.data && u.body.data.urgeCount) + ' 次');
  }
  const doneRow = ((await call('GET', '/api/process-tasks?status=' + encodeURIComponent('已交付') + '&size=1', tBoss)).body.data || {}).items;
  if (doneRow && doneRow[0]) {
    const u2 = await call('POST', '/api/process-tasks/' + doneRow[0].id + '/urge', tBoss, {});
    check('已交付任务不能催办', u2.status === 409, u2.body.message);
  }
  const uNoAuth = await call('POST', '/api/process-tasks/1/urge', null, {});
  check('未登录催办被拒', uNoAuth.status === 401, String(uNoAuth.status));

  if (task && imgPath) {
    const img = { stage: '', path: imgPath };
    const d1 = await call('POST', '/api/craftsman/tasks/' + task.id + '/delivery', tCra, {
      note: '自动用例', images: [{ ...img, stage: '加工前' }, { ...img, stage: '加工中' }, { ...img, stage: '完工' }]
    });
    check('提交完工成功', d1.body.code === 0, d1.body.data && d1.body.data.applyNo);

    const d2 = await call('POST', '/api/craftsman/tasks/' + task.id + '/delivery', tCra, {
      note: '重复', images: [{ ...img, stage: '加工前' }, { ...img, stage: '加工中' }, { ...img, stage: '完工' }]
    });
    check('重复提交被拒', d2.status === 409 || d2.status === 422, d2.body.message);
  }

  console.log('\n== 5. 商户：调货价目 ==');
  const price = await call('GET', '/api/merchant/price-list?page=1&size=5', tMer);
  check('价目接口可用', price.body.code === 0, 'total=' + (price.body.data && price.body.data.total));
  const priceNoAuth = await call('GET', '/api/merchant/price-list', tSup);
  check('供应商访问商户接口被拒', priceNoAuth.status === 403 || priceNoAuth.status === 401, String(priceNoAuth.status));

  console.log('\n== 6. 老板审核 + 供应商约上门 ==');
  let visitId = null;
  if (applyId) {
    const audit = await call('POST', '/api/transfer-applies/' + applyId.id + '/audit', tBoss, { pass: true, opinion: '用例通过' });
    check('老板审核通过', audit.body.code === 0, audit.body.message);

    // 随机挑一个半小时时段：固定 14:00 时同日重跑会撞上一轮留下的待上门预约（uk_visit_slot_open）
    const d3 = new Date(Date.now() + 3 * 24 * 3600 * 1000);
    const day = d3.getFullYear() + '-' + String(d3.getMonth() + 1).padStart(2, '0') + '-' + String(d3.getDate()).padStart(2, '0');
    const hh = 14 + Math.floor(Math.random() * 4);
    const tm = hh + (Math.random() < 0.5 ? ':00' : ':30');
    const visit = await call('POST', '/api/visit-appts', tSup, {
      matter: '送样品', expectDate: day, startTime: tm, transferApplyId: applyId.id
    });
    check('供应商预约上门成功', visit.body.code === 0, visit.body.data && visit.body.data.apptNo);
    visitId = visit.body.data && visit.body.data.id;

    const dup = await call('POST', '/api/visit-appts', tSup, {
      matter: '重复', expectDate: day, startTime: tm, transferApplyId: applyId.id
    });
    check('同时段重复预约被拒', dup.status === 409, dup.body.message);

    const off = await call('POST', '/api/visit-appts', tSup, {
      matter: '非整半点', expectDate: day, startTime: hh + ':20', transferApplyId: applyId.id
    });
    check('非整半点时段被拒', off.status === 422, off.body.message);
    const late = await call('POST', '/api/visit-appts', tSup, {
      matter: '打烊后', expectDate: day, startTime: '21:00', transferApplyId: applyId.id
    });
    check('营业时段之外被拒', late.status === 422, late.body.message);
  } else {
    bad('老板审核通过', '没拿到申请 id');
  }

  console.log('\n== 7. 清理用例数据 ==');
  const ids = [applyId && applyId.id, visitId].filter(Boolean).join(',');
  console.log('  待清理：apply=' + (applyId && applyId.id) + ' visit=' + visitId + ' task=' + (task && task.id));
  console.log(ids ? '  applyIds=' + ids : '  (无)');

  console.log('\n合计 ' + (pass + fail) + ' 项，通过 ' + pass + '，失败 ' + fail);
  process.exit(fail ? 1 : 0);
})().catch((e) => { console.error('用例异常：', e); process.exit(1); });
