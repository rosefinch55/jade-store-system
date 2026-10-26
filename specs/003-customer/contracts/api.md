# 接口契约：客户与到店

**风格**：REST，前缀 `/api`
通用约定见 `../README.md`。

## 两组接口，鉴权不同

| 分组 | 前缀 | 鉴权 | 使用者 |
|---|---|---|---|
| 内部 | `/api/customers`、`/api/bookings` | 需登录 | 销售、老板 |
| **公开** | `/api/public/*` | **免登录** | 客户（小程序/手机浏览器） |

公开接口单独一段前缀，不是给内部接口加白名单——这样拦截器配置只需「放行 `/api/public/**`」，不会漏。

## 内部：客户档案

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/customers` | 列表，按编号/姓名/手机号/微信号/标签筛选 |
| GET | `/api/customers/{id}` | 详情 |
| POST | `/api/customers` | 新建 |
| PUT | `/api/customers/{id}` | 修改 |
| GET | `/api/customers/{id}/purchases` | 购买记录（由成交汇总） |
| PUT | `/api/customers/{id}/tags` | 覆盖式更新标签 |

### POST /api/customers

```json
{
  "name": "王某", "phone": "13800138000", "wechat": "wx_wang",
  "sourceChannel": "朋友介绍", "introducerId": 12,
  "prefCategory": "和田玉手镯", "prefSize": "56", "prefBudget": 15000,
  "tags": ["大客户", "爽快"], "remark": "喜欢籽料"
}
```

| 场景 | 返回 |
|---|---|
| `phone` 已存在 | 409，`data` 带原客户档案与 id |
| `sourceChannel` 不在七种内 | 400 |
| `tags` 含非法值 | 400 |
| `sourceChannel = 朋友介绍` 但无 `introducerId` | 400 |

**409 的处理**：前端收到后不报错，直接跳转到原客户档案的编辑页。这是 UC-04 扩展 2a 的行为。

### GET /api/customers/{id}/purchases

```json
{ "code": 0, "message": "ok", "data": [
  { "saleNo": "CJ2026102200001", "goodsName": "和田玉手镯",
    "price": 12800.00, "saleTime": "2026-10-22 23:40:00", "returned": false }
]}
```

只读汇总，不接受写入（FR-006）。

## 公开：浏览货品

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/public/goods` | 在柜货品列表（推荐位 + 全部库） |
| GET | `/api/public/goods/{goodsNo}` | 单件展示页 |

### GET /api/public/goods

查询参数：`featured`（true 只看推荐位）、`categoryId`、`keyword`、`page`、`size`。

**返回字段只含一个价格：`priceUpper`**——即对外报价上界（最低售价 × 1.25 取整到十位）。`cost`、`minPrice`、`transferPrice` 一律不下发，这是 DTO 层面的裁剪，不是前端隐藏。

```json
{ "code": 0, "message": "ok", "data": {
  "total": 679, "page": 1, "size": 20,
  "items": [{
    "goodsNo": "JD20261022000002", "name": "和田玉挂件",
    "categoryName": "挂件吊坠", "material": "和田玉", "origin": "新疆和田",
    "shape": "挂件", "weightG": 31.45, "size": null,
    "image": "/images/goods/img_dedecf4fcf474725.jpg",
    "featured": true, "priceUpper": 1580, "reservable": true
  }]
}}
```

`featured` 对应 `t_goods.is_featured`。首页推荐区调用 `?featured=true`，全部库不传该参数。

### GET /api/public/goods/{goodsNo}

单件详情，带完整描述。**成交过的货品返回 404**（FR-012）。

## 公开：看货预约

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/api/public/reservations` | 提交预约 |
| GET | `/api/public/reservations` | 按手机号后四位查 |

### POST /api/public/reservations

```json
{ "goodsNo": "JD20261022000002", "name": "王某",
  "phone": "13800138000", "expectSlot": "2026-10-24 15:00" }
```

`expectSlot` 必须是 `yyyy-MM-dd HH:mm`，且满足三条：落在「提交时刻 +2 小时」至「提交时刻 +3 天」之间（两端含边界）、分钟为 `00` 或 `30`、货品与时段都未被占用（FR-030、FR-031）。

| 场景 | 返回 |
|---|---|
| 货品不存在或非在柜 | 404 |
| 手机号格式不合法 | 400 |
| `expectSlot` 缺失或格式不符 | 400，提示格式或要求选择 |
| `expectSlot` 早于 +2 小时或晚于 +3 天 | 400，`message` 给出最早/最晚可约时刻 |
| `expectSlot` 分钟不是 00 或 30 | 400，提示请选择整点或半点 |
| 该货品已被他人有效预约 | 409，提示该货品已被预约 |
| 该半小时时段已被占到店或上门 | 409，提示「该时段已约满，请选择其他时间」 |
| 同手机号再次提交、新时间晚于原时间 | 409，提示只能提前，如需推迟联系门店 |
| 同手机号再次提交、新时间早于原时间 | 0，更新原预约的 `expectSlot`，`resvNo` 不变 |
| 同一来源 IP 已持有 3 条有效预约 | 429，提示同网络最多同时 3 条，改期不占新名额 |

**响应**

```json
{ "code": 0, "message": "ok", "data": {
  "resvNo": "YY202610220001", "expectSlot": "2026-10-24 15:00",
  "address": "门店地址占位", "tip": "请截图保存预约编号"
}}
```

### GET /api/public/reservations?phoneTail=8000

```json
{ "code": 0, "message": "ok", "data": [
  { "resvNo": "YY202610220001", "expectSlot": "2026-10-24 15:00", "goodsName": "和田玉挂件" }
]}
```

**不返回姓名、完整手机号、价格**（FR-018）。

**防遍历**：按 `phoneTail` 查可能被猜，加两条限制——同一 IP 每分钟最多 10 次，返回条数上限 20。超限返回 429。

## 公开：商品预定

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/public/bookings/quota?phone=13800138000` | 查该号码剩余额度 |
| POST | `/api/public/bookings` | 提交预定 |

### GET /api/public/bookings/quota

```json
{ "code": 0, "message": "ok", "data": { "phone": "138****8000", "used": 1, "remaining": 2 } }
```

页面上显示「剩余 2 次」用（UC-13 设计约束要求额度可见）。

### POST /api/public/bookings

```json
{ "goodsNo": "JD20261022000002", "name": "王某", "phone": "13800138000" }
```

| 场景 | 返回 |
|---|---|
| 额度已满（占用 ≥ 3） | 422 |
| 该货品已有有效预定 | 409 |
| 货品非在柜 | 404 |

## 内部：预定跟进

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/bookings/today` | 当日到店名单 |
| POST | `/api/bookings/{id}/contact` | 标记已联系 |
| POST | `/api/bookings/{id}/result` | 登记到店结论 |
| POST | `/api/goods/{id}/reserve` | 货品置「预留」 |
| POST | `/api/goods/{id}/unreserve` | 取消预留，回「在柜」 |

### GET /api/bookings/today

按时段升序（`submit_time`），只含状态「待到店」「已联系」。

```json
{ "code": 0, "message": "ok", "data": [
  { "id": 12, "bookingNo": "YD202610220003", "name": "王某", "phone": "13800138000",
    "goodsName": "和田玉挂件", "slot": "今天下午", "status": "待到店" }
]}
```

### POST /api/bookings/{id}/contact

无请求体。写入 `contact_time`，状态改「已联系」。

| 场景 | 返回 |
|---|---|
| 已标记过 | 409，提示重复标记 |
| 状态非「待到店」 | 409 |

### POST /api/bookings/{id}/result

```json
{ "arrived": true }
```

`arrived=true` → 「已达成」，释放 1 条额度；`false` → 「已爽约」，额度不变。

**响应**带回该手机号的剩余额度，方便销售当场告知客户。

### POST /api/goods/{id}/reserve 与 /unreserve

这两个是货品状态变更，放在这里是因为由预定流程触发（UC-13 规则⑥）。

`reserve`：在柜 → 预留，写流水。
`unreserve`：预留 → 在柜，写流水。

两者都不改 `t_booking.status`——预定状态与货品状态是两条独立的线。

## 前端页面与接口的对应

| 页面 | 主要接口 |
|---|---|
| 客户档案页 | `GET/POST/PUT /api/customers`、`PUT /api/customers/{id}/tags` |
| 客户详情页 | `GET /api/customers/{id}`、`GET /api/customers/{id}/purchases` |
| 小程序首页（推荐位） | `GET /api/public/goods?featured=true` |
| 小程序全部库 | `GET /api/public/goods` |
| 小程序货品详情 | `GET /api/public/goods/{goodsNo}` |
| 预约表单 | `POST /api/public/reservations` |
| 查预约 | `GET /api/public/reservations?phoneTail=` |
| 预定按钮 | `GET /api/public/bookings/quota`、`POST /api/public/bookings` |
| 当日到店名单 | `GET /api/bookings/today`、`POST /api/bookings/{id}/contact`、`POST /api/bookings/{id}/result` |
