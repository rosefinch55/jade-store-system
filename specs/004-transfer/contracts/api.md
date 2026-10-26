# 接口契约：同行往来

**风格**：REST，前缀 `/api`
通用约定见 `../README.md`。

## 三组角色，三组接口

| 分组 | 前缀 | 谁用 | 权限点 |
|---|---|---|---|
| 商户价目 | `/api/merchant/*` | 合作商户 | UC-11 |
| 供应商申请 | `/api/supplier/*` | 供应商 | UC-14 |
| 内部管理 | `/api/transfer-applies`、`/api/transfers`、`/api/settlements` | 老板（销售只读） | UC-18/20/23 |

## 商户：调货价目

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/merchant/price-list` | 在柜货品的调货价目 |

### GET /api/merchant/price-list

查询参数：`categoryId`、`keyword`、`page`、`size`。

```json
{ "code": 0, "message": "ok", "data": {
  "total": 272, "page": 1, "size": 20,
  "items": [{
    "goodsNo": "JD20261022000005", "name": "和田玉碧玉手镯",
    "categoryName": "手镯", "shape": "手镯", "size": "60-13-10mm",
    "weightG": 64.5, "transferPrice": 2600.00,
    "image": "/images/goods/img_xxx.jpg"
  }]
}}
```

**只返回在柜货品，且 `transfer_price` 不为空**——调货价没维护的货品不进价目（FR-005）。

**权限**：仅 `MERCHANT` 角色，其他角色返回 403。

## 供应商：出让申请

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/api/supplier/applies` | 提交申请 |
| GET | `/api/supplier/applies` | 我的申请列表 |

### POST /api/supplier/applies

```json
{
  "outType": "成品", "name": "和田玉籽料手镯 56 圈",
  "quantity": 2, "expectPrice": 8000.00,
  "description": "一料两镯，皮色好，无裂", "images": ["/upload/apply/1.jpg"]
}
```

| 场景 | 返回 |
|---|---|
| 缺 `expectPrice` 或 `quantity ≤ 0` | 400，`message` 指明缺哪项 |
| `outType` 不在三种内 | 400 |
| 供应商档案不存在或已停用 | 403 |

**响应**

```json
{ "code": 0, "message": "ok", "data": {
  "applyNo": "CR202610220001", "status": "待审核", "submitTime": "2026-10-23 00:10:00"
}}
```

### GET /api/supplier/applies

只返回当前登录供应商自己的申请，带审核结果。**不返回 `expectPrice` 给非本人**（不适用此接口，仅供内部审核用）。

## 内部：审核

| 方法 | 路径 | 用途 | 权限 |
|---|---|---|---|
| GET | `/api/transfer-applies` | 申请列表，可按状态筛选 | 老板、销售（只读） |
| GET | `/api/transfer-applies/{id}` | 详情（含期望出让价） | 老板 |
| POST | `/api/transfer-applies/{id}/audit` | 通过或驳回 | 仅老板 |

### GET /api/transfer-applies?status=待审核

按 `submit_time` 升序（FR-012，先进先审）。

### POST /api/transfer-applies/{id}/audit

```json
{ "pass": true, "opinion": "成色不错，按 7500 收" }
```

```json
{ "pass": false, "opinion": "料子有裂，暂不收" }
```

| 场景 | 返回 |
|---|---|
| `pass=false` 且 `opinion` 为空 | 400 |
| 申请状态非「待审核」 | 409 |
| 操作者非老板 | 403 |
| 操作者 = 提交该申请的供应商本人 | 403 |

**响应**：返回审核后的状态、审核时间、审核人。

## 内部：调货记录

| 方法 | 路径 | 用途 | 权限 |
|---|---|---|---|
| GET | `/api/transfers` | 列表，按商户、方向、状态筛选 | 老板、销售（只读） |
| POST | `/api/transfers` | 登记一条调货 | 仅老板 |
| PUT | `/api/transfers/{id}/status` | 改状态（已售/已退回） | 仅老板 |

### POST /api/transfers

```json
{ "direction": "调出", "merchantId": 3, "goodsNo": "JD20261022000005", "recordTime": "2026-10-23 00:10:00" }
```

| 场景 | 返回 |
|---|---|
| `merchantId` 的 `party_type` 不是「同行商户」 | 400 |
| 货品已有未结算的调货记录 | 409 |
| 货品非在柜（调出时） | 409 |

**事务**：插调货记录、改货品状态为「已调出」、写流水。

### PUT /api/transfers/{id}/status

```json
{ "status": "已售" }
```

- `已售`：货品状态改「已售」，等待结算
- `已退回`：货品状态改「在柜」，可再次出售

**不允许改成「在调」**——那是新建时的初始状态，回退没有业务含义。

## 内部：台账结算

| 方法 | 路径 | 用途 | 权限 |
|---|---|---|---|
| GET | `/api/settlements/pending?merchantId=3` | 该商户未结算清单 | 老板 |
| POST | `/api/settlements/batch` | 批量结算 | 老板 |
| GET | `/api/settlements` | 结算历史 | 老板 |

### GET /api/settlements/pending

```json
{ "code": 0, "message": "ok", "data": {
  "merchantId": 3, "merchantName": "琢玉阁珠宝玉器",
  "total": 3, "amountSum": 8600.00,
  "items": [
    { "transferId": 12, "recordNo": "DH202600012", "goodsName": "和田玉碧玉手镯",
      "direction": "调出", "amount": 2600.00, "status": "已售",
      "recordTime": "2026-09-20 10:00:00", "overdueDays": 26 }
  ]
}}
```

`overdueDays` 是鉴赏期（按 7 天）届满后的天数，负数表示还在鉴赏期内。

无未结算记录时返回 `total: 0` 与提示（FR-027）。

### POST /api/settlements/batch

```json
{ "merchantId": 3, "transferIds": [12, 15, 18], "settleMethod": "转账", "settleTime": "2026-10-23 00:15:00" }
```

| 场景 | 返回 |
|---|---|
| `transferIds` 为空 | 400 |
| 任一记录不属于该商户 | 400 |
| 任一记录已结算 | 409，整体回滚 |
| 任一记录状态为「在调」 | 422，货还没卖/没退，不该结算 |

**事务**：逐条插 `t_settlement`（`settle_type=调货`，`transfer_id` 对应）、把所选 `t_transfer.settled` 置「是」，全成或全回滚。

**响应**

```json
{ "code": 0, "message": "ok", "data": {
  "settledCount": 3, "amountSum": 8600.00,
  "settlements": [
    { "settleNo": "JS202610220001", "transferId": 12, "amount": 2600.00 }
  ]
}}
```

## 前端页面与接口的对应

| 页面 | 主要接口 |
|---|---|
| 商户调货价目（小程序） | `GET /api/merchant/price-list` |
| 供应商出让申请（小程序） | `POST /api/supplier/applies`、`GET /api/supplier/applies` |
| 待审核申请（老板端） | `GET /api/transfer-applies?status=待审核`、`POST /api/transfer-applies/{id}/audit` |
| 调货记录（老板端） | `GET/POST /api/transfers`、`PUT /api/transfers/{id}/status` |
| 台账结算（老板端） | `GET /api/settlements/pending`、`POST /api/settlements/batch` |

## 权限小结

| 接口 | 老板 | 销售 | 合作商户 | 供应商 | 加工师傅 |
|---|---|---|---|---|---|
| 价目查询 | — | — | ✅ | — | — |
| 提交申请 | — | — | — | ✅ | — |
| 审核 | ✅ | 只读 | — | — | — |
| 调货记录 | ✅ | 只读 | — | — | — |
| 台账结算 | ✅ | — | — | — | — |

与报告 §4.4 权限矩阵一致。
