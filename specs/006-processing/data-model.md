# 数据模型：加工与上门

**对应分析设计文档**：§4.2 数据层设计
**建表语句**：`docs/schema.sql`
**演示数据**：`docs/demo-business.sql` 已生成 204 条加工任务、5 个加工师傅

## 实体与关系

```
t_goods          1 ──n t_process_task    （一件货品可有多次加工任务）
t_source_party   1 ──n t_process_task    （分派给加工师傅）
t_process_task   1 ──n t_delivery_apply  （一条任务可多次提交，但只有一条待确认）
t_delivery_apply 1 ──n t_delivery_image  （每份申请三类图）
t_transfer_apply 1 ──0..1 t_visit_appt   （出让交货的上门）
t_delivery_apply 1 ──0..1 t_visit_appt   （完工交付的上门）
```

`t_visit_appt` 的两个外键二选一非空，表达「这次上门是来交货，还是来交付加工件」。

## t_process_task 加工任务

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| task_no | VARCHAR(32) | NOT NULL, UNIQUE | 任务编号，前缀 `JG` |
| goods_id | BIGINT UNSIGNED | NOT NULL, FK → t_goods | 加工对象 |
| craftsman_id | BIGINT UNSIGNED | NOT NULL, FK → t_source_party | 加工师傅，类别须为「加工师傅」 |
| requirement | VARCHAR(500) | NOT NULL | 加工要求 |
| labor_fee | DECIMAL(12,2) | NOT NULL | 工费 |
| deadline | DATE | NOT NULL | 期限 |
| status | VARCHAR(8) | NOT NULL, 默认「待加工」 | 待加工 / 在加工 / 待交付 / 已交付 |
| urge_count | INT | NOT NULL, 默认 0 | 门店催办次数（2026-10-26 加） |
| last_urge_at | DATETIME | NULL | 最近一次催办时间 |

**「逾期」不落库**：判定口径是 `status ∈ {待加工, 在加工}` 且 `deadline < 今天`，由接口现算（返回 `overdue` 与 `overdueDays`）。存一个逾期状态会碰到两个麻烦：过一天要批量刷库，且老板把任务改成「待交付」时得记得回滚标记。落库的只有催办这个动作本身。

**演示数据的状态分布**：待加工约 2 成、在加工 3 成、待交付 2 成、已交付 5 成（204 条里）。按上面的口径，2026-10-26 实测逾期 111 条（待加工 33 + 在加工 78），期限集中在 8–9 月的历史数据上。

## t_delivery_apply 完工交付申请

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| apply_no | VARCHAR(32) | NOT NULL, UNIQUE | 申请编号，前缀 `JGJF` |
| task_id | BIGINT UNSIGNED | NOT NULL, FK → t_process_task | 任务 |
| note | VARCHAR(500) | NULL | 完工说明 |
| submit_time | DATETIME | NOT NULL | 提交时间 |
| status | VARCHAR(8) | NOT NULL, 默认「待确认」 | 待确认 / 已确认 |

**「一条任务只能有一条待确认申请」（FR-009）**：服务层查 `status='待确认'` 的条数，已有则拒绝。表上不加唯一约束，因为已确认的历史申请可以有多条（返工后再次交付）。

## t_delivery_image 交付过程图

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| apply_id | BIGINT UNSIGNED | NOT NULL, FK → t_delivery_apply | 交付申请 |
| stage | VARCHAR(16) | NOT NULL | 加工前 / 加工中 / 完工 |
| image_path | VARCHAR(255) | NOT NULL | 图片路径 |

**这张表正好覆盖 UC-17 的三类图**，`stage` 字段就是为它设计的。提交时校验三个阶段各至少一张。

## t_visit_appt 上门预约

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| appt_no | VARCHAR(32) | NOT NULL, UNIQUE | 预约编号，前缀 `SM` |
| appt_party | VARCHAR(128) | NOT NULL | 预约方（供应商或师傅名） |
| matter | VARCHAR(255) | NOT NULL | 事项（上门交接内容） |
| expect_date | DATE | NOT NULL | 期望上门日期（2026-10-26 由 expect_slot 文本拆出） |
| start_time | TIME | NOT NULL | 时段起，10:00–19:00，整点或半点 |
| end_time | TIME | NOT NULL | 时段止，服务端按起 + 1 小时写入 |
| submit_time | DATETIME | NOT NULL | 提交时间 |
| status | VARCHAR(8) | NOT NULL, 默认「待上门」 | 待上门 / 已完成 / 已取消 |
| transfer_apply_id | BIGINT UNSIGNED | NULL, FK → t_transfer_apply | 出让交货的上门 |
| delivery_apply_id | BIGINT UNSIGNED | NULL, FK → t_delivery_apply | 完工交付的上门 |

**两个外键二选一**：服务层校验「有且仅有一个非空」，对应 UC-15 规则①的两个来源。

**时段怎么占**：一次交接固定一小时，`end_time` 服务端算。判重用区间重叠（`from < 已有.end && 已有.start < to`），与客户到店预约（`t_reservation.expect_slot` 本来就是 DATETIME）也用同一个区间比对，两边互相避让。

## 状态流转

```
任务：待加工 ──开始加工──> 在加工 ──提交完工──> 待交付 ──老板确认──> 已交付
                                                              ↓
                                                    按 UC-02 建档入库

交付申请：待确认 ──老板确认──> 已确认

上门预约：待上门 ──完成──> 已完成
          待上门 ──取消──> 已取消（时段释放）
```

**任务状态推进**：`待加工 → 在加工` 由老板操作（派工后开始），`在加工 → 待交付` 由师傅提交完工，`待交付 → 已交付` 由老板确认。

当前 `001`/`004` 的文档没有明确「待加工 → 在加工」由谁触发，这里定为老板在任务列表上点「开始加工」。若实际是师傅自行开始，要改 FR。

## 与货品的联动

| 动作 | t_goods.status | 说明 |
|---|---|---|
| 任务创建（加工完工来源建档） | 在柜 | 货品先按「加工完工」入库，任务挂在其上 |
| 任务已交付 | 在柜 | 不自动改状态，由老板确认后决定是否上柜 |
| 出让申请通过后货到店 | 在柜 | 按 UC-02 建档 |

**加工任务不改变货品状态**——任务是记录加工过程，货品状态由入库与成交驱动。这一点与 `004` 的调货（改货品状态）不同。

## 演示数据现状

| 表 | 条数 | 说明 |
|---|---|---|
| t_process_task | 204 | 分给 5 个师傅，四种状态都有 |
| t_delivery_apply | 0 | **未生成**，见 tasks T001 |
| t_delivery_image | 0 | 同上 |
| t_visit_appt | 0 | **未生成**，见 tasks T001 |

三张表为空，`006` 的演示链路走不通——需要造几份完工申请与上门预约。

## 与文档的差异

**一、`expect_slot` 是自由文本，无法判时段冲突 —— 已消除（2026-10-26）**

原来是 `VARCHAR(64)` 文本，只能按字符串相等判重。现在拆成 `expect_date` + `start_time` + `end_time` 三列（升级脚本 `docs/upgrade-20261026.sql`，改表前 `t_visit_appt` 为 0 行，无需换算历史值），冲突判定改成区间重叠。函数唯一索引跟着改：`uk_visit_slot_open` 建在 `(case when status='待上门' then concat(expect_date,'#',start_time,'#',end_time) end)` 上，整段完全相同的并发重复提交由数据库挡，部分重叠由服务层挡。

**二、任务状态「待加工 → 在加工」的触发者未定义**

UC-16/17 只说师傅能看和报完工，没说「开始加工」谁点。当前定为老板操作，写在上面的状态流转里。若与实际做法不符，改一处即可。
