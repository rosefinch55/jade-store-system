# 数据模型：货品档案

**对应分析设计文档**：§4.2 数据层设计、表 4-5 数据表清单
**建表语句**：`docs/schema.sql`，已在本机与云服务器 MySQL 8.0 验证（31 张表全部建成）

## 实体与关系

```
t_category 1 ──── n t_goods
t_stock_in 1 ──── n t_goods
t_goods    1 ──── n t_stock_log
```

- 一件货品属于一个类别、一个入库批次
- 一件货品的每次状态变更产生一条流水
- 删除策略：类别与批次均 `ON DELETE RESTRICT`，有货品引用时不允许删除

## t_goods 货品档案

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK, 自增 | 代理键 |
| goods_no | VARCHAR(32) | NOT NULL, UNIQUE | 货品编号，一物一码，系统生成，不可改 |
| name | VARCHAR(128) | NOT NULL | 品名 |
| category_id | BIGINT UNSIGNED | NOT NULL, FK → t_category | 类别 |
| material | VARCHAR(32) | NOT NULL | 料种，自由填写（无法穷举） |
| origin | VARCHAR(32) | NOT NULL | 产地，自由填写（证书不判产地，靠眼力） |
| shape | VARCHAR(32) | NOT NULL | 器型，自由填写 |
| form | VARCHAR(16) | NOT NULL | 产状：山料 / 籽料 / 山流水料 / 戈壁料 |
| weight_g | DECIMAL(10,2) | NOT NULL | 克重 |
| size | VARCHAR(64) | NULL | 尺寸或圈口，如「内径 56mm」 |
| cert_no | VARCHAR(64) | NULL | 最新一张证书编号，完整证书在 t_certificate |
| image | VARCHAR(500) | NULL | 图片路径或 URL |
| src_type | VARCHAR(16) | NOT NULL | 来源类型：采购 / 回收 / 调货 / 加工完工 / 寄售 |
| src_party | VARCHAR(128) | NULL | 来源方名称 |
| src_doc | VARCHAR(64) | NULL | 来源单据号，寄售时为寄售合同编号 |
| cost | DECIMAL(12,2) | NULL | 成本，调货调入为空 |
| min_price | DECIMAL(12,2) | NOT NULL | 最低售价，成交价不得低于此值 |
| transfer_price | DECIMAL(12,2) | NULL | 调货价，在柜时维护 |
| in_time | DATETIME | NOT NULL | 入库时间，系统时间 |
| stock_in_id | BIGINT UNSIGNED | NOT NULL, FK → t_stock_in | 入库批次 |
| status | VARCHAR(16) | NOT NULL, 默认「在柜」 | 八状态，见下 |
| **is_featured** | TINYINT(1) | NOT NULL, 默认 0 | **演示扩展列**：1 = 首页精选，可进推荐位；0 = 仅库内可查，不给首页权重 |
| created_at / updated_at | DATETIME | NOT NULL | 审计字段 |

**关于 `is_featured`**：报告 §4.2 的 `t_goods` 没有这一列，是为演示追加的。规则来自实做：**同一件货品能在朋友圈找到原始帖、且图就是那一件的，只有 94 件**（占 679 的 14%），其余 585 件的图是按器型从图池轮转分配的，器型对但不是同一件。首页只放这 94 件，避免把"手镯配手镯"的兜底图当成实拍推荐。

**索引**：`uk_goods_no`(unique)、`idx_goods_category`、`idx_goods_stock_in`、`idx_goods_status`

**状态取值**：在柜 / 已送检 / 预留 / 已售 / 已订 / 已调出 / 代销中 / 待处理

## t_stock_in 入库批次

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| in_time | DATETIME | NOT NULL | 入库时间 |
| receiver_id | BIGINT UNSIGNED | NOT NULL, FK → t_user | 验收人 |
| remark | VARCHAR(255) | NULL | 备注 |
| created_at | DATETIME | NOT NULL | 审计字段 |

一个批次对应一次收货，含多件货品。同批次多件的意义在于：一次买料加工出来的几件、一次同行调货的几件，来源相同、时间相同，方便回查。

## t_stock_log 状态流水

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| goods_id | BIGINT UNSIGNED | NOT NULL, FK → t_goods | 货品 |
| from_status | VARCHAR(16) | NULL | 变更前状态，入库首条为空 |
| to_status | VARCHAR(16) | NOT NULL | 变更后状态，取值同 t_goods.status |
| ref_type | VARCHAR(16) | NULL | 关联单据类型：成交/退货/调货记录/加工任务/送检记录/入库批次/寄售合同 |
| ref_no | VARCHAR(32) | NULL | 关联单据编号 |
| operator_id | BIGINT UNSIGNED | NOT NULL, FK → t_user | 经手人 |
| log_time | DATETIME | NOT NULL | 流水时间 |

**为什么用 ref_type + ref_no 而不是外键**：流水可能指向成交、送检、调货等不同表，无法用单一外键表达；文档 §4.2 已说明这是逻辑引用，与 t_payment 同口径。

## t_category 货品类别

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| name | VARCHAR(64) | NOT NULL | 类别名称，如翡翠 / 和田玉 / 碧玉 / 金丝玉 |
| remark | VARCHAR(255) | NULL | 备注 |
| created_at | DATETIME | NOT NULL | 审计字段 |

## 本 feature 涉及的校验规则

| 规则 | 依据 | 实现位置 |
|---|---|---|
| 一件一编号，不可复用 | UC-02 业务规则① | 编号生成器 + 唯一索引 |
| 调货调入不记成本 | UC-02 业务规则③ | 建档时按 src_type 置空 cost |
| 回收按回流作价入成本 | UC-02 业务规则② | 建档时校验 cost 必填 |
| 寄售价即成本与结算依据 | UC-02 业务规则⑤ | 建档时 cost = 寄售价 |
| 已售不可改档 | FR-010 | 更新前校验状态 |
| 状态只能由业务动作变更 | FR-008 | 不暴露改状态接口，只在业务服务内部调用 |

## 与文档的对应

- 表结构：`docs/schema.sql` 中 `t_category`、`t_stock_in`、`t_goods`、`t_stock_log` 四张表，直接执行即可
- 字段说明：报告 §4.2「各表字段说明」
- 状态机：报告 §3.3 货品生命周期（八状态，分三张图给出）
- 类-表映射：报告 §4.2「类-表映射」表
