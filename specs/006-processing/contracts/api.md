# 接口契约：加工与上门

**风格**：REST，前缀 `/api`
通用约定见 `../README.md`。

## 两组接口

| 分组 | 前缀 | 谁用 |
|---|---|---|
| 师傅端 | `/api/craftsman/*` | 加工师傅（只看自己的） |
| 内部 | `/api/process-tasks`、`/api/delivery-applies`、`/api/visit-appts` | 老板（销售只读） |

## 师傅端：加工任务

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/craftsman/tasks` | 我的任务列表 |
| GET | `/api/craftsman/tasks/{id}` | 任务详情 |
| POST | `/api/craftsman/tasks/{id}/delivery` | 提交完工交付 |

### GET /api/craftsman/tasks

查询参数：`status`（可多选，逗号分隔）。

```json
{ "code": 0, "message": "ok", "data": {
  "total": 41,
  "items": [{
    "id": 12, "taskNo": "JG202600012",
    "goodsName": "和田玉籽料观音挂件",
    "requirement": "按图纸雕观音，保留皮色",
    "laborFee": 800.00, "deadline": "2026-11-05", "status": "在加工",
    "overdue": false, "overdueDays": 0, "urgeCount": 0, "lastUrgeAt": null
  }]
}}
```

`overdue` / `overdueDays` 是服务端算出来的派生态（`status ∈ {待加工, 在加工}` 且过了期限），管理端与师傅端都读这两个字段，前端不自己算。

**只返回 `craftsman_id = 当前登录用户关联的师傅` 的任务**。关联方式见下方「身份关联」。
无任务时 `total: 0`，前端显示「当前无加工任务」。

### POST /api/craftsman/tasks/{id}/delivery

```json
{
  "note": "按图完成，皮色保留完好",
  "images": [
    { "stage": "加工前", "path": "/upload/delivery/12-before.jpg" },
    { "stage": "加工中", "path": "/upload/delivery/12-mid.jpg" },
    { "stage": "完工", "path": "/upload/delivery/12-done.jpg" }
  ]
}
```

| 场景 | 返回 |
|---|---|
| 任务不属于本人 | 403 |
| 任务状态非「在加工」 | 422 |
| 已有待确认的交付申请 | 409 |
| 三个阶段图缺任一 | 400 |
| `stage` 不在三种内 | 400 |

**事务**：插 `t_delivery_apply`（待确认）、插三条 `t_delivery_image`、把任务状态改「待交付」。

**响应**

```json
{ "code": 0, "message": "ok", "data": {
  "applyNo": "JGJF202600012", "taskStatus": "待交付"
}}
```

## 内部：任务管理

| 方法 | 路径 | 用途 | 权限 |
|---|---|---|---|
| GET | `/api/process-tasks` | 全部任务，按师傅、状态、期限筛选；`overdueOnly=true` 只看逾期，返回里带 `overdueCount` | 老板、销售 |
| GET | `/api/process-tasks/{id}` | 任务详情（含交付申请） | 老板、销售 |
| POST | `/api/process-tasks/{id}/start` | 开始加工（待加工 → 在加工） | 老板 |
| POST | `/api/process-tasks/{id}/urge` | 催办一次，返回累计次数与时间 | 老板、销售 |

### POST /api/process-tasks/{id}/urge

无请求体。给逾期任务记一笔催办（`urge_count + 1`、`last_urge_at = now`），师傅端任务卡显示「门店已催办 N 次」。不推送消息，只留痕。

| 场景 | 返回 |
|---|---|
| 状态是「待交付」或「已交付」 | 409，不在催办口径内 |
| 还没到期限 | 422，「这件还没到期，不用催」 |
| 任务不存在 | 404 |

### POST /api/process-tasks/{id}/start

无请求体。状态从「待加工」改「在加工」。

| 场景 | 返回 |
|---|---|
| 状态非「待加工」 | 409 |

## 内部：确认完工

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/delivery-applies` | 待确认列表 |
| POST | `/api/delivery-applies/{id}/confirm` | 确认完工 |

### POST /api/delivery-applies/{id}/confirm

```json
{ "accepted": true, "opinion": "做工可以，安排入库" }
```

| 场景 | 返回 |
|---|---|
| 申请状态非「待确认」 | 409 |
| `accepted=false` 且无 `opinion` | 400 |

`accepted=true`：申请改「已确认」，任务改「已交付」。
`accepted=false`：申请保留「待确认」，任务退回「在加工」，`note` 记退回原因——师傅可重新提交。

**确认后不自动入库**：货品入库仍走 UC-02 的建档接口（FR-011），老板在入库页选择该任务作为来源。

## 上门预约

| 方法 | 路径 | 用途 | 权限 |
|---|---|---|---|
| POST | `/api/visit-appts` | 提交预约 | 供应商、加工师傅 |
| GET | `/api/visit-appts` | 预约列表 | 老板、销售（只读） |
| POST | `/api/visit-appts/{id}/confirm` | 确认 | 老板 |
| POST | `/api/visit-appts/{id}/cancel` | 取消 | 老板 |

### POST /api/visit-appts

```json
{
  "matter": "完工交付",
  "expectDate": "2026-10-25",
  "startTime": "14:30",
  "deliveryApplyId": 12
}
```

供应商提交时改用 `transferApplyId`，两者有且仅有一个。`expectDate` 是 `yyyy-MM-dd`，`startTime` 是 `HH:mm`，只能是整点或半点，最早 10:00、最晚 19:00；这一次交接占一小时，`end_time` 由服务端算，客户端传了也不算。

| 场景 | 返回 |
|---|---|
| 两个 id 都传或都不传 | 400 |
| 日期或时间格式不对 | 400 |
| 关联的出让申请状态非「已通过」 | 422 |
| 关联的交付申请状态非「已确认」 | 422 |
| 日期早于今天，或时段不在 10:00–19:00 | 422 |
| 时间不是整点/半点 | 422 |
| 该时段与已有待上门预约区间重叠 | 409，消息里带已占的起止 |
| 该区间内已有客户到店预约 | 409 |

**时段冲突判定**：同日「待上门」的预约按区间重叠判（`from < 已有.end && 已有.start < to`）；与客户到店预约同区间比对。整段完全相同的并发重复提交另有函数唯一索引 `uk_visit_slot_open` 兜底（1062 转 409）。

### POST /api/visit-appts/{id}/confirm

把 `status` 从「待上门」置为「已完成」——**确认即视为交接完成**。取消则置「已取消」，释放该时段。

## 身份关联：师傅账号怎么对上 craftsman_id

`t_user` 没有指向 `t_source_party` 的外键。三种做法：

| 做法 | 说明 | 代价 |
|---|---|---|
| **按姓名匹配** | `t_source_party.name` 与 `t_user.name` 相等 | 师傅改名就断 |
| **约定用户名** | 用户名等于师傅档案 id，如 `craftsman_5` | 不直观，但稳定 |
| **加关联字段** | 给 `t_user` 加 `party_id` | 改表结构 |

当前采用**约定用户名**：老板开通师傅账号时，用户名形如 `craftsman_5`，后段即 `t_source_party.id`。登录后从用户名解析出师傅 id。

这样不用改表，也不用担心改名。缺点是不直观，开通账号时要按规则来——已写进 `000-base` 的开通流程。

## 前端页面与接口的对应

| 页面 | 主要接口 |
|---|---|
| 师傅任务列表（小程序） | `GET /api/craftsman/tasks` |
| 任务详情与报完工（小程序） | `GET /api/craftsman/tasks/{id}`、`POST /api/craftsman/tasks/{id}/delivery` |
| 加工任务管理（老板端） | `GET /api/process-tasks`、`POST /api/process-tasks/{id}/start` |
| 待确认完工（老板端） | `GET /api/delivery-applies`、`POST /api/delivery-applies/{id}/confirm` |
| 上门预约管理（老板端） | `GET /api/visit-appts`、`POST /api/visit-appts/{id}/confirm` |
