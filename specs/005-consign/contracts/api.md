# 接口契约：寄售结算

**风格**：REST，前缀 `/api`
通用约定见 `../README.md`。全部接口仅老板可用（UC-21/22 的主执行者），销售只读合同列表。

## 合同与明细

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/consignments` | 合同列表，按供应商、状态筛选 |
| GET | `/api/consignments/{id}` | 合同详情，含明细与结清进度 |
| POST | `/api/consignments` | 登记合同（可同时录货品） |
| POST | `/api/consignments/{id}/items` | 向已有合同追加寄售货品 |
| POST | `/api/consignments/{id}/terminate` | 终止合同，未结算货品退回 |

### POST /api/consignments

一次提交合同头 + 货品清单，避免「先建合同再逐件录」的两次交互（UC-21 要求每件不超过 4 次操作）。

```json
{
  "supplierId": 5,
  "signTime": "2026-10-23 00:30:00",
  "expireDate": "2027-10-22",
  "settleCycle": "售出即结",
  "items": [
    { "name": "和田玉籽料手镯", "categoryId": 1, "form": "籽料",
      "weightG": 58.20, "size": "圈口56", "consignPrice": 12000.00 },
    { "name": "和田玉挂件", "categoryId": 2, "form": "山料",
      "weightG": 26.00, "size": null, "consignPrice": 3200.00 }
  ]
}
```

| 场景 | 返回 |
|---|---|
| `supplierId` 不存在或类型非「供应商」 | 422，提示先在基础数据建档 |
| `items` 为空 | 400 |
| `expireDate` 早于 `signTime` | 400 |
| 某件 `consignPrice ≤ 0` | 400 |

**事务**：逐件建档（`t_goods`，`src_type='寄售'`、`cost=consignPrice`）、插明细（`settle_status='未结算'`）、写库存流水、插合同头。全成或全回滚。

**响应**

```json
{ "code": 0, "message": "ok", "data": {
  "consignNo": "JS20260007", "itemCount": 2,
  "goods": [
    { "goodsNo": "JD2026102300680", "consignPrice": 12000.00 },
    { "goodsNo": "JD2026102300681", "consignPrice": 3200.00 }
  ]
}}
```

### GET /api/consignments/{id}

```json
{ "code": 0, "message": "ok", "data": {
  "consignNo": "JS20260001", "supplierName": "仪-玉王",
  "signTime": "2026-06-01 10:00:00", "expireDate": "2027-06-01",
  "status": "生效", "settleCycle": "售出即结",
  "itemCount": 12, "soldCount": 3, "settledCount": 2,
  "unsettledAmount": 9600.00,
  "settled": false,
  "items": [
    { "id": 1, "goodsNo": "JD...", "name": "和田玉手镯", "status": "已售",
      "consignPrice": 4800.00, "settleStatus": "未结算" }
  ]
}}
```

`soldCount` 是已售出件数，`settledCount` 是已结算件数，两者差就是待结算。`settled` 由「是否还有未结算明细」算出，不读合同状态字段。

## 结算

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/consignments/settleable` | 所有可结算明细（已售出未结算） |
| POST | `/api/consignments/settlements` | 批量结算 |

### GET /api/consignments/settleable

查询参数：`supplierId`（可选，按供应商筛）。

```json
{ "code": 0, "message": "ok", "data": {
  "total": 3, "amountSum": 9600.00,
  "items": [
    { "itemId": 1, "consignNo": "JS20260001", "supplierName": "仪-玉王",
      "goodsName": "和田玉手镯", "consignPrice": 4800.00,
      "saleTime": "2026-10-10 15:00:00" }
  ]
}}
```

### POST /api/consignments/settlements

```json
{ "itemIds": [1, 4, 7], "settleMethod": "转账", "settleTime": "2026-10-23 00:35:00" }
```

| 场景 | 返回 |
|---|---|
| `itemIds` 为空 | 400 |
| 任一明细 `settle_status='已结算'` | 409，整体回滚 |
| 任一明细对应货品非「已售」 | 422 |
| 明细分属不同供应商 | 允许，逐条按各自 `party_id` 生成结算单 |

**事务三步**：

1. 插 `t_settlement`（`settle_type='寄售'`，`consign_item_id`，`amount=consign_price`，`party_id=合同供应商`）
2. 改 `t_consignment_item.settle_status='已结算'`
3. 插 `t_payment`（`biz_type='寄售货款'`，`direction='支付'`，`related_type='台账结算'`）

第 3 步对应 UC-22 规则③。

**响应**

```json
{ "code": 0, "message": "ok", "data": {
  "settledCount": 3, "amountSum": 9600.00,
  "settlements": [
    { "settleNo": "JS202610230001", "itemId": 1, "amount": 4800.00 }
  ]
}}
```

## 终止合同

### POST /api/consignments/{id}/terminate

```json
{ "reason": "供应商要撤货" }
```

把合同状态改「已终止」，未结算明细对应的货品状态改「待处理」（退回供应商，不再展示）。

| 场景 | 返回 |
|---|---|
| 合同已终止或已到期 | 409 |
| 仍有未结算明细但货品已售出 | 409，提示先结算再终止 |

**已结算的明细不受影响**，货品状态不动。

## 前端页面与接口的对应

| 页面 | 主要接口 |
|---|---|
| 寄售合同列表 | `GET /api/consignments` |
| 合同详情 | `GET /api/consignments/{id}` |
| 登记合同 | `POST /api/consignments` |
| 追加货品 | `POST /api/consignments/{id}/items` |
| 结算页 | `GET /api/consignments/settleable`、`POST /api/consignments/settlements` |

## 与调货结算的共用部分

`t_settlement` 两个 feature 共用，查询时要按 `settle_type` 过滤：

- 寄售结算页：`WHERE settle_type='寄售'`
- 调货结算页：`WHERE settle_type='调货'`

结算历史页（如果做）不分类型，但要显示类型列。
