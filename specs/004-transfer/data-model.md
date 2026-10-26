# 数据模型：同行往来

**对应分析设计文档**：§4.2 数据层设计、§3.3 状态机（出让申请三状态）
**建表语句**：`docs/schema.sql`
**演示数据**：`docs/demo-business.sql` 已生成 272 条调货记录、41 个来源方

## 实体与关系

```
t_source_party 1 ──n t_transfer_apply   （供应商提交）
t_source_party 1 ──n t_transfer         （同行商户往来）
t_goods        1 ──n t_transfer         （一件货一条调货记录）
t_source_party 1 ──n t_settlement       （按商户结算）

t_transfer        1 ──0..1 t_settlement （结算类型=调货时关联）
t_consignment_item 1 ──0..1 t_settlement（结算类型=寄售时关联，见 005）
```

`t_settlement` 是调货与寄售共用的结算表，靠 `settle_type` 区分，`transfer_id` 与 `consign_item_id` 二选一非空。

## t_transfer_apply 出让申请

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| apply_no | VARCHAR(32) | NOT NULL, UNIQUE | 申请编号，前缀 `CR` |
| supplier_id | BIGINT UNSIGNED | NOT NULL, FK → t_source_party | 供应商，类别须为「供应商」 |
| out_type | VARCHAR(8) | NOT NULL | 玉料 / 成品 / 回流 |
| name | VARCHAR(128) | NOT NULL | 品名 |
| quantity | INT | NOT NULL | 数量 |
| expect_price | DECIMAL(12,2) | NOT NULL | 期望出让价，仅审核时可见 |
| description | VARCHAR(500) | NULL | 描述 |
| submit_time | DATETIME | NOT NULL | 提交时间 |
| status | VARCHAR(8) | NOT NULL, 默认「待审核」 | 待审核 / 已通过 / 已驳回 |
| audit_opinion | VARCHAR(255) | NULL | 审核意见，驳回时必填 |
| audit_time | DATETIME | NULL | 审核时间 |
| auditor_id | BIGINT UNSIGNED | NULL, FK → t_user | 审核人，须为老板 |

审核相关四个字段放在同一张表，不单独建审核记录表——一次申请只审一次，驳回后要重提就新建申请。

## t_transfer 调货记录

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| record_no | VARCHAR(32) | NOT NULL, UNIQUE | 记录编号，前缀 `DH` |
| direction | VARCHAR(8) | NOT NULL | 调入 / 调出 |
| merchant_id | BIGINT UNSIGNED | NOT NULL, FK → t_source_party | 同行商户，类别须为「同行商户」 |
| goods_id | BIGINT UNSIGNED | NOT NULL, FK → t_goods | 货品 |
| quantity | INT | NOT NULL, 默认 1 | 数量，当前恒为 1 |
| status | VARCHAR(8) | NOT NULL, 默认「在调」 | 在调 / 已售 / 已退回 |
| settled | VARCHAR(4) | NOT NULL, 默认「否」 | 否 / 是，结算后置是 |
| record_time | DATETIME | NOT NULL | 时间 |

**方向决定谁欠谁**：

| 方向 | 含义 | 结算时 |
|---|---|---|
| 调出 | 我的货放到同行处代销 | 同行售出后付我钱（我收） |
| 调入 | 同行的货放到我这里代销 | 我售出后付同行钱（我付） |

`t_settlement` 的金额恒为正，收付方向由关联的 `t_transfer.direction` 反推，不在结算表里冗余存。

## t_settlement 台账结算

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| settle_no | VARCHAR(32) | NOT NULL, UNIQUE | 结算编号，前缀 `JS` |
| party_id | BIGINT UNSIGNED | NOT NULL, FK → t_source_party | 往来方 |
| settle_type | VARCHAR(8) | NOT NULL | 调货 / 寄售 |
| transfer_id | BIGINT UNSIGNED | NULL, FK → t_transfer | 结算类型=调货时非空 |
| consign_item_id | BIGINT UNSIGNED | NULL, FK → t_consignment_item | 结算类型=寄售时非空 |
| amount | DECIMAL(12,2) | NOT NULL | 结算金额 |
| settle_method | VARCHAR(16) | NOT NULL | 现金 / 转账 / 冲抵 |
| settle_time | DATETIME | NOT NULL | 结算时间 |
| operator_id | BIGINT UNSIGNED | NOT NULL, FK → t_user | 经手人 |

**两个外键可空的约束**：`CHECK` 在 MySQL 8 里生效有限，改在服务层校验「`settle_type=调货` 时 `transfer_id` 非空且 `consign_item_id` 为空，反之亦然」。

## 结算金额从哪来

UC-23 说「每条结算金额取对应调货记录金额」，但 `t_transfer` 没有金额字段。只有两条路：

```sql
-- 优先调货价，为空时回退最低售价
SELECT COALESCE(g.transfer_price, g.min_price) AS amount
FROM t_goods g WHERE g.id = ?;
```

**为什么不能只用 min_price**：最低售价是零售底价，调货价通常更低。用零售价结算，同行不肯接。

**风险**：货品改价后，历史结算会对不上。调货价改了，已结算的单据金额无法追溯原值。这是当前表结构的硬伤，见下方差异。

## 调货状态与货品状态的联动

| 调货记录状态 | 货品状态 | 说明 |
|---|---|---|
| 在调（调出） | 已调出 | 货在同行处，不在我店 |
| 在调（调入） | 在柜 | 货在我店，可卖 |
| 已售 | 已售 | 卖掉了，等结算 |
| 已退回 | 在柜 | 退回来，重新可卖 |

状态变更要同事务改两张表，并写一条 `t_stock_log`。

## 鉴赏期时点

UC-20 规则②：「以客户鉴赏期（3 天或 7 天）届满为结账时点」。

当前按 `record_time + 7 天` 算应结账日，3 天与 7 天无法区分——`t_transfer` 没有 `grace_days` 字段。

结算清单里可以标出「已过鉴赏期」与「鉴赏期内」，用于老板判断该不该结：

```sql
SELECT *, DATEDIFF(CURDATE(), DATE_ADD(record_time, INTERVAL 7 DAY)) AS overdue_days
FROM t_transfer WHERE settled = '否' AND merchant_id = ?;
```

## 演示数据现状

`demo-business.sql` 已生成：

| 表 | 条数 | 分布 |
|---|---|---|
| t_source_party | 41 | 同行商户 14、供应商 22、加工师傅 5 |
| t_transfer | 272 | 状态 在调/已售/已退回 约 5:4:1，已结算约 1/4 |
| t_transfer_apply | 0 | **未生成**，见 tasks T001 |

出让申请没有数据——它是「供应商想卖货给我」的前置单据，与现有 679 件货品没有必然关系，需要单独造 10–15 条覆盖三种状态。

## 与文档的差异

**一、调货金额未固化（同上）**

`t_transfer` 没有 `amount` 字段，结算金额实时从货品取。货品调价后历史结算金额会失真。建议后续加 `amount DECIMAL(12,2)`，在调货登记时固化。

**二、鉴赏期天数未记录**

`t_transfer` 没有 `grace_days`，3 天与 7 天无法区分。当前统一按 7 天算。
