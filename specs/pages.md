# 前端页面清单

按角色分组。每页给出路由、权限、主要字段、操作与对应接口。

**两个前端**：
- **柜台端**（Vue3 + Element Plus）：老板与销售用，部署在门店局域网
- **小程序/H5**（客户、商户、供应商、师傅用）：免登录或独立登录

## 一、共用：登录

| 项 | 内容 |
|---|---|
| 路由 | `/login` |
| 权限 | 匿名 |
| 字段 | 用户名、口令 |
| 操作 | 登录、错误提示（401 口令错 / 423 锁定 / 403 停用） |
| 接口 | `POST /api/auth/login` |

登录后按 `permissions` 生成菜单。

## 二、柜台端：老板

### 2.1 首页概览

| 项 | 内容 |
|---|---|
| 路由 | `/` |
| 权限 | 老板、销售 |
| 内容 | 本月销售额、销售客户数、加权周转天数、待办（待审核申请、待结算、今日到店名单） |
| 接口 | `GET /api/stats/sales`、`GET /api/stats/turnover` |

### 2.2 货品管理

| 页面 | 路由 | 主要字段 | 操作 | 接口 |
|---|---|---|---|---|
| 货品列表 | `/goods` | 编号、图、品名、类别、克重、状态、最低售价、入库时间 | 筛选（状态/类别/关键词）、分页、进入详情 | `GET /api/goods` |
| 货品建档 | `/goods/new` | 品名、类别、料种、产地、器型、产状、克重、尺寸、证书号、图、来源类型、来源方、成本、最低售价 | 提交（来源类型联动成本字段） | `POST /api/stock-ins` |
| 货品详情 | `/goods/:id` | 全部字段 + 状态流水时间线 | 改档（已售置灰）、送检、预留 | `GET /api/goods/{id}`、`PUT /api/goods/{id}`、`GET /api/goods/{id}/logs` |

### 2.3 成交与退货

| 页面 | 路由 | 主要字段 | 操作 | 接口 |
|---|---|---|---|---|
| 成交登记 | `/sales/new` | 扫编号/选货品、货品信息与最低售价、成交价、支付方式、定金、客户 | 提交；低于底价时弹授权框 | `GET /api/goods/{id}`、`POST /api/sales` |
| 成交列表 | `/sales` | 成交号、货品、价格、客户、经手人、时间、是否退货 | 筛选、进入详情 | `GET /api/sales` |
| 成交详情 | `/sales/:id` | 成交信息 + 退货记录 + 分期计划 | 发起退货（超 7 天按钮置灰）、建分期 | `GET /api/sales/{id}`、`POST /api/sales/{id}/returns`、`POST /api/sales/{id}/installment` |

### 2.4 分期与货款

| 页面 | 路由 | 主要字段 | 操作 | 接口 |
|---|---|---|---|---|
| 分期管理 | `/installments` | 计划号、成交号、期数、每期应还、已还、剩余、下一期到期日、逾期天数 | 筛选（未还/逾期/已结清）、还款 | `GET /api/installments`、`POST /api/installments/{id}/payments` |
| 货款台账 | `/payments` | 记录号、业务类型、关联单据、金额、收付方向、经手人、时间 | 四个 tab 分类、新增、作废 | `GET /api/payments`、`POST /api/payments`、`POST /api/payments/{id}/void` |

### 2.5 统计

| 项 | 内容 |
|---|---|
| 路由 | `/stats` |
| 内容 | 时段切换（月/季/年）、销售额与退货冲减分列、客户数、加权周转天数、渠道分布、核心客户、最慢 10 件 |
| 接口 | `GET /api/stats/sales`、`GET /api/stats/turnover` |

### 2.6 客户

| 页面 | 路由 | 主要字段 | 操作 | 接口 |
|---|---|---|---|---|
| 客户列表 | `/customers` | 编号、姓名、手机号、渠道、标签、累计成交额 | 筛选（含标签）、新建 | `GET /api/customers`、`POST /api/customers` |
| 客户详情 | `/customers/:id` | 基本信息、偏好、标签、购买记录 | 编辑、改标签、复制手机号 | `GET /api/customers/{id}`、`GET /api/customers/{id}/purchases`、`PUT /api/customers/{id}/tags` |

### 2.7 同行往来

| 页面 | 路由 | 主要字段 | 操作 | 接口 |
|---|---|---|---|---|
| 待审核申请 | `/transfer-applies` | 申请号、供应商、类型、品名、数量、期望价、提交时间 | 通过/驳回（驳回填原因）、查看图 | `GET /api/transfer-applies?status=待审核`、`POST /api/transfer-applies/{id}/audit` |
| 调货记录 | `/transfers` | 记录号、方向、商户、货品、状态、是否已结算、时间 | 新增、改状态（已售/已退回） | `GET /api/transfers`、`POST /api/transfers`、`PUT /api/transfers/{id}/status` |
| 台账结算 | `/settlements` | 商户、未结算记录列表、金额合计、鉴赏期天数 | 勾选多条、选结算方式、确认 | `GET /api/settlements/pending`、`POST /api/settlements/batch` |

### 2.8 寄售

| 页面 | 路由 | 主要字段 | 操作 | 接口 |
|---|---|---|---|---|
| 合同列表 | `/consignments` | 合同号、供应商、签订日、到期日、件数、已结清标记 | 筛选、新建 | `GET /api/consignments`、`POST /api/consignments` |
| 合同详情 | `/consignments/:id` | 合同信息、结清进度、明细表（货品/状态/寄售价/结算状态） | 追加货品、终止合同 | `GET /api/consignments/{id}`、`POST /api/consignments/{id}/items` |
| 寄售结算 | `/consignments/settle` | 可结算明细、寄售价、金额合计 | 勾选、确认结算 | `GET /api/consignments/settleable`、`POST /api/consignments/settlements` |

### 2.9 加工与上门

| 页面 | 路由 | 主要字段 | 操作 | 接口 |
|---|---|---|---|---|
| 加工任务 | `/process-tasks` | 任务号、货品、师傅、工费、期限、状态 | 开始加工 | `GET /api/process-tasks`、`POST /api/process-tasks/{id}/start` |
| 待确认完工 | `/delivery-applies` | 任务号、货品、师傅、完工说明、三阶段图 | 接受/退回 | `GET /api/delivery-applies`、`POST /api/delivery-applies/{id}/confirm` |
| 上门预约 | `/visit-appts` | 预约号、预约方、事项、时段、状态 | 确认、取消 | `GET /api/visit-appts`、`POST /api/visit-appts/{id}/confirm` |

### 2.10 账号与基础数据

| 页面 | 路由 | 主要字段 | 操作 | 接口 |
|---|---|---|---|---|
| 账号管理 | `/users` | 用户名、姓名、角色、状态 | 开通、停用、重置口令 | `GET/POST /api/users`、`PUT /api/users/{id}` |
| 基础数据 | `/base-data` | 三个 tab：来源方、鉴定机构、货品类别 | 增改、停用、删除（被引用时提示改停用） | `/api/source-parties`、`/api/cert-orgs`、`/api/categories` |

## 三、小程序：客户（免登录）

| 页面 | 路由 | 主要字段 | 操作 | 接口 |
|---|---|---|---|---|
| 首页 | `/` | 上部推荐位（94 件精选横滑）、下部全部库入口、类别快捷入口 | 点进详情 | `GET /api/public/goods?featured=true` |
| 全部货品 | `/goods` | 卡片流：图、品名、类别、尺寸（**无价格**） | 类别筛选、搜索、翻页 | `GET /api/public/goods` |
| 货品详情 | `/goods/:no` | 大图、品名、类别、料种、产地、克重、尺寸、描述（**无价格**） | 预约看货、预定 | `GET /api/public/goods/{no}` |
| 预约看货 | `/reserve` | 姓名、手机号、期望时段 | 提交，显示预约编号与地址 | `POST /api/public/reservations` |
| 查预约 | `/my-reservation` | 手机号后四位 | 查询，显示编号、时段、货品名 | `GET /api/public/reservations?phoneTail=` |
| 预定商品 | `/book` | 姓名、手机号、剩余额度提示 | 提交；额度满时禁用 | `GET /api/public/bookings/quota`、`POST /api/public/bookings` |

## 四、小程序：合作商户

| 页面 | 路由 | 主要字段 | 操作 | 接口 |
|---|---|---|---|---|
| 调货价目 | `/merchant/price` | 货品图、品名、类别、圈口、**调货价** | 类别筛选、搜索 | `GET /api/merchant/price-list` |

## 五、小程序：供应商

| 页面 | 路由 | 主要字段 | 操作 | 接口 |
|---|---|---|---|---|
| 出让申请 | `/supplier/apply` | 类型（玉料/成品/回流）、品名、数量、期望价、描述、图 | 提交 | `POST /api/supplier/applies` |
| 我的申请 | `/supplier/applies` | 申请号、品名、状态、审核意见 | 查看 | `GET /api/supplier/applies` |
| 上门预约 | `/supplier/visit` | 关联申请、期望时段 | 提交 | `POST /api/visit-appts` |

## 六、小程序：加工师傅

| 页面 | 路由 | 主要字段 | 操作 | 接口 |
|---|---|---|---|---|
| 我的任务 | `/craftsman/tasks` | 货品、要求、工费、期限、状态 | 查看、进详情 | `GET /api/craftsman/tasks` |
| 任务详情 | `/craftsman/tasks/:id` | 全部字段 + 交付申请状态 | 报完工（传三阶段图） | `GET /api/craftsman/tasks/{id}`、`POST /api/craftsman/tasks/{id}/delivery` |
| 上门预约 | `/craftsman/visit` | 关联交付申请、期望时段 | 提交 | `POST /api/visit-appts` |

## 页面总数

| 端 | 页面数 |
|---|---|
| 柜台端（老板/销售） | 21 |
| 小程序（客户） | 6 |
| 小程序（合作商户） | 1 |
| 小程序（供应商） | 3 |
| 小程序（加工师傅） | 3 |
| 共用登录 | 1 |
| **合计** | **35** |

## 不做页面

以下用例只做接口或后台逻辑，没有独立页面：

- UC-06 统计的一部分（最慢货品）并进统计页，不单开
- UC-19 货款记录的作废在台账页内弹窗，不单开页
- UC-23 与 UC-22 的结算各有一个页面，但共用「结算」组件

## 页面与接口的对应关系

每个页面在 `contracts/api.md` 末尾都有「前端页面与接口的对应」表，这里只给全局视图。开发时以各 feature 的 contracts 为准。
