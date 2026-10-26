# 数据模型：客户与到店

**对应分析设计文档**：§4.2 数据层设计、§3.3 状态机（预定记录五状态）
**建表语句**：`docs/schema.sql`

## 实体与关系

```
t_customer 1 ──n t_customer_tag
t_customer 1 ──n t_booking      （按手机号关联，散客可空）
t_goods    1 ──n t_reservation  （一件货品可被多人预约查看）
t_goods    1 ──n t_booking      （同时只能有 1 条有效预定）
t_customer 1 ──n t_customer     （自反：介绍人 introducer_id）
```

## t_customer 客户

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| customer_no | VARCHAR(32) | NOT NULL, UNIQUE | 客户编号，系统生成 |
| name | VARCHAR(64) | NOT NULL | 姓名 |
| phone | VARCHAR(32) | NOT NULL, UNIQUE | 手机号，同一号视为同一客户 |
| wechat | VARCHAR(64) | NULL | 微信号 |
| source_channel | VARCHAR(16) | NOT NULL | 七种固定渠道 |
| introducer_id | BIGINT UNSIGNED | NULL, FK → t_customer | 介绍人（渠道为「朋友介绍」时记） |
| pref_category | VARCHAR(64) | NULL | 偏好品类 |
| pref_size | VARCHAR(32) | NULL | 偏好圈口/尺寸 |
| pref_budget | DECIMAL(12,2) | NULL | 偏好预算 |
| remark | VARCHAR(500) | NULL | 备注，含跟进记录摘要 |
| created_at / updated_at | DATETIME | NOT NULL | 审计字段 |

**手机号上加 UNIQUE**：直接把 UC-04 业务规则①「同一手机号视为同一客户」落到库上。

**`introducer_id` 是自反关系**：报告 §4.2 说自反关系用单表 + 表内外键，这里是「客户介绍客户」，正确落法就是同一张表里的外键。

## t_customer_tag 客户标签

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| customer_id | BIGINT UNSIGNED | NOT NULL, FK → t_customer | 客户 |
| tag | VARCHAR(16) | NOT NULL | 大客户 / 懂行 / 爽快 / 要还价 |

联合唯一 `(customer_id, tag)`。用单独表而不是在 `t_customer` 里存逗号分隔串，因为要按标签筛选客户（FR-007），字符串匹配既慢又不准。

## t_reservation 看货预约

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| resv_no | VARCHAR(32) | NOT NULL, UNIQUE | 预约编号，前缀 `YY` |
| goods_id | BIGINT UNSIGNED | NOT NULL, FK → t_goods | 预约查看的目标货品 |
| name | VARCHAR(64) | NOT NULL | 姓名 |
| phone | VARCHAR(32) | NOT NULL | 手机号 |
| expect_slot | VARCHAR(64) | NOT NULL | 期望到店时段 |
| status | VARCHAR(16) | NOT NULL, 默认「待确认」 | 待确认 / 已到店 / 已取消 |

状态只有三个，比预定简单——预约不锁货、不占额度，只是约个时间。

## t_booking 商品预定

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| booking_no | VARCHAR(32) | NOT NULL, UNIQUE | 预定编号，前缀 `YD` |
| goods_id | BIGINT UNSIGNED | NOT NULL, FK → t_goods | 货品 |
| name | VARCHAR(64) | NOT NULL | 姓名 |
| phone | VARCHAR(32) | NOT NULL | 手机号，3 次额度按此计 |
| customer_id | BIGINT UNSIGNED | NULL, FK → t_customer | 按手机号关联，散客可空 |
| submit_time | DATETIME | NOT NULL | 提交时间 |
| contact_time | DATETIME | NULL | 联系时间，UC-28 标记已联系时写 |
| status | VARCHAR(16) | NOT NULL, 默认「待到店」 | 待到店 / 已联系 / 已达成 / 已爽约 / 已取消 |

## 两个关键计算

### 预定额度占用

UC-13 说「最多同时占用 3 条」，但没明说哪几种状态算占用。从业务规则②③⑤反推：

| 状态 | 是否占额度 | 依据 |
|---|---|---|
| 待到店 | 占 | 尚未履约 |
| 已联系 | 占 | UC-28 规则②「标记已联系只作登记，不改变预定额度」 |
| 已达成 | **不占** | UC-13 规则②「如约达成后释放 1 条额度」 |
| 已爽约 | 占 | UC-13 规则③「爽约或客户取消不释放额度」 |
| 已取消 | 占 | 同上 |

```sql
-- 某手机号当前占用额度
SELECT COUNT(*) FROM t_booking
WHERE phone = ?
  AND status IN ('待到店','已联系','已爽约','已取消');
```

提交预定前查这个数，`>= 3` 返回 422。

### 当日到店名单

```sql
SELECT b.*, g.name AS goods_name, g.image
FROM t_booking b JOIN t_goods g ON g.id = b.goods_id
WHERE b.status IN ('待到店','已联系')
  AND DATE(b.submit_time) = CURDATE()      -- 约定时段在当日
ORDER BY b.submit_time;
```

`t_booking` 没有独立的「约定到店时间」字段，只有 `submit_time` 和 `expect_slot`（字符串时段）。当前按提交日期过滤，粒度是「日」，不是「时段」。

这是个受限点：如果客户今天预约明天的时段，`submit_time` 是今天，就会被算进今天的名单。见下方差异说明。

## 状态流转

```
预定：待到店 ──标记已联系──> 已联系 ──到店──> 已达成（释放额度）
                │                    └──未到──> 已爽约（不释放）
                └──超时未到（系统判定）──> 已爽约
      待到店/已联系 ──客户取消──> 已取消（不释放）

预约：待确认 ──到店──> 已到店
      待确认 ──取消──> 已取消
      待确认 ──时段过期──> 不再出现在查询结果（状态不改）
```

## 与货品状态的联动

UC-13 业务规则⑥要求：客户电话确认到店后，销售把货品置「预留」；到店看后不决定，取消预留回「在柜」。

| 动作 | t_goods.status | t_stock_log |
|---|---|---|
| 确认到店意向 | 在柜 → **预留** | from=在柜, to=预留, ref_type=预定, ref_no=booking_no |
| 取消预留 | 预留 → **在柜** | from=预留, to=在柜, ref_type=预定, ref_no=booking_no |

**注意**：这两步不改变 `t_booking.status`。预定状态（待到店→已联系）和货品状态（在柜→预留）是两条独立的线，销售可以只标记联系而不留货。

## 本 feature 的校验落点

| 规则 | 依据 | 实现位置 |
|---|---|---|
| 手机号唯一 | FR-001 | UNIQUE 索引 + 服务层预检，冲突时带出原档案 |
| 渠道七选一 | FR-003 | 服务层枚举校验 |
| 展示不含价格 | FR-010 | 公开接口的 DTO 不含价格字段（不是前端过滤） |
| 额度 ≤ 3 | FR-020 | 服务层查 COUNT |
| 同货品一条有效预定 | FR-022 | 服务层查 `status IN ('待到店','已联系')` |
| 查询只返回待确认未过期 | FR-019 | 服务层按 status + 时段过滤 |
| 手机号后四位检索 | FR-017 | `RIGHT(phone,4) = ?`，配合 status 过滤避免全表扫 |

## 与文档的差异

**预定缺「约定到店时间」字段**

`t_booking` 只有 `submit_time`（提交时间）和 `expect_slot`（字符串时段，如「今天下午」），没有结构化的约定到店时间。

后果：
- 当日名单按 `submit_time` 的日期过滤，粒度粗
- 无法精确判断「超过约定到店时间」，UC-13 规则⑤的超时判爽约做不准

当前处理：当日名单按提交日期过滤；超时判爽约暂由销售手工登记（UC-28 的「未到」结论）。

若要自动化，需给 `t_booking` 加 `expect_date DATE` 与 `expect_time_slot`，或把 `expect_slot` 规范化成时间区间。
