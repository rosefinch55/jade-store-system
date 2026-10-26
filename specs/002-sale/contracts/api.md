# 接口契约：成交与账务

**风格**：REST，前缀 `/api`
通用约定（响应包装、分页、时间格式、错误码）见 `../README.md`。
**鉴权**：成交、退货、分期、还款为销售与老板可用；货款记录、统计仅老板。

## 本 feature 的错误码用法

| HTTP | 用在什么情况 |
|---|---|
| 409 | 货品状态不可售、重复退货、重复分期、已结清再还、二次退货 |
| 422 | 底价未授权、超 7 天退货、原因不在可退范围、货款与单据不符 |
| 400 | 定金超成交价、还款超余额、参数格式错 |

## 成交

| 方法 | 路径 | 用途 | 权限 |
|---|---|---|---|
| POST | `/api/sales` | 登记成交 | 销售、老板 |
| GET | `/api/sales` | 成交列表，按时间、客户、货品筛选 | 销售、老板 |
| GET | `/api/sales/{id}` | 成交详情（含退货、分期状态） | 销售、老板 |
| POST | `/api/sales/{id}/returns` | 登记退货 | 销售、老板 |

### POST /api/sales

**请求**

```json
{
  "goodsNo": "JD20261022000001",
  "price": 12800.00,
  "payMethod": "全款",
  "received": 12800.00,
  "customerId": 37,
  "authorizedBy": null,
  "authNote": null
}
```

定金成交时将 `payMethod` 改为 `"定金"`，`received` 填定金额；若成交价低于最低售价，必须带 `authorizedBy`（老板用户 id）与 `authNote`。

**校验顺序**

1. 货品存在且状态为「在柜」→ 否则 409
2. `price < t_goods.min_price` 且未带 `authorizedBy` → 422
3. `payMethod = 定金` 且 `received > price` → 400
4. 该货品已有未退货的成交 → 409

**响应**

```json
{ "code": 0, "message": "ok", "data": {
  "id": 501, "saleNo": "CJ2026102200001",
  "goodsNo": "JD20261022000001", "goodsName": "和田玉白玉挂件",
  "price": 12800.00, "payMethod": "全款", "received": 12800.00,
  "goodsStatus": "已售", "saleTime": "2026-10-22 23:40:00"
}}
```

**事务**：插 `t_sale`、改 `t_goods.status`、写 `t_stock_log`，三步同一事务。

### POST /api/sales/{id}/returns

**请求**

```json
{ "reason": "质量问题", "refund": 12800.00, "note": "玉面有裂，客户要求退" }
```

**校验**

| 场景 | 返回 |
|---|---|
| 成交不存在或已退货 | 409 |
| `DATEDIFF(NOW(), sale_time) > 7` | 422 |
| `reason` 不在「真假存疑」「质量问题」内 | 422 |
| `refund` 与原成交价不一致且无说明 | 422 |

**响应**

```json
{ "code": 0, "message": "ok", "data": {
  "returnId": 88, "saleNo": "CJ2026102200001",
  "goodsStatus": "在柜", "refund": 12800.00, "refundedAt": "2026-10-22 23:45:00"
}}
```

**连带效果**：货品状态回「在柜」、写流水、当月销售额冲减该笔。

## 分期

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/api/sales/{id}/installment` | 为该成交建分期计划 |
| GET | `/api/installments` | 计划列表，可按状态、逾期筛选 |
| GET | `/api/installments/{id}` | 计划详情与还款记录 |
| POST | `/api/installments/{id}/payments` | 记录一次还款 |

### POST /api/sales/{id}/installment

```json
{ "periods": 6, "firstDueDate": "2026-11-22" }
```

系统按 `成交价 / periods` 算每期应还（四舍五入到分，末期吸收尾差），生成计划。

| 场景 | 返回 |
|---|---|
| 该成交已有计划 | 409 |
| `periods < 1` | 400 |

**响应**

```json
{ "code": 0, "message": "ok", "data": {
  "planNo": "FQ202610220001", "saleNo": "CJ2026102200001",
  "periods": 6, "periodAmount": 2133.33,
  "paidAmount": 0, "nextDueDate": "2026-11-22", "status": "未还"
}}
```

### GET /api/installments

查询参数：`status`（未还/逾期/已结清）、`overdueOnly`（true 时只看逾期）、`page`、`size`。

**逾期判定**：`next_due_date < CURDATE() AND paid_amount < 总额`，不读 `status` 字段。

响应中每条带计算后的 `displayStatus`（可能为「逾期」），与库里的 `status` 区分开：

```json
{ "planNo": "FQ202610220001", "status": "未还", "displayStatus": "逾期",
  "overdueDays": 12, "totalAmount": 12800.00, "paidAmount": 4266.66,
  "remaining": 8533.34, "nextDueDate": "2026-11-22" }
```

### POST /api/installments/{id}/payments

```json
{ "amount": 2133.33, "payTime": "2026-10-22 23:50:00" }
```

| 场景 | 返回 |
|---|---|
| `amount > 未还余额` | 400 |
| 计划已结清 | 409 |

**响应**：返回剩余应还、下一期到期日、是否结清。

## 货款记录

| 方法 | 路径 | 权限 |
|---|---|---|
| POST | `/api/payments` | 老板 |
| GET | `/api/payments` | 老板 |

### POST /api/payments

```json
{
  "bizType": "采购货款",
  "relatedType": "入库批次",
  "relatedNo": "1",
  "amount": 8000.00,
  "direction": "支付",
  "payTime": "2026-10-22 23:50:00"
}
```

| 场景 | 返回 |
|---|---|
| `bizType` 不在四种内 | 400 |
| `relatedType` 不在四种内 | 400 |
| 单据不存在 | 404 |
| 金额与单据金额不一致 | 422 |

**不提供 PUT**：UC-19 业务规则③要求「只增不改，错误作废重录」。作废走 `POST /api/payments/{id}/void`（见下方差异说明）。

### 作废接口（与文档不一致，需确认）

当前 `t_payment` 没有 `voided` 字段。两个做法：

- **临时**：`POST /api/payments/{id}/void` 把 `remark` 写成 `VOID:原因`，统计时排除含该前缀的记录
- **正式**：给表加 `voided TINYINT(1)`、`void_at`、`void_by`，走一次 schema 变更

先按临时方案实现，接口形态不变，后面换实现不影响前端。

## 统计

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/stats/sales` | 销售额、客户数、渠道分布、核心客户 |
| GET | `/api/stats/turnover` | 加权周转周期、最慢货品清单 |

### GET /api/stats/sales

查询参数：`from`、`to`（`yyyy-MM-dd`），或 `period=month|quarter|year` 二选一。

```json
{ "code": 0, "message": "ok", "data": {
  "range": { "from": "2026-10-01", "to": "2026-10-31" },
  "amount": 186400.00,
  "returnAmount": 12800.00,
  "netAmount": 173600.00,
  "customerCount": 27,
  "saleCount": 41,
  "byChannel": [
    { "channel": "到店", "amount": 120000.00, "count": 25 },
    { "channel": "老客介绍", "amount": 66400.00, "count": 16 }
  ],
  "topCustomers": [
    { "customerId": 37, "name": "王某", "amount": 38600.00, "ratio": 0.21 }
  ]
}}
```

`netAmount = amount - returnAmount`，即 FR-025 的「退货冲减」。

### GET /api/stats/turnover

```json
{ "code": 0, "message": "ok", "data": {
  "range": { "from": "2026-10-01", "to": "2026-10-31" },
  "weightedDays": 186.4,
  "sampleCount": 41,
  "slowest": [
    { "goodsNo": "JD20261022000002", "name": "和田玉挂件", "days": 412, "minPrice": 3200.00 }
  ]
}}
```

`weightedDays` 按 `min_price` 加权，不用 `cost`——调货调入的货品成本为空，用成本加权会把这类整体漏掉。

## 前端页面与接口的对应

| 页面 | 主要接口 |
|---|---|
| 成交登记页 | `GET /api/goods/{id}`（取货品信息与底价）、`POST /api/sales` |
| 退货页 | `GET /api/sales/{id}`、`POST /api/sales/{id}/returns` |
| 分期管理页 | `GET /api/installments`、`POST /api/installments/{id}/payments` |
| 货款台账页 | `GET /api/payments`、`POST /api/payments` |
| 统计页 | `GET /api/stats/sales`、`GET /api/stats/turnover` |
