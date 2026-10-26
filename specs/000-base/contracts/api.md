# 接口契约：账号与基础数据

**风格**：REST，前缀 `/api`
**鉴权**：令牌，登录后放入请求头 `Authorization: Bearer <token>`，有效期 8 小时。前缀必须带 `Bearer `，裸令牌一律拒绝（RFC 6750）。
**框架**：SpringBoot 3，`@RestController`

通用约定（响应包装、分页、时间格式、错误码）见 `../README.md`，本文只补充本 feature 特有的部分。

## 本 feature 新增错误码

| HTTP | 含义 | 场景 |
|---|---|---|
| **423** | 账号锁定 | 连续 5 次口令错误，锁 10 分钟 |
| 403 | 账号停用或无权限 | 停用账号登录、越权访问 |

## 认证

| 方法 | 路径 | 用途 | 权限 |
|---|---|---|---|
| POST | `/api/auth/login` | 登录 | 匿名 |
| POST | `/api/auth/logout` | 登出 | 已登录 |
| GET | `/api/auth/me` | 当前用户与权限 | 已登录 |
| PUT | `/api/auth/password` | 修改自己的口令 | 已登录 |

### POST /api/auth/login

**请求**

```json
{ "username": "admin", "password": "123456" }
```

**成功**

```json
{ "code": 0, "message": "ok", "data": {
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresIn": 28800,
  "user": {
    "id": 1, "username": "admin", "name": "张三（老板）",
    "roles": [{ "code": "BOSS", "name": "老板" }],
    "permissions": ["UC-01", "UC-02", "UC-06", "UC-07", "..."]
  }
}}
```

**失败**

| 场景 | HTTP | message |
|---|---|---|
| 用户不存在或口令错 | 401 | 用户名或口令错误 |
| 连续错 5 次后 | 423 | 账号已锁定，请 10 分钟后重试或联系老板解锁 |
| 账号停用 | 403 | 账号已停用 |

`permissions` 是角色权限的并集，前端据此生成菜单、置灰按钮。

### GET /api/auth/me

返回同 login 的 `user` 结构，用于刷新页面后恢复登录态。

### PUT /api/auth/password

```json
{ "oldPassword": "123456", "newPassword": "Abc@2026" }
```

新口令要求：8 位以上，含字母与数字。旧口令错误返回 400。

## 账号管理

仅老板可调用（`role_code = BOSS`）。

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/users` | 账号列表，可按角色、状态、关键词筛选 |
| POST | `/api/users` | 开通账号 |
| PUT | `/api/users/{id}` | 改姓名、启停用、重设口令 |
| GET | `/api/roles` | 角色列表 |
| GET | `/api/permissions` | 权限点列表（28 条） |

### POST /api/users

```json
{ "username": "merchant01", "name": "同和祥", "password": "Init@123",
  "roleIds": [3] }
```

| 校验 | 返回 |
|---|---|
| 用户名已存在 | 409 |
| `roleIds` 为空 | 400 |
| 口令不符合强度 | 400 |
| 角色含 BOSS 且操作者非 BOSS | 403 |

**响应**

```json
{ "code": 0, "message": "ok", "data": { "id": 12, "username": "merchant01", "status": "启用" } }
```

### PUT /api/users/{id}

```json
{ "name": "同和祥（新）", "status": "停用", "password": "Reset@2026" }
```

字段均可选。带 `password` 时表示老板重置口令。

**规则**：不允许把老板自己的账号置为停用，返回 409，提示「不能停用当前登录账号」。

## 基础数据：来源方

| 方法 | 路径 | 用途 | 权限 |
|---|---|---|---|
| GET | `/api/source-parties` | 列表，按 `partyType`、`status`、`keyword` 筛选 | 老板、销售（只读） |
| POST | `/api/source-parties` | 新增 | 老板 |
| PUT | `/api/source-parties/{id}` | 修改 | 老板 |
| DELETE | `/api/source-parties/{id}` | 删除，被引用时拒绝 | 老板 |

### 数据结构

```json
{ "id": 7, "partyType": "供应商", "name": "新疆料商张师傅",
  "contactPerson": null, "phone": "138****1234",
  "remark": "玉料", "status": "启用" }
```

`partyType` 取值：`供应商`、`加工师傅`、`同行商户`。

### DELETE /api/source-parties/{id}

被 `t_goods.src_party`、`t_transfer`、`t_consignment` 等引用时返回 409：

```json
{ "code": 409, "message": "该来源方已被 12 件货品引用，只能停用", "data": { "refCount": 12 } }
```

前端据此提示用户改为停用。

## 基础数据：鉴定机构

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/api/cert-orgs` | 老板、销售 |
| POST | `/api/cert-orgs` | 老板 |
| PUT | `/api/cert-orgs/{id}` | 老板 |
| DELETE | `/api/cert-orgs/{id}` | 老板，被 `t_inspection` 引用时 409 |

结构与来源方类似，字段为 `name`、`phone`、`remark`、`status`。

## 基础数据：货品类别

已在 `001-goods/contracts/api.md` 定义（`GET/POST/PUT/DELETE /api/categories`），此处不重复。

新增约定：类别删除被拒时同样返回 409 并带 `refCount`。

## 停用与删除的区别

| 操作 | 语义 | 用在什么情况 |
|---|---|---|
| 停用（`PUT` 带 `status=停用`） | 保留历史引用，新建时不再出现在下拉 | 已被业务数据引用 |
| 删除（`DELETE`） | 物理删除 | 从未被任何业务数据引用 |

两者不是替代关系，前端在删除被拒后应引导用户改走停用。

## 鉴权实现约定

- 登录成功签发令牌，**载荷是 AES-256-GCM 密文**（键名 `d`），标准声明只留 `iat`/`exp`；
  `userId`、`username`、`roles`、`permissions` 全部在密文里，base64 解不开，抓包也读不出权限结构
- 签名用 HMAC-SHA256，密钥与加密密钥分开配置（`jade.jwt.secret` / `jade.jwt.enc-secret`），
  正式部署必须用环境变量覆盖，仓库里的默认值只供演示
- 每次请求由拦截器校验签名、过期与解密，任一环节失败都按「未登录」返回 401，不区分原因
- 接口级权限用注解标注，如 `@RequirePerm("UC-12")`，拦截器比对权限集合
- 「只读」不单独建权限点，由 Controller 的参数校验与前端置灰共同表达
- 登录入参做边界收敛：用户名 ≤32 字符且不含控制字符，口令 ≤128 字符；SQL 全部走参数化查询
- 响应头统一加 `X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Referrer-Policy: no-referrer`，
  接口响应 `Cache-Control: no-store`，令牌与业务数据不落代理缓存
