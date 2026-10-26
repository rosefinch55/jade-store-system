# 接口契约：货品档案

**风格**：REST，路径用名词，动作交给 HTTP 方法
**前缀**：`/api`
**框架**：SpringBoot 3，`@RestController`（契约为框架中立，换实现不用改这份）

## 统一约定

**响应包装**

```json
{ "code": 0, "message": "ok", "data": {} }
```

**错误码**

| HTTP | code | 场景 |
|---|---|---|
| 400 | 400 | 参数缺失或格式错 |
| 404 | 404 | 资源不存在 |
| 409 | 409 | 状态冲突，如修改已售货品 |
| 422 | 422 | 业务规则不满足，如调货未填来源商户 |
| 500 | 500 | 服务端异常 |

**分页**：请求带 `page`（从 1 起）、`size`（默认 20，上限 100），响应 `data = { total, page, size, items: [] }`

**时间格式**：`yyyy-MM-dd HH:mm:ss`，时区固定 `Asia/Shanghai`

## 接口清单

| 方法 | 路径 | 用途 | 对应需求 |
|---|---|---|---|
| GET | `/api/goods` | 货品列表，支持筛选与分页 | FR-011 |
| GET | `/api/goods/{id}` | 单件详情 | — |
| POST | `/api/goods` | 单件建档 | FR-001、FR-003~006 |
| PUT | `/api/goods/{id}` | 修改基础信息 | FR-010 |
| GET | `/api/goods/{id}/logs` | 状态变更流水 | FR-009 |
| POST | `/api/stock-ins` | 一次入库多件，创建批次 | FR-002 |
| GET | `/api/stock-ins/{id}` | 批次详情与货品清单 | — |
| GET | `/api/categories` | 类别列表 | — |
| POST | `/api/categories` | 新增类别 | — |
| PUT | `/api/categories/{id}` | 修改类别 | — |
| DELETE | `/api/categories/{id}` | 删除类别，有货品引用时拒绝 | 删除策略 |

**不提供的接口**：`PATCH /api/goods/{id}/status`。状态只能由成交、送检、调货等业务动作驱动（FR-008），开放通用状态接口会让状态机失效。

## GET /api/goods

**查询参数**

| 参数 | 类型 | 说明 |
|---|---|---|
| status | string | 八状态之一，为空则不限 |
| categoryId | long | 类别 |
| keyword | string | 匹配货品编号或品名 |
| page / size | int | 分页 |

**响应示例**

```json
{ "code": 0, "message": "ok", "data": {
  "total": 128, "page": 1, "size": 20,
  "items": [{
    "id": 1001, "goodsNo": "JD202610210001", "name": "和田玉手镯",
    "categoryId": 3, "categoryName": "和田玉",
    "weightG": 56.80, "status": "在柜",
    "minPrice": 12800.00, "inTime": "2026-10-21 19:30:00"
  }]
}}
```

## POST /api/goods

**请求**

```json
{
  "name": "和田玉手镯", "categoryId": 3,
  "material": "新疆和田玉", "origin": "和田", "shape": "手镯",
  "form": "籽料", "weightG": 56.80, "size": "内径56mm",
  "certNo": "XJ2026-10231", "image": "/upload/goods/1001.jpg",
  "srcType": "采购", "srcParty": "新疆料商张师傅", "srcDoc": null,
  "cost": 8000.00, "minPrice": 12800.00,
  "receiverId": 2
}
```

**规则校验**

| 场景 | 返回 |
|---|---|
| `srcType` 不在五种取值内 | 400 |
| `srcType = 调货` 且 `cost` 有值 | 422，提示调货不记成本 |
| `srcType = 回收 / 加工完工 / 寄售` 且 `cost` 为空 | 422 |
| `srcType = 寄售` | `cost` 取寄售价，同时写入结算依据 |
| `minPrice ≤ 0` | 400 |
| `weightG ≤ 0` | 400 |

**响应**

```json
{ "code": 0, "message": "ok", "data": {
  "id": 1001, "goodsNo": "JD202610210001", "status": "在柜",
  "inTime": "2026-10-21 19:30:00", "stockInId": 88
}}
```

编号规则：`JD` + `yyyyMMdd` + 6 位流水，同日递增，全局唯一。

## POST /api/stock-ins

一次入库多件，共用一个批次。单件入库也走这个接口，`items` 长度为 1。

**请求**

```json
{
  "receiverId": 2, "remark": "10月21日同行调货一批",
  "items": [
    { "name": "翡翠平安扣", "categoryId": 1, "form": "山料", "weightG": 12.30,
      "srcType": "调货", "srcParty": "同和祥", "minPrice": 2600.00 },
    { "name": "翡翠平安扣", "categoryId": 1, "form": "山料", "weightG": 12.10,
      "srcType": "调货", "srcParty": "同和祥", "minPrice": 2600.00 }
  ]
}
```

**响应**

```json
{ "code": 0, "message": "ok", "data": {
  "stockInId": 88, "inTime": "2026-10-21 19:30:00",
  "goodsCount": 2,
  "goods": [
    { "id": 1002, "goodsNo": "JD202610210002" },
    { "id": 1003, "goodsNo": "JD202610210003" }
  ]
}}
```

**事务**：整批要么全成功，要么全回滚。任一条校验失败返回 422，已写入的档案撤销。

## PUT /api/goods/{id}

**允许修改**：`name`、`categoryId`、`material`、`origin`、`shape`、`form`、`weightG`、`size`、`certNo`、`image`、`minPrice`、`transferPrice`、`srcParty`、`srcDoc`

**不可修改**：`goodsNo`、`srcType`、`cost`、`inTime`、`stockInId`、`status`

**规则**

- 状态为「已售」时拒绝，返回 409
- 请求体里出现不可修改字段，返回 400

## GET /api/goods/{id}/logs

**响应**

```json
{ "code": 0, "message": "ok", "data": [
  { "fromStatus": null, "toStatus": "在柜", "refType": "入库批次", "refNo": "88",
    "operatorName": "张三", "logTime": "2026-10-21 19:30:00" },
  { "fromStatus": "在柜", "toStatus": "已售", "refType": "成交", "refNo": "CJ202610210017",
    "operatorName": "李四", "logTime": "2026-11-02 15:12:00" }
]}
```

按 `logTime` 正序，第一条的 `fromStatus` 为 `null`。

## 类别接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/categories` | 返回全部类别，按名称排序 |
| POST | `/api/categories` | body: `{ "name": "...", "remark": "..." }`，名称唯一 |
| PUT | `/api/categories/{id}` | 改名或备注 |
| DELETE | `/api/categories/{id}` | 有货品引用时返回 409，提示先转移货品 |

## 鉴权

- 建档、改档、新增类别：仅「老板」角色
- 列表、详情、流水：老板与销售均可
- 未登录返回 401，无权限返回 403

权限点沿用报告 §4.4 权限矩阵：`goods:create`、`goods:update`、`goods:read`、`category:write`
