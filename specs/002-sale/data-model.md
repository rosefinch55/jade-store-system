# 数据模型：成交与账务

**对应分析设计文档**：§4.2 数据层设计、§3.3 状态机（货品、分期计划）
**建表语句**：`docs/schema.sql`

## 实体与关系

```
t_goods 1 ──── 1 t_sale        （一件货品同时只有一条有效成交）
t_sale  1 ──── 1 t_installment （一份成交至多一份分期计划）
t_sale  1 ──── 0..1 t_return   （一次成交最多退货一次）
t_payment                      （独立，按单据编号逻辑引用，不建硬外键）
```

## t_sale 成交记录

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| sale_no | VARCHAR(32) | NOT NULL, UNIQUE | 成交编号 |
| goods_id | BIGINT UNSIGNED | NOT NULL, FK → t_goods | 货品 |
| sale_time | DATETIME | NOT NULL | 成交发生时间（系统时间） |
| price | DECIMAL(12,2) | NOT NULL | 成交价，不得低于货品最低售价 |
| pay_method | VARCHAR(8) | NOT NULL | 全款 / 定金 |
| received | DECIMAL(12,2) | NOT NULL | 已收金额，定金成交时为定金额 |
| customer_id | BIGINT UNSIGNED | NULL, FK → t_customer | 散客可空 |
| operator_id | BIGINT UNSIGNED | NOT NULL, FK → t_user | 经手销售 |
| recorded_at | DATETIME | NOT NULL | 录入时间，与发生时间分别记录 |

**为什么分 `sale_time` 与 `recorded_at`**：UC-01 业务规则⑥明确要求两者分别记录。柜台可能事后补录，统计必须按发生时间归属月份，不能用录入时间。

## t_return 退货记录

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| return_time | DATETIME | NOT NULL | 退货时间 |
| sale_id | BIGINT UNSIGNED | NOT NULL, FK → t_sale | 原成交 |
| reason | VARCHAR(16) | NOT NULL | 真假存疑 / 质量问题 |
| refund | DECIMAL(12,2) | NOT NULL | 退款金额 |
| operator_id | BIGINT UNSIGNED | NOT NULL, FK → t_user | 经手人 |

7 天期限与原因范围在服务层校验，表上不设约束——期限会随业务规则变化，写死在库里改不动。

## t_installment 分期计划

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| plan_no | VARCHAR(32) | NOT NULL, UNIQUE | 计划编号 |
| sale_id | BIGINT UNSIGNED | NOT NULL, FK → t_sale, UNIQUE | 一份成交一份计划 |
| periods | INT | NOT NULL | 分期期数 |
| period_amount | DECIMAL(12,2) | NOT NULL | 每期应还 |
| paid_amount | DECIMAL(12,2) | NOT NULL, 默认 0 | 已还金额 |
| next_due_date | DATE | NOT NULL | 下一期到期日 |
| start_date | DATE | NOT NULL | 计划生效日 |
| status | VARCHAR(8) | NOT NULL, 默认「未还」 | 未还 / 逾期 / 已结清 |
| created_at / updated_at | DATETIME | NOT NULL | 审计字段 |

**`sale_id` 上加 UNIQUE**：直接把「一份成交至多一份计划」（FR-013）落到库上，服务层再检查一次，双重保险。

**「逾期」不落库**：UC-25 设计要求「查询时按到期日与未还余额实时判定」。`status` 只在还款动作发生时写「未还」或「已结清」，逾期由查询时计算 `next_due_date < CURDATE() AND paid_amount < 总额` 得出。这样避免每天跑批改状态。

## t_payment 货款记录

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| record_no | VARCHAR(32) | NOT NULL, UNIQUE | 记录编号 |
| biz_type | VARCHAR(16) | NOT NULL | 采购货款 / 调货货款 / 加工工费 / 寄售货款 |
| related_type | VARCHAR(32) | NOT NULL | 关联单据类型：入库批次 / 调货记录 / 加工任务 / 台账结算 |
| related_no | VARCHAR(32) | NOT NULL | 关联单据编号，逻辑引用 |
| amount | DECIMAL(12,2) | NOT NULL | 金额 |
| direction | VARCHAR(8) | NOT NULL | 支付 / 收取 |
| operator_id | BIGINT UNSIGNED | NOT NULL, FK → t_user | 经手人 |
| pay_time | DATETIME | NOT NULL | 时间 |

**为什么用 `related_type + related_no` 而不是外键**：一条货款可能指向入库批次、调货记录、加工任务或台账结算，四张不同的表，无法用单一外键。报告 §4.2 已说明这是逻辑引用，与 `t_stock_log` 同口径。

## 状态联动

成交与退货会改动货品状态并写流水，这是本 feature 与 `001-goods` 的接缝。

| 动作 | t_goods.status | t_stock_log 新增一条 |
|---|---|---|
| 全款成交 | 在柜 → **已售** | from=在柜, to=已售, ref_type=成交, ref_no=sale_no |
| 定金成交 | 在柜 → **已订** | from=在柜, to=已订, ref_type=成交, ref_no=sale_no |
| 退货 | 已售/已订 → **在柜** | from=已售, to=在柜, ref_type=退货, ref_no=sale_no |

三处变更必须在同一事务里：改状态、写流水、插成交/退货记录。任一步失败全部回滚。

## 统计的 SQL 口径

UC-06 的三个指标对应三条聚合，写清楚免得实现时各算各的。

**月度销售额**（退货冲减）

```sql
SELECT DATE_FORMAT(s.sale_time,'%Y-%m') AS ym,
       SUM(s.price) - COALESCE(SUM(r.refund),0) AS amount
FROM t_sale s LEFT JOIN t_return r ON r.sale_id = s.id
WHERE s.sale_time >= ? AND s.sale_time < ?
GROUP BY ym;
```

**销售客户数**（去重，散客不计）

```sql
SELECT COUNT(DISTINCT customer_id) FROM t_sale
WHERE customer_id IS NOT NULL AND sale_time >= ? AND sale_time < ?;
```

**加权周转周期**（从入库到售出并回款，按货品金额加权）

```sql
SELECT SUM(DATEDIFF(s.sale_time, g.in_time) * g.min_price) / SUM(g.min_price) AS weighted_days
FROM t_sale s JOIN t_goods g ON g.id = s.goods_id
WHERE s.sale_time >= ? AND s.sale_time < ?;
```

加权用 `min_price` 而非 `cost`：调货调入的货品 `cost` 为空，用成本加权会把这类货品整体漏掉。

## 本 feature 的校验落点

| 规则 | 依据 | 实现位置 |
|---|---|---|
| 货品须「在柜」 | FR-001 | 服务层查状态，同时靠 `t_sale.goods_id` 唯一性兜并发 |
| 成交价不低于最低售价 | FR-003 | 服务层比对 `t_goods.min_price` |
| 定金不超成交价 | FR-004 | 服务层比对 |
| 7 天退货期 | FR-009 | 服务层算 `DATEDIFF(NOW(), sale_time) <= 7` |
| 一份成交一份计划 | FR-013 | `t_installment.sale_id` UNIQUE + 服务层预检 |
| 还款不超余额 | FR-015 | 服务层比对 `总额 - paid_amount` |
| 货款金额与单据一致 | FR-022 | 服务层按 `related_type` 反查单据金额 |

## 演示数据现状

`docs/demo-goods.sql` 里 679 件货品的状态全部是「在柜」，`t_sale` 为空。要做成交演示，需要先造几笔成交——这件事放在 `tasks.md` 的 T001。
