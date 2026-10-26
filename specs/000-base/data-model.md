# 数据模型：账号与基础数据

**对应分析设计文档**：§4.2 数据层设计、表 4-5 数据表清单
**建表语句**：`docs/schema.sql`

## 实体与关系

```
t_user n ──n t_role n ──n t_permission
   │                        │
   └── t_user_role          └── t_role_permission

t_source_party（独立，供货品与成交引用）
t_cert_org    （独立，供送检引用）
t_category    （独立，已在 001-goods 说明）
```

- 用户与角色多对多：一个账号可同时是老板与销售，也可以是多角色外部账号
- 角色与权限多对多：一个角色聚合多条权限
- 权限与功能用例一一对应，编码取 UC 编号

## t_user 用户

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| username | VARCHAR(64) | NOT NULL, UNIQUE | 登录名 |
| name | VARCHAR(64) | NOT NULL | 姓名 |
| password_hash | VARCHAR(128) | NOT NULL | bcrypt 密文，禁止明文 |
| status | VARCHAR(8) | NOT NULL, 默认「启用」 | 启用 / 停用 |
| created_at / updated_at | DATETIME | NOT NULL | 审计字段 |

## t_role 角色

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| role_code | VARCHAR(16) | NOT NULL | BOSS / SALE / MERCHANT / SUPPLIER / CRAFTSMAN |
| role_name | VARCHAR(32) | NOT NULL | 老板 / 销售 / 合作商户 / 供应商 / 加工师傅 |
| remark | VARCHAR(255) | NULL | 备注 |

## t_permission 权限

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| perm_code | VARCHAR(16) | NOT NULL | UC-01 ~ UC-28 |
| perm_name | VARCHAR(64) | NOT NULL | 用例名 |
| remark | VARCHAR(255) | NULL | 备注 |

## t_user_role 用户-角色

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| user_id | BIGINT UNSIGNED | NOT NULL, FK → t_user | 用户 |
| role_id | BIGINT UNSIGNED | NOT NULL, FK → t_role | 角色 |

联合唯一 `(user_id, role_id)`，双向 `ON DELETE CASCADE`。

## t_role_permission 角色-权限

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| role_id | BIGINT UNSIGNED | NOT NULL, FK → t_role | 角色 |
| permission_id | BIGINT UNSIGNED | NOT NULL, FK → t_permission | 权限 |

联合唯一 `(role_id, permission_id)`，双向 `ON DELETE CASCADE`。

两张关联表只有「有/无」两种状态，**不表达「只读」**。报告 §4.4 矩阵里标「只读」的组合（如销售对 UC-07、UC-20、UC-26），在库里与「可执行」同权，只读限制由前端按钮置灰与接口参数校验实现。这一取舍已写在权限矩阵的注里。

## t_source_party 来源方

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| party_type | VARCHAR(16) | NOT NULL | 供应商 / 加工师傅 / 同行商户 |
| name | VARCHAR(128) | NOT NULL | 公司名 / 师傅姓名 / 店名 |
| contact_person | VARCHAR(64) | NULL | 联系人，名称为个人时留空 |
| phone | VARCHAR(32) | NULL | 联系电话 |
| remark | VARCHAR(255) | NULL | 备注，供应商的「玉料/成品/回流」细分暂存于此 |

唯一性：`(party_type, name)` 组合唯一，即同一类别内名称不重复，跨类别可同名。

## t_cert_org 鉴定机构

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 代理键 |
| name | VARCHAR(128) | NOT NULL | 机构名称 |
| phone | VARCHAR(32) | NULL | 联系电话 |
| remark | VARCHAR(255) | NULL | 备注 |

## 初始化数据

`docs/seed.sql` 已包含：

| 表 | 条数 | 内容 |
|---|---|---|
| t_role | 5 | 老板、销售、合作商户、供应商、加工师傅 |
| t_permission | 28 | UC-01 ~ UC-28 |
| t_role_permission | 36 | 按 §4.4 矩阵 |
| t_user | 2 | admin（老板）、sale01（销售），口令均为 123456 的 bcrypt |
| t_category | 6 | 手镯、挂件吊坠、手串项链、戒指耳饰、把件摆件、其他 |

## 本 feature 的校验落点

| 规则 | 依据 | 实现位置 |
|---|---|---|
| 口令不得明文 | FR-001 | 写入前 BCrypt 加密 |
| 5 次错误锁 10 分钟 | FR-002 | 见下方实现说明 |
| 用户名唯一 | FR-006 | 唯一索引 + 服务层预检 |
| 名称同类别唯一 | FR-008 | `(party_type, name)` 唯一索引 |
| 被引用不可删 | FR-009 | 服务层查引用计数，有引用返回 409 |
| 老板账号不可停用 | 边界情况 | 服务层按 role_code 拦截 |

## 实现说明：登录失败计数

`t_user` 表没有失败次数与锁定截止时间字段。两条路：

**采用**：服务层内存缓存记录 `(username → 失败次数, 锁定截止时间)`。单机部署够用，重启后计数清零——对演示环境可接受，且不会因锁死而无法登录。

**未采用**：给 `t_user` 加 `login_fail_count`、`locked_until` 两列。改动会与报告 §4.2 的字段说明不一致，且需要重跑 schema，代价大于收益。

若将来要多实例部署，再改表。
