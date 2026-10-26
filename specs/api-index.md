# 接口总览

| Feature | 方法 | 路径 | 用途 |
|---|---|---|---|
| `000-base` | POST | `/api/auth/login` | 登录 |
| `000-base` | POST | `/api/auth/logout` | 登出 |
| `000-base` | GET | `/api/auth/me` | 当前用户与权限 |
| `000-base` | PUT | `/api/auth/password` | 修改自己的口令 |
| `000-base` | GET | `/api/users` | 账号列表，可按角色、状态、关键词筛选 |
| `000-base` | POST | `/api/users` | 开通账号 |
| `000-base` | PUT | `/api/users/{id}` | 改姓名、启停用、重设口令 |
| `000-base` | GET | `/api/roles` | 角色列表 |
| `000-base` | GET | `/api/permissions` | 权限点列表（28 条） |
| `000-base` | GET | `/api/source-parties` | 列表，按 `partyType`、`status`、`keyword` 筛选 |
| `000-base` | POST | `/api/source-parties` | 新增 |
| `000-base` | PUT | `/api/source-parties/{id}` | 修改 |
| `000-base` | DELETE | `/api/source-parties/{id}` | 删除，被引用时拒绝 |
| `000-base` | GET | `/api/cert-orgs` | 老板、销售 |
| `000-base` | POST | `/api/cert-orgs` | 老板 |
| `000-base` | PUT | `/api/cert-orgs/{id}` | 老板 |
| `000-base` | DELETE | `/api/cert-orgs/{id}` | 老板，被 `t_inspection` 引用时 409 |
| `001-goods` | GET | `/api/goods` | 货品列表，支持筛选与分页 |
| `001-goods` | GET | `/api/goods/{id}` | 单件详情 |
| `001-goods` | POST | `/api/goods` | 单件建档 |
| `001-goods` | PUT | `/api/goods/{id}` | 修改基础信息 |
| `001-goods` | GET | `/api/goods/{id}/logs` | 状态变更流水 |
| `001-goods` | POST | `/api/stock-ins` | 一次入库多件，创建批次 |
| `001-goods` | GET | `/api/stock-ins/{id}` | 批次详情与货品清单 |
| `001-goods` | GET | `/api/categories` | 类别列表 |
| `001-goods` | POST | `/api/categories` | 新增类别 |
| `001-goods` | PUT | `/api/categories/{id}` | 修改类别 |
| `001-goods` | DELETE | `/api/categories/{id}` | 删除类别，有货品引用时拒绝 |
| `001-goods` | GET | `/api/categories` | 返回全部类别，按名称排序 |
| `001-goods` | POST | `/api/categories` | body: `{ "name": "...", "remark": "..." }`，名称唯一 |
| `001-goods` | PUT | `/api/categories/{id}` | 改名或备注 |
| `001-goods` | DELETE | `/api/categories/{id}` | 有货品引用时返回 409，提示先转移货品 |
| `002-sale` | POST | `/api/sales` | 登记成交 |
| `002-sale` | GET | `/api/sales` | 成交列表，按时间、客户、货品筛选 |
| `002-sale` | GET | `/api/sales/{id}` | 成交详情（含退货、分期状态） |
| `002-sale` | POST | `/api/sales/{id}/returns` | 登记退货 |
| `002-sale` | POST | `/api/sales/{id}/installment` | 为该成交建分期计划 |
| `002-sale` | GET | `/api/installments` | 计划列表，可按状态、逾期筛选 |
| `002-sale` | GET | `/api/installments/{id}` | 计划详情与还款记录 |
| `002-sale` | POST | `/api/installments/{id}/payments` | 记录一次还款 |
| `002-sale` | POST | `/api/payments` | 老板 |
| `002-sale` | GET | `/api/payments` | 老板 |
| `002-sale` | GET | `/api/stats/sales` | 销售额、客户数、渠道分布、核心客户 |
| `002-sale` | GET | `/api/stats/turnover` | 加权周转周期、最慢货品清单 |
| `003-customer` | GET | `/api/customers` | 列表，按编号/姓名/手机号/微信号/标签筛选 |
| `003-customer` | GET | `/api/customers/{id}` | 详情 |
| `003-customer` | POST | `/api/customers` | 新建 |
| `003-customer` | PUT | `/api/customers/{id}` | 修改 |
| `003-customer` | GET | `/api/customers/{id}/purchases` | 购买记录（由成交汇总） |
| `003-customer` | PUT | `/api/customers/{id}/tags` | 覆盖式更新标签 |
| `003-customer` | GET | `/api/public/goods` | 在柜货品列表（推荐位 + 全部库） |
| `003-customer` | GET | `/api/public/goods/{goodsNo}` | 单件展示页 |
| `003-customer` | POST | `/api/public/reservations` | 提交预约 |
| `003-customer` | GET | `/api/public/reservations` | 按手机号后四位查 |
| `003-customer` | GET | `/api/public/bookings/quota?phone=13800138000` | 查该号码剩余额度 |
| `003-customer` | POST | `/api/public/bookings` | 提交预定 |
| `003-customer` | GET | `/api/bookings/today` | 当日到店名单 |
| `003-customer` | POST | `/api/bookings/{id}/contact` | 标记已联系 |
| `003-customer` | POST | `/api/bookings/{id}/result` | 登记到店结论 |
| `003-customer` | POST | `/api/goods/{id}/reserve` | 货品置「预留」 |
| `003-customer` | POST | `/api/goods/{id}/unreserve` | 取消预留，回「在柜」 |
| `004-transfer` | GET | `/api/merchant/price-list` | 在柜货品的调货价目 |
| `004-transfer` | POST | `/api/supplier/applies` | 提交申请 |
| `004-transfer` | GET | `/api/supplier/applies` | 我的申请列表 |
| `004-transfer` | GET | `/api/transfer-applies` | 申请列表，可按状态筛选 |
| `004-transfer` | GET | `/api/transfer-applies/{id}` | 详情（含期望出让价） |
| `004-transfer` | POST | `/api/transfer-applies/{id}/audit` | 通过或驳回 |
| `004-transfer` | GET | `/api/transfers` | 列表，按商户、方向、状态筛选 |
| `004-transfer` | POST | `/api/transfers` | 登记一条调货 |
| `004-transfer` | PUT | `/api/transfers/{id}/status` | 改状态（已售/已退回） |
| `004-transfer` | GET | `/api/settlements/pending?merchantId=3` | 该商户未结算清单 |
| `004-transfer` | POST | `/api/settlements/batch` | 批量结算 |
| `004-transfer` | GET | `/api/settlements` | 结算历史 |
| `005-consign` | GET | `/api/consignments` | 合同列表，按供应商、状态筛选 |
| `005-consign` | GET | `/api/consignments/{id}` | 合同详情，含明细与结清进度 |
| `005-consign` | POST | `/api/consignments` | 登记合同（可同时录货品） |
| `005-consign` | POST | `/api/consignments/{id}/items` | 向已有合同追加寄售货品 |
| `005-consign` | POST | `/api/consignments/{id}/terminate` | 终止合同，未结算货品退回 |
| `005-consign` | GET | `/api/consignments/settleable` | 所有可结算明细（已售出未结算） |
| `005-consign` | POST | `/api/consignments/settlements` | 批量结算 |
| `006-processing` | GET | `/api/craftsman/tasks` | 我的任务列表 |
| `006-processing` | GET | `/api/craftsman/tasks/{id}` | 任务详情 |
| `006-processing` | POST | `/api/craftsman/tasks/{id}/delivery` | 提交完工交付 |
| `006-processing` | GET | `/api/process-tasks` | 全部任务，按师傅、状态、期限筛选 |
| `006-processing` | GET | `/api/process-tasks/{id}` | 任务详情（含交付申请） |
| `006-processing` | POST | `/api/process-tasks/{id}/start` | 开始加工（待加工 → 在加工） |
| `006-processing` | GET | `/api/delivery-applies` | 待确认列表 |
| `006-processing` | POST | `/api/delivery-applies/{id}/confirm` | 确认完工 |
| `006-processing` | POST | `/api/visit-appts` | 提交预约 |
| `006-processing` | GET | `/api/visit-appts` | 预约列表 |
| `006-processing` | POST | `/api/visit-appts/{id}/confirm` | 确认 |
| `006-processing` | POST | `/api/visit-appts/{id}/cancel` | 取消 |

## 说明

- 共 92 个接口，按 feature 分组
- 认证类接口见 `000-base`，其余均需登录态
- 客户侧免登录接口统一在 `/api/public/**` 下，见 `003-customer`
- 统一响应包装、错误码、分页约定见 `README.md`
