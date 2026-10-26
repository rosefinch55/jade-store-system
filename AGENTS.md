# 接手说明

写给接下来接手这个项目的 AI 助手。人也可以读，但重点是让助手少走弯路。

读完这页应该能：把服务跑起来、知道每个页面在哪、知道哪些地方是坏的、改完怎么验。

---

## 一、这是个什么系统

单店玉器零售的经营管理系统，课程设计项目，同时也是要真落地的。

店名 **「隆圭琢玉」**（2026-10-25 定，替换原占位名"玉器门店"）。门店 logo 是 `static/logo.png`（双色调圆形玉璧徽记，源自用户 feather 图），五个页面的 title、页头、登录页、管理端侧栏都已挂新品牌，favicon 指向 `/logo.png`。改店名要动五处 html + 桌面快捷方式名。

**门店地址（用户指定的虚构地址，2026-10-25 定）**：乌鲁木齐市新市区鲤鱼山南路 66 号（鲤鱼山公园东门旁），营业 10:00–20:00（与预约时段收口一致）。出现在：门户「到店看货」区与页脚、`mini.html` 预约/预定成功弹窗（F-12 的"成功反馈带地址"就此补齐，小程序端弹窗仍没有地址，改小程序时记得同步）。真定下铺面后全局替换这四个位置。

**图库卫生**：图源是朋友圈导出，混有带播放标的视频封面。`tools/scan-video-cover.py` 用 OpenCV 检测正中心半透明圆（圆心偏移≤6%、半径 8–20% 图宽、圆内亮度>170），命中后**必须拼图人眼复检再清**（2026-10-25 检出 16/359 全真、30 个货品 image 置 NULL，占位样式兜底）。以后补图重跑一遍即可。换 logo 或图后记得给引用加版本参数（如 `/logo.png?v=2`）破 7 天强缓存。

**预约入口分流（2026-10-26）**：门户「预约看货」→ `/mini.html?entry=reserve`，列表只显示当前可约的货品（后端 `/api/public/goods` 加 `reservable=1` 入参，按有效预约占用过滤，过滤在分页前 total 才正确；口径=待确认且在到店时间后 60 分钟保留期内，与详情页「已被预约」标记同源）；列表顶部有「只看可预约货品 / 看全部在售 ✕」切换条。「查询预约」→ `/mini.html?tab=query` 直达查预约页签。小程序端列表未接此参数，行为不变。

**上门交接时段（2026-10-26 结构化）**：供应商与合作商户/师傅在外部端约上门，选日期 + 整点或半点起，一次占一小时（最早 10:00、最晚 19:00 起），库里存 `expect_date` + `start_time` + `end_time`（`end_time` 服务端算）。冲突按**区间重叠**判，并与客户到店预约（`t_reservation.expect_slot` 是 DATETIME）同区间互斥；整段完全相同的并发提交另有函数唯一索引挡。改这个口径要同步 `specs/006-processing`。

**加工「逾期」是派生态**：`status ∈ {待加工, 在加工}` 且过了期限才算逾期（待交付、已交付不催），接口返回 `overdue`/`overdueDays`，管理端可勾选「只看逾期」并点催办（只写 `urge_count`/`last_urge_at`，师傅端显示「门店已催办 N 次」）。没有对外推送通道，催办只是留痕，真催人靠电话。

三端：

| 端 | 页面 | 给谁用 | 终端 |
|---|---|---|---|
| 管理端 | `index.html` | 老板、销售 | 电脑，店内容局域网或直连 IP |
| 客户看货 | `mini.html` | 顾客，免登录 | 手机，扫码打开 |
| 外部协同 | `external.html` | 供应商、加工师傅、合作商户 | 手机，扫码后登录 |

另有 `portal.html` 做门户（三端导览 + 精选货品 + 悬浮二维码）。原来的 `staff.html`（跳转页）与 `web/`（10-24 旧副本）已删。

后端 Spring Boot 3.2 + MySQL 8，一套接口三个页面共用。分析与设计文档在 `docs/thesis/`，规格在 `specs/`，改动前先看对应的 `spec.md`，业务规则以文档为准。

## 二、环境

**仓库**：`E:\claude-code专用文件夹\系统分析与设计\玉器门店系统\`（独立 git 仓库，改之前先 `git status` 和提交快照）

**服务器**：阿里云 ECS `120.26.19.193`，Ubuntu 22.04
```bash
ssh -i C:/Users/ASUS/.ssh/workbuddy_server_key root@120.26.19.193
```

**服务器上的布局**（`/opt/jade/`）

| 路径 | 说明 |
|---|---|
| `app.jar` | 应用包，容器挂载进去 |
| `run-app.sh` | 重建容器脚本，所有启动参数都在这里 |
| `tls/server.p12` | 自签证书，口令 `Jade2026Tls`，别名 `jade` |
| `uploads/` | 上传图片，挂到容器 `/data/uploads`，由 `/uploads/**` 对外 |

容器：`jade-app`（应用，host 网络）、`jade-db`（MySQL 8，口令 `Jade@2026Root`，库 `db_jade`）。

**对外地址**

| 用途 | 地址 |
|---|---|
| 公网（可信证书，微信能打开） | `https://jade-store.serveousercontent.com` |
| 直连（自签证书，微信会拦） | `https://120.26.19.193:8080` |

隧道是 serveo，systemd 服务 `jade-tunnel`，`ssh -R jade-store:80:127.0.0.1:8081 serveo.net`。应用额外用 `--jade.http-port=8081` 开了一个只绑本机的明文端口给隧道回源（隧道自己终结证书）。

**账号**（口令都是 `123456`；外部三个账号 2026-10-26 起改用本人名号拼音）

```text
admin          老板，18 项权限
sale01         销售
yizhuoyimo     供应商（一琢一磨，party_id=19）
lishifuyudiao  加工师傅（李师傅玉雕，party_id=37）
shinian        合作商户（十年，party_id=5）
```

外部账号的往来方绑定在 `t_user.party_id`，不要靠用户名后缀猜。`t_source_party` 里其余 30+ 家往来方（同行商户/供应商/师傅）只是业务记录上的合作方，没有账号；要给谁开通就在柜台端「账号与角色」建号并绑 party_id。

## 三、构建与部署

```bash
cd server && mvn -q -DskipTests package      # 产物 target/app.jar

cd .. && scp -i C:/Users/ASUS/.ssh/workbuddy_server_key -o StrictHostKeyChecking=no \
  server/target/app.jar root@120.26.19.193:/opt/jade/app.jar.new
ssh -i C:/Users/ASUS/.ssh/workbuddy_server_key root@120.26.19.193 \
  "cd /opt/jade && mv app.jar.new app.jar && bash run-app.sh"
```

只有静态文件改动也要走这一遍（静态资源打进 jar）。重启后等 18–20 秒再访问，JVM 没热起来时首请求很慢。

改 `run-app.sh` 也要同步 scp 到服务器，否则下次重建容器会用旧参数。

服务器上的运维脚本源头在 `tools/server/`：`backup-db.sh`（每 7 天备份，见 §六 P2）、`restore-db.sh`（默认恢复到演练库，不碰线上）、`backup.conf`（现场配置，装到 `/opt/jade/backup.conf`）、`jade-backup.cron`（装到 `/etc/cron.d/jade-backup`）。改了同样要 scp 过去，服务器上的副本不是权威源。

## 四、改完必须跑这些

全部在 `tools/`，都不要联网以外的依赖，直接 `node` 或 `bash` 跑。

| 脚本 | 项数 | 覆盖 |
|---|---|---|
| `bash tools/security-test.sh` | 22 | 令牌加密、注入、越权、响应头，只读不写，可以直接对线上跑 |
| `node tools/auth-leak-probe.js` | 12 轮 | 鉴权失败后身份是否残留在 Tomcat 线程上（踩坑第 9 条）。只发 GET，可对线上跑；退出码非 0 就是漏了 |
| `node tools/ui-check.js` | 14 页 | 管理端页面渲染，控制台报错必须为 0 |
| `node tools/role-test.js` | 26 | 四角色接口：出让、完工、价目、上门、逾期与催办、越权 |
| `node tools/mp-sim.js` | 26 | 小程序客户端流程（Node 模拟器，不需要微信）。**会写线上演示库**，见下方说明 |
| `node tools/mp-role-sim.js` | 20 | 小程序外部角色页 |
| `node tools/external-check.js` | 10 | 外部协同 H5：三种身份登录、提交、列表、完工面板、上门时段控件受约束 |
| `node tools/page-check.js` | 12 | 门户 / 客户看板 / 外部协同 × 手机桌面两视口：渲染 + 无脚本异常 |
| `bash tools/reset-demo-data.sh` | — | 清预约/预定测试数据，跑用例前后都该执行 |

`tools/api-test.sh`（47 项，后端业务规则。2026-10-26 二次复核补了 5 项：停用账号不能登录、停用账号错口令仍统一报 401、重置口令的强度门槛；补齐当天就在隔离库实测 47/47）**不要对演示库跑**：它会真实建采购单、成交、调货、寄售，且没有清理逻辑。本机没有 Docker 时，已验证的服务器隔离法：`mysqldump db_jade | 建库 db_jade_apitest 导入` → 起第二个容器（同 jar，`--server.port=18080 --server.ssl.enabled=false --spring.datasource.url=...db_jade_apitest`，记得带 `-e DB_USER -e DB_PWD`，数据源 URL 里的 `&` 不能写成 `\&`；`jade.http-port` 默认 0，不会和线上 8081 抢端口）→ SSH 转发后 `PATH="tools/curlwrap:$PATH" bash tools/api-test.sh http://127.0.0.1:<端口>` → 跑完 `docker rm -f` + `DROP DATABASE`（2026-10-26 实测 47/47；同一份隔离环境又跑了 role-test 26、mp-role-sim 20 全绿。**mp-sim 不能这么跑**，见下面一条）。

**同一份隔离库不能连跑两遍 api-test**：第 5 节「预定额度」按来源 IP 限同时保留 3 条有效预定，第一遍跑完那 3 条还挂着，第二遍三条 POST 全被 429 挡掉，于是报「占满后剩余 0（实际 3）」和「第 4 条预定应 422（实际 429）」。2026-10-26 被这两条误导过一次，看着像代码退化，其实是上一遍没清。要么重灌副本库，要么跑前 `DELETE FROM db_jade_apitest.t_booking`。

跑完记得清数据，否则会污染演示。两个已踩过的坑：外键顺序（t_visit_appt 引用 t_transfer_apply，删申请前先删上门单）；docker exec 直连 mysql 默认 latin1，带中文的 SQL 要加 `--default-character-set=utf8mb4`，否则中文 LIKE 静默匹配不到、UPDATE 中文值报 Data too long。

`ui-check.js` 与 `external-check.js` 依赖 `puppeteer-core`（在 `tools/node_modules` 里，别只查 `puppeteer` 这个包名就下结论）+ 本机 Edge，2026-10-26 实测能跑：14 页 0 报错、9 项全过。页面级故障（某段 `<script>` 整段语法错）只有这两套抓得到，`node --check` 抽内联脚本只是没有浏览器时的兜底。

**`mp-sim.js` 会打线上，不受命令行 baseUrl 管**：它模拟的小程序里 `globalData.base` 写死 `https://120.26.19.193:8080`（脚本第一行输出 `BASE=https://120.26.19.193:8080` 就是证据），所以它的预约/预定**写在演示库**，跑完必须按它打印的手机号删 `t_reservation`/`t_booking`（2026-10-26 就被这条坑过一次：第二次跑报「该货品已被预约」，看着像代码退化，其实是上一轮的数据没清）。`role-test.js`、`mp-role-sim.js`、`api-test.sh` 才真正吃命令行 baseUrl，可以打到隔离库。

```sql
-- 先删引用方，再删被引用方
delete from t_visit_appt where matter like '上门办理%';
delete from t_transfer_apply where name like '%测试%' or name like '%用例%';
-- 预约/预定按测试手机号精确删（脚本用随机号，跑完从输出里抄手机号）
delete from t_reservation where phone='填测试手机号';
delete from t_booking where phone='填测试手机号';
```

role-test.js 会对「第一件在加工任务」提交完工申请（任务变待交付）。清法：删 t_delivery_image、t_delivery_apply 里对应 apply，再把 t_process_task.status 改回「在加工」。预约/预定的清空见 `reset-demo-data.sh`（清空整表，跑之前先确认没有真实客户预约）。

## 五、视觉规范（改样式前必读）

四个页面共用一套令牌，改配色要同步改四处（已记录为已知债）：

```
宣纸底 #FBF7F0   次级底 #F4EEE3   卡片 #FFFFFF
墨玉 #22352F / #2C4A41   哑金 #A9843F / #8A6B2F   朱砂 #B23A2B
正文 #2C3531   次要 #6B7772   更淡 #8A948F
描边 #E6DECF / #F0E9DC
标题 Songti SC / STSong / SimSun（衬线）  正文 PingFang SC / 微软雅黑
圆角 4px（弹层 12px 顶角）  分隔线 1px  阴影极浅
手机视口基准 390×844，内容最大宽 640–680 居中
```

品牌视觉（2026-10-25 定，参考传统珠宝行官网公约）：

- 徽记 = `logo.png` 双色调圆形玉璧（阴影墨玉绿、高光暖白，源自用户 feather 图），页头一律圆形 + 哑金细环（`box-shadow` 双环），不再用方形照片块——方形灰黑图与宣纸底冲突、小尺寸不可识
- 字标中英配对：隆圭琢玉（衬线大字距）+ LONGGUI JADE（9px 金色宽字距），门户页头/页脚、管理端登录页都有
- 门户 hero 居中对称：kicker 两端细线、题下英文行、按钮居中；章节题 ❖ 花锚对称，辅助链接桌面端绝对定位右上
- 排版底线：标题只衬线、字距只加不减、金色只做细线与小字，不大面积铺

产品语气：克制、具体、不用感叹号、不写营销词。价格一律"到店面谈"，页面上只给"参考价上界"。

## 六、当前问题清单

按严重程度排。前两条会直接影响演示效果，先处理。

### P0 · 管理端在公网域名下白屏 —— 已按方案①处置（2026-10-25 拍板）

`index.html` 依赖 `lib/element-plus.js`（959KB）+ `lib/element-plus.css`（327KB）。经 serveo 隧道访问时这两个文件稳定报 `ERR_HTTP2_PROTOCOL_ERROR`，整页白屏。直连 IP 正常（`https://120.26.19.193:8080/index.html`）。

**已落地**：门户「管理端」卡片与页脚「门店员工入口」都改为直连地址，卡片文案标注"请用店内电脑打开"。管理端的承诺访问路径 = 店内电脑直连/局域网，不再承诺公网隧道打开。

若以后要撤销这个决定（让管理端在任意网络可开），剩下的路只有：去掉 Element Plus 重写 13 个页面的表单表格（参考 `mini.html` 的自建控件 `.btn/.field/.row/.inp/.mask/.sheet/.toast`），或换正式域名+备案。

顺带修正一段旧结论：当初"开了 gzip 也没用"其实是 gzip 根本没到隧道——`server.compression` 只作用于主连接器，隧道回源的 8081 明文连接器没吃到配置。2026-10-25 已给 8081 单独开压缩（vue.js 经隧道 145KB→53KB）并给静态资源加了 7 天 Cache-Control（见踩坑第 7 条）。隧道下 Element Plus 是否因此能过未再验证，反正方案①已承诺店内直连。

### P0 · 客户看板刚做过大改，需要重新验

`mini.html` 为了摆脱 Element Plus，把两个弹窗（预约看货、预定这件）从 `el-dialog` 换成了自建 `.sheet`，时间选择从 `el-date-picker` 换成原生 `input type="date"` + `input type="time"`。

已验证（2026-10-25 全量浏览器实测，390×844 与 768 视口，隧道 + 直连）：列表/详情渲染、独占提示、返回列表与浏览器后退键一致、预约四项校验（空姓名/短手机号/缺日期/窗口外时段全部拦截且文案正确）、合法预约拿到编号、预定额度查询与提交、按后四位查预约（隐私裁剪正确）、768 两列布局、无控制台报错。预约/预定成功用**自建居中结果弹窗**（`.dlg-mask/.dlg`，编号+时段/额度+地址，强制确认：点遮罩不关闭、只有点「已保存」才放行——2026-10-26 按用户要求改，原生 alert 在微信里贴底弹出且位置不可控，已弃用）。

**没验过**：

- `input type="time" step="1800"` 在 iOS Safari 上是否真的按半小时步进（手上没有 iOS 设备）

### P1 · 二维码是写死的图片

门户右下角悬浮码现在是两张（2026-10-25 起）：`qr-share.png` → `/mini.html`（顾客看货）、`qr-external.png` → `/external.html`（外部协同登录），桌面访客扫码用，≤820px 视口隐藏。隧道改名或换域名后两张图都指错地方。

重新生成（在项目根目录跑，颜色用的是设计令牌墨玉/宣纸底）：

```bash
node -e "const QR=require('./tools/node_modules/qrcode');QR.toFile('server/src/main/resources/static/qr-external.png','https://<域名>/external.html',{width:520,margin:2,errorCorrectionLevel:'M',color:{dark:'#22352F',light:'#FBF7F0'}},e=>console.log(e||'ok'))"
```

理想做法仍是 JS 按 `location.origin` 现生成。另外：店内电脑桌面已放「玉器门店管理端.url」快捷方式指向 `https://120.26.19.193:8080/index.html`（该机已信任自签 CA，双击直达）。

### P1 · serveo 免费隧道首次访问有英文警告页

serveo 对匿名隧道插一页"Serveo Browser Warning"（提示不要输入敏感信息，要点 Continue to Site）。顾客扫码第一次会先看到它，对演示观感有影响；同一浏览器后续访问不再出现。代码侧无解，根治要换付费隧道或自有备案域名（与 P0 第一条同源）。

### P1 · 登录态没有续期 —— 已修复（2026-10-25）

`external.html` 令牌 8 小时过期：原来页面带着失效令牌继续显示，列表请求 401 被当成空数据，显示"还没有提交过申请"（假空状态）。已改：`call()` 统一拦 401 → 清本地会话 → 回登录页。柜台端 `index.html` 同日修了登录接口 401 被误判成会话失效的问题（原来错误口令会弹"登录已失效"并强制刷新，正确报错"用户名或口令错误"显示不出来）。若要做"过期自动续期"，两个文件的 `call()`/`api.req()` 是同一个改动点。

### P1 · 每次只改一处会漏

小程序（`miniprogram/`）和 H5（`mini.html`、`external.html`）是两套独立代码，业务规则一样但实现各写一遍。改预约规则、加字段，两边都要动，否则不一致。

**2026-10-26 用户确认：小程序端停工，实现方法换成 H5**。`miniprogram/` 作为代码留存保留，改业务规则不再要求同步改它，上传/发版流程（`tools/mp-upload.js`、IP 白名单那套）也不再维护；相应地 `mp-sim.js`/`mp-role-sim.js` 是它的壳回归，只在真回去动 `miniprogram/` 时才需要跑。H5 侧的等价覆盖是 `external-check.js`（9 项）+ 浏览器过 `mini.html`。

### P2 · 其他

- 菜单权限裁剪只盖了硬失败页（2026-10-25）：销售与周转（UC-06）、账号与角色（UC-12）按登录账号的权限码隐藏，其余页读取接口本就开放给员工、写操作由 @RequirePerm 兜底。分期管理的"调货台账结算/收款记录"页签等混合权限页对缺码角色仍会在对应页签报"没有该功能的权限"，属数据粒度问题（t_role_permission 种子），不是前端漏裁
- 柜台端与外部端共用同一组 localStorage 键（jade_token/jade_user），同一浏览器互相顶号是既有设计；老板令牌打开 external.html 会看到"请用电脑端"引导页
- 权威源只有 `server/src/main/resources/static/`；10-24 的 `web/` 旧副本已删，别再按旧路径找页面
- 演示数据审阅项：货品名"和田玉手镯温润如月光透露出半分暖意"带营销形容词、员工姓名自带"（销售）"后缀（柜台端显示时已去重）。两者都在演示数据里。加工任务期限过期那条已经不再是问题：2026-10-26 起「逾期」在管理端与师傅端都看得见（实测 111 条逾期：待加工 33 + 在加工 78），老板可点催办留痕，要不要结掉仍是数据层面的决定
- `external.html` 完工照片压缩走 canvas，HEIC 在部分安卓机读不出，会静默失败
- 商户价目只有"加载更多"，没有分类筛选和回到顶部
- `index.html` 只按桌面做过，窄屏会挤
- 公网防刷现状（2026-10-26 验收）：预约 per-IP 有效数≤3 + 违约≥10 拉黑（exempt 解除）+ DB 函数唯一索引兜底；预定同口径 per-IP≤3 + `uk_booking_goods_active` 函数唯一索引（并发双写 1062 转 409）；登录按账号 5 败锁 10 分钟（423，老板可重置口令解锁）；分页 size 硬顶 100；上传需登录且只放老板/销售/师傅（2026-10-26 加，供应商与合作商户页面没有上传口）；`/api/**` 请求体超过 10MB 由 `RequestBodySizeFilter` 看 Content-Length 直接 413，一个字节都不读（multipart 的 8MB/10MB 只管表单，Jackson 的 20M 字符上限要读完整包才撞线）。真被打穿的前提取决于攻击者换 IP，应用层能做的都在这了，再往上要 WAF/CDN
- `/opt/jade/uploads` 没有清理策略，图片会一直堆
- **数据库备份已落地（2026-10-26）**：`tools/server/backup-db.sh` 每 7 天跑一次（`/etc/cron.d/jade-backup`，每周日 04:30），本地 `/opt/jade/backup` 留 4 份，**每次跑完把包还原进临时库比对表数与货品数**（没验证过的备份不算备份）。外置硬盘插上任一 `EXT_DIRS` 路径就自动多导一份并留 12 份，没插只记一行日志（配置在 `/opt/jade/backup.conf`，源在 `tools/server/backup.conf`）。恢复用 `tools/server/restore-db.sh`：默认恢复到 `db_jade_restore` 演练库，要覆盖线上必须 `--into-live --yes`，且会先把现库另存。首跑实测：76KB 包、33 张表、680 件货品比对通过

## 七、这个项目踩过的坑

1. **Java 文件是 CRLF**。用 python 做 `str.replace` 插代码，带 `\n` 的模式匹配不上会静默失败，编译时才报"找不到符号"。改 Java 用 Edit 工具逐处改，别批量替换。
2. **本机有全局代理**（`HTTP_PROXY=127.0.0.1:xxxx`）。`curl` 测隧道会被拖慢甚至判断失真，测网络用 `--noproxy '*'`。
3. **安全策略拦程序**。`cmd.exe`、`reg.exe`、`wsl.exe`、`wmic.exe` 都在黑名单，所以微信开发者工具的命令行调不通，`npm install` 也可能失败。上传小程序改成在服务器上跑 `miniprogram-ci`。
4. **小程序上传需要 IP 白名单**。密钥白名单里要有 `120.227.57.74`（家里）和 `120.26.19.193`（服务器）。上传命令见 `tools/mp-upload.js`，但本机装不上依赖时改在服务器跑。
5. **小程序发不出去的两条硬原因**：没有备案域名（微信只认已备案的 HTTPS 域名，不认 IP 加端口）；证书是自签的（微信内置浏览器直接拦）。发布还要过平台审核，云开发按量计费没有免费额度。README 的「关于微信小程序」一节写了完整说明。
6. **测试脚本的等待时间**。部署完立刻跑走查会偶发失败（JVM 未热），`external-check.js` 已把登录等待放宽到 2.4 秒。别把它改回去。
7. **`server.compression` 只作用于自动配置的主连接器**。`ExtraHttpConnectorConfig` 用 `addAdditionalTomcatConnectors` 加的 8081 明文连接器必须自己 `setProperty("compression","on")` + `compressibleMimeType`，否则隧道流量全量明文出境（微信扫码慢的直接原因，2026-10-25 修）。静态缓存一并看：`SecurityHeadersFilter` 给 `/lib /images /uploads`、两张码、`/logo.png` 发 `public, max-age=604800`，HTML 发 no-cache——隧道每秒只有几十 KB，二次访问全靠浏览器缓存。
8. **从 PowerShell 里 ssh 执行远端命令，嵌套的双引号会被吞掉**。`ssh root@host "mysql -e \"SHOW DATABASES\""` 到远端会变成 `-e SHOW DATABASES` 被当两条命令跑（2026-10-26 实测：`DROP DATABASE` 就这么静默没执行）。要么在 PS 里用单引号包整条、远端参数写成 `''…''`，要么把脚本 scp 上去 `bash /tmp/x.sh`；远端 `docker ps --format {{.Names}}={{.Status}}` 这种不含空格的写法最稳。顺带：`.sh` 必须是 LF，仓库已用 `.gitattributes`（`*.sh text eol=lf`、`*.cron text eol=lf`）钉住；手头脚本若还是 CRLF，scp 上去会 `$'\r': command not found`，在本地先 `.Replace("`r`n","`n")` 再传（别学远端 `tr -d "\r"`——PS 里那对双引号会被吞成"删掉字母 r"，把脚本里的 `docker` 变成 `docke`）。curl 传带中文或引号的 JSON body 也别在 PS 里内联，会被转义成非法 JSON、服务端回 500，用 `--data-binary @文件`。
9. **鉴权失败时 `afterCompletion` 不会回调，ThreadLocal 不会自动清**。`AuthInterceptor.preHandle` 抛异常时，Spring 的 `HandlerExecutionChain.interceptorIndex` 还停在 -1，本拦截器的 `afterCompletion`（里面做着 `CurrentUser.clear()`）根本不被调用，身份就留在该 Tomcat 线程上。2026-10-26 线上实测：同一条 keep-alive 连接上先让鉴权失败一次（师傅打老板接口 403，或扔个废令牌），紧接着**不带令牌**的请求 12 轮里 10 轮被当成上一个账号处理，`/api/auth/me` 直接返回别人的资料。现在所有拒绝都走 `deny()`，在里面自己清上下文并留痕。以后加拦截器同理：`preHandle` 抛异常等于自己负责收尾。
10. **curl 配置文件里的 `header = "X:"` 不会删掉前面设过的请求头**。它不像命令行 `-H "X:"` 那样是"删除"语义，会把上一行 `url` 设的 `Authorization` 一路带到后面的请求里——用 `-K` 写"先带令牌再裸打"的多段测试时，第二条其实还带着令牌，能凭空造出一个"身份泄漏"的假阳性（2026-10-26 就被它骗过一次，判成漏洞）。要精确控制请求头就别用配置文件，用 Node 手写请求，或直接开两条连接做对照。

## 八、建议的推进顺序

1. ~~决定管理端怎么处理（P0 第一条）~~（2026-10-25 拍板方案①并落地：门户管理端入口改直连地址+标注店内电脑打开）
2. ~~补完 `mini.html` 的预约/预定链路回归~~（2026-10-25 已全量实测并修复：返回键、窗口外兜底、768 布局、预定额度与弹窗反馈；后续改预约规则时重跑 `mp-sim.js` + 浏览器过一遍即可）
3. 二维码改成动态生成
4. ~~401 统一回登录页~~（2026-10-25 已修复：external 的 `call()`、admin 的 `api.req()` 都已处理）
5. 小程序与 H5 的重复实现，挑一处收敛（短期优先 H5，小程序作为代码留存）
6. ~~备份这类运维尾巴~~（2026-10-26 备份已装：每 7 天一次 + 还原自检 + 外置盘接口，见 §六 P2）。剩下 `/opt/jade/uploads` 清理一条没做

## 九、几条红线

- 页面文案不出现"本报告/本系统/本页面"这类称呼，直接说名词
- 不写提示词痕迹、不写"演示环境""测试账号"这类字样，账号提示只说"由门店开通"
- 不编造业务数据。价格、库存、台账一律以库里的真实数据为准（例外：门店地址由用户拍板虚构，见 §一）
- 改业务规则前先看 `specs/*/spec.md`，接口层的校验规则（预约窗口、额度、状态机）不要为了前端方便而放松
- 大改之前先 `git commit` 留快照

## 十、交接状态（2026-10-26 收尾）

### 已完成

- **35 条差异全部处置**（报告 §三）。A 组 14 条改论文：表数 32 与 `t_op_log`、`party_id`/`is_featured` 字段、UC-15/UC-02 矩阵、三个状态机的实现口径（不含"已调出"、不含"已到店/已取消"、寄售无自动到期、分期逾期是派生态）、业务层与接口路径说明、限流豁免归属。B 组 17 条写进各自 `specs/*/spec.md` 的差异段，带 B 编号。C 组：C1–C3 进 README「上线前检查清单」，C4 的备份表已导出后删除。
- **B1 由"记录缺陷"改为真修**：寄售建档与终止各补一条库存流水（`TradeController`），实测两条都落库。
- **论文重编**：MiKTeX 在 `D://miktex//texmfs//install//miktex//bin//x64`，两遍 xelatex，142 页、0 未定义引用。`docs/thesis/逐页检查结果.md` 已按新 PDF 整体重建 142 行。
- **页面断言**：新增 `tools/page-check.js`（门户/客户看板/外部协同 × 手机桌面两视口），直连与隧道各 12/12。
- **清理**：`staff.html`、`web/` 已删；实库 32 张表；上传目录清空并落地 `tools/server/prune-uploads.sh`（默认 dry-run，`--delete` 才删，按 `t_goods.image`、`t_apply_image.image_path`、`t_delivery_image.image_path` 三处反查）。
- **外置硬盘**只做接口预留，不要求实测（用户 2026-10-26 明确）。

### 还没做

1. **`specs/*/tasks.md` 118 个 `[ ]` 没回填**。要逐条对照代码，别整体打勾；这项最耗时间，优先级看交付要求。
2. **`_release` 子模块仍 dirty**：里面 `analysis-design-document.pdf` 还是 10-24 的构建。等论文定稿后三选一——提交进去、`git submodule update --checkout` 还原、或从父仓库移除。
3. **时段校验缺端到端页面断言**：`external-check.js` 只验 `step/min/max` 属性，没验"填 09:00 提交会被拒"；接口层由 `role-test.js` 覆盖。
4. **iOS Safari 的 `step="1800"` 未实测**（三处用到：`mini.html` 预约 + `external.html` 两个上门表单），没有真机。
5. **没有 git 远端**（用户说「远端别急」）。代码只有本机一份，jar 在服务器。
6. **小程序不发布**：备案域名 + 受信任证书两条硬门槛，H5 已覆盖九个界面（见 README「关于微信小程序」）。
