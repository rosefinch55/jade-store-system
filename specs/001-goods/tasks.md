# 任务清单：货品档案

按依赖顺序排列，每项独立可验证。完成一项勾一项，不要并行跳步。

## 阶段一：数据层

- [ ] **T001 建库与种子数据**
  执行 `docs/schema.sql`（含 `SET NAMES utf8mb4;`），确认 31 张表建成；再执行 `02-seed.sql` 插入角色、权限、类别、来源方类别的初始数据。
  *验收*：`SELECT COUNT(*) FROM t_category` 非零；`SELECT * FROM t_role` 有老板、销售、客户三条。

- [ ] **T002 核对本 feature 涉及的 4 张表**
  确认 `t_goods`、`t_stock_in`、`t_stock_log`、`t_category` 的字段、索引、外键与 `data-model.md` 一致。
  *验收*：`SHOW CREATE TABLE t_goods` 无差异。

## 阶段二：后端

- [ ] **T003 SpringBoot 骨架**
  Maven 项目，依赖 Web、MyBatis-Plus、MySQL Driver、Validation。配置 `application.yml` 的 datasource（指向容器 `db:3306/db_jade`）与 Jackson 时区 `Asia/Shanghai`。
  *验收*：`GET /api/health` 返回 200。

- [ ] **T004 统一响应与异常处理**
  `Result<T>` 包装、`@RestControllerAdvice` 全局异常，按 `contracts/api.md` 的 400/404/409/422 映射。
  *验收*：故意传错参数，返回结构为 `{code, message, data}`。

- [ ] **T005 实体与 Mapper**
  `Goods`、`StockIn`、`StockLog`、`Category` 四个实体与对应 Mapper，字段名严格对齐 `data-model.md`（驼峰映射下划线）。
  *验收*：`goodsMapper.selectById(1)` 能查出数据。

- [ ] **T006 货品编号生成器**
  `JD` + `yyyyMMdd` + 6 位当日流水，用数据库唯一索引兜底并发。
  *验收*：并发提交 100 件，编号无重复、无跳号。

- [ ] **T007 类别接口（4 个）**
  `GET/POST/PUT/DELETE /api/categories`，删除时有货品引用返回 409。
  *验收*：删除已使用的类别被拒绝。

- [ ] **T008 单件建档 `POST /api/goods`**
  按 `contracts/api.md` 的校验表实现：来源类型五选一、调货不记成本、回收/加工/寄售成本必填、寄售价即成本。建档时自动创建或复用批次，写一条 `t_stock_log`（from 为空，to 为「在柜」）。
  *验收*：五种来源类型各建一件，成本字段表现符合规则；流水各有一条。

- [ ] **T009 批量入库 `POST /api/stock-ins`**
  一次多件，整批事务。任一条失败全部回滚。
  *验收*：提交 3 件，第 2 件故意传非法克重，返回 422 且三件都没入库。

- [ ] **T010 列表与详情 `GET /api/goods`、`GET /api/goods/{id}`**
  支持状态、类别、关键词筛选，按 `in_time` 倒序分页。
  *验收*：50 条数据下翻页总数正确，筛选结果与手工清点一致。

- [ ] **T011 修改档案 `PUT /api/goods/{id}`**
  允许字段白名单校验，出现不可改字段返回 400；已售返回 409。
  *验收*：改已售货品被拒；改在柜货品的最低售价成功，编号与状态不变。

- [ ] **T012 状态流水 `GET /api/goods/{id}/logs`**
  按时间正序返回，首条 `fromStatus` 为 null。
  *验收*：新建一件货，流水只有一条入库记录。

## 阶段三：前端

- [ ] **T013 Vue3 骨架**
  Vite + Vue3 + Element Plus + Pinia + Axios。Axios 基址用相对路径 `/api`，不写死 localhost。
  *验收*：`npm run dev` 能起，请求打到 `/api`。

- [ ] **T014 货品列表页**
  筛选栏（状态、类别、关键词）+ 表格 + 分页。列：编号、品名、类别、克重、状态、最低售价、入库时间。
  *验收*：切换状态筛选，列表实时变化。

- [ ] **T015 建档表单**
  单件录入，来源类型联动成本字段（选调货时成本置灰）。保存后返回编号。
  *验收*：五种来源类型各建一件，成本字段的必填与禁用状态正确。

- [ ] **T016 详情与流水页**
  上半部货品信息，下半部流水时间线。已售货品的编辑按钮置灰。
  *验收*：点开一件已成交货品，编辑按钮不可用，流水显示入库到成交的链路。

## 阶段四：联调与交付

- [ ] **T017 打通前后端**
  Nginx 反代 `/api` 到后端容器，前端 dist 由 Nginx 托管。
  *验收*：浏览器打开 `http://IP:8080`，列表能加载真实数据。

- [ ] **T018 容器化**
  两个 Dockerfile（web: nginx:alpine + dist，app: temurin:17-jre + jar），compose 加 `web`、`app` 两个服务，与已有 `db` 同一网络。
  *验收*：`docker compose up -d` 后三个容器运行，`/api/health` 可达。

- [ ] **T019 验收走查**
  按 `spec.md` 的 4 个用户故事的验收场景逐条走一遍，记录结果。
  *验收*：11 条验收场景全部通过，或差异记录在案。

## 依赖关系

```
T001 → T002 → T003 → T004 → T005 → T006
                              ↓
                    T007 → T008 → T009 → T010 → T011 → T012
                                                   ↓
                          T013 → T014 → T015 → T016 → T017 → T018 → T019
```

T007 与 T008 可并行（无依赖）。前端 T013 可在 T010 完成后并行开工。

## 不在此清单内

成交登记、送检、调货、寄售结算、分期、统计报表各自独立 feature。本清单只在 T019 验收时验证「建档到可查询」这条最基础链路。
