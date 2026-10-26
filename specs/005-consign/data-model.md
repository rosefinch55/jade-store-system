# 数据模型：寄售结算

**对应分析设计文档**：§4.2 数据层设计、§3.3 状态机（寄售合同三状态）
**建表语句**：`docs/schema.sql`
**演示数据**：`docs/demo-business.sql` 已生成 6 份合同、68 条明细

## 实体与关系

```
t_source_party   1 ──n t_consignment      （供应商）
t_consignment    1 ──n t_consignment_item （一份合同多件货品）
t_goods          1 ──1 t_consignment_item （一件货一条未结算明细）
t_consignment_item 1 ──0..1 t_settlement  （结算类型=寄售）
```

## t_consignment 寄售合同

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| consign_no | VARCHAR(32) | NOT NULL, UNIQUE | 合同编号，前缀 `JS` |
| supplier_id | BIGINT UNSIGNED | NOT NULL, FK → t_source_party | 供应商，类别须为「供应商」 |
| sign_time | DATETIME | NOT NULL | 签订时间 |
| expire_date | DATE | NOT NULL | 到期日，到期未终止即置「已到期」 |
| settle_cycle | VARCHAR(16) | NOT NULL, 默认「售出即结」 | 售出即结 / 按月结算 |
| status | VARCHAR(8) | NOT NULL, 默认「生效」 | 生效 / 已终止 / 已到期 |
| remark | VARCHAR(255) | NULL | 备注 |

**只有三个状态，没有「已结清」**——理由见下。

## t_consignment_item 寄售明细

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| consignment_id | BIGINT UNSIGNED | NOT NULL, FK → t_consignment | 合同 |
| goods_id | BIGINT UNSIGNED | NOT NULL, FK → t_goods | 货品，`src_type` 应为「寄售」 |
| consign_price | DECIMAL(12,2) | NOT NULL | 寄售价，售出后按此与供应商结算 |
| settle_status | VARCHAR(8) | NOT NULL, 默认「未结算」 | 未结算 / 已结算 |

**`consign_price` 与 `t_goods.cost` 的关系**：UC-21 规则②要求寄售货品的成本记为寄售价，所以两个字段值相同。冗余存两份是为了：

- `t_goods.cost` 用于周转统计（成本口径）
- `t_consignment_item.consign_price` 用于结算，且改价时不影响历史成本

登记时由服务层保证两者一致，后续不联动。

## 为什么合同没有「已结清」状态

UC-22 基本路径第 5 步写「明细全部结清置已结清」，但 `t_consignment.status` 的注释只有三个值。

加第四个状态的问题：合同状态与明细状态会打架——「生效」的合同可能明细全结清了，也可能还有一批没卖。一个字段同时表达「合同是否有效」和「钱是否结清」两件事，必然歧义。

当前做法：**合同状态只管有效期，结清与否从明细实时算**。

```sql
-- 某合同是否已结清
SELECT c.id, c.status,
       SUM(i.settle_status='未结算') AS unsettled
FROM t_consignment c LEFT JOIN t_consignment_item i ON i.consignment_id = c.id
WHERE c.id = ? GROUP BY c.id;
```

`unsettled = 0` 即视为结清。界面显示合同状态时叠加这个计算结果。

## 结算金额与结算流程

金额直接取 `t_consignment_item.consign_price`，不需要从货品反查——这是寄售与调货的关键差别：调货金额要从货品上取（`t_transfer` 没有金额字段），寄售金额本来就记在明细上。

```sql
-- 可结算的寄售明细
SELECT i.id, i.consign_price, g.name, g.status
FROM t_consignment_item i JOIN t_goods g ON g.id = i.goods_id
WHERE i.settle_status = '未结算' AND g.status = '已售';
```

结算步骤（同一事务）：

1. 插 `t_settlement`：`settle_type='寄售'`、`party_id=contract.supplier_id`、`consign_item_id=i.id`、`amount=consign_price`
2. 改 `t_consignment_item.settle_status='已结算'`
3. 插一条 `t_payment`：`biz_type='寄售货款'`、`direction='支付'`（付给供应商）

第 3 步对应 UC-22 业务规则③「结算完成后资金流水在 UC-19 货款记录登记」。

## 状态流转

```
合同：生效 ──到期──> 已到期
      生效 ──手动终止──> 已终止
      已到期/已终止：未结算明细的货品退回供应商

明细：未结算 ──结算（货品已售出）──> 已结算
```

**合同到期不自动改状态**——`expire_date` 过了要靠查询时判断或用定时任务刷，当前按查询时实时比对 `expire_date < CURDATE()` 显示。

## 演示数据现状

| 表 | 条数 | 说明 |
|---|---|---|
| t_consignment | 6 | 状态全为「生效」，供应商取真实商家名 |
| t_consignment_item | 68 | 寄售价 = `min_price × 0.8`，结算状态全「未结算」 |

68 条明细对应的货品状态都是「在柜」，所以**当前没有可结算的明细**——`AC-06` 需要先造几笔寄售货品的成交。这件事放在 `tasks.md` 的 T001。

## 与文档的差异

**一、合同状态无「已结清」**（同上，用实时计算替代）

**二、寄售明细无退货回退**

`settle_status` 只有「未结算/已结算」两态，没有「已退货」。寄售货品售后退货时，明细状态无法表达。

当前处理：退货时若明细已结算，由老板人工处理（在 `remark` 记一笔）；未结算的明细保持「未结算」，货品回「在柜」可再售。

若要严谨，需要给 `t_consignment_item` 加 `returned TINYINT(1)` 与 `return_time`，届时走一次 schema 变更。
