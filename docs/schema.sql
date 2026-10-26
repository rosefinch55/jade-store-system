-- ⚠️ 初始化脚本：第 12-14 行含 DROP DATABASE db_jade，只能对空实例跑！
-- ⚠️ 绝不能导入运行中的 MySQL（2026-10-26 事故：误导入线上导致演示库被清空，靠 binlog 回放恢复）。
-- ⚠️ 要对比结构请用临时库：CREATE DATABASE x; mysql x < schema.sql

-- =====================================================================
-- 玉器门店顾客服务与经营管理系统  数据库设计（D 阶段·数据层）
-- 数据库：db_jade    版本：MySQL 8.0
-- 字段取自各用例规约的「字段列表」，类-表 / 属性-列 / 对象-行 一一映射
-- 约定：
--   * 代理键 id（BIGINT，自增，唯一且恒定）作主键；业务编码单列并加唯一索引
--   * 外键放在“非 1”方（多方）。删除策略分三类：
--       - RESTRICT：业务行禁止物理删除（主体数据）
--       - CASCADE：多值拆表与多对多拆表，删主记录连带删明细
--       - SET NULL：客户介绍人，介绍人删除后该列置空、客户记录保留
--   * 来源方三表合一：供应商/加工师傅/同行商户共用 t_source_party，类别列区分，
--     名称在同一类别内唯一（唯一键 类别+名称）；外键只保证“指向的来源方存在”，
--     类别与单据类型是否匹配由业务层把关
--   * 台账结算一单一据：调货记录与寄售明细各一条可空外键，二选一必有其一，
--     由业务层把关；货款资金流水另见 t_payment（UC-19）
--   * 库存流水只增不改不删：货品每次状态变更必写一条（不变式）
--   * 金额 DECIMAL(12,2)；克重 DECIMAL(10,2)；尺寸/圈口/料种等枚举用 VARCHAR 并存注释
--   * 每个表、每列均带中文 COMMENT；状态字段注释里列全取值
-- =====================================================================

-- 客户端连接字符集必须显式声明：mysql 命令行默认 latin1 读文件，
-- 中文默认值会被当作多字节乱码，VARCHAR(n) 长度校验超限（如 status VARCHAR(8) DEFAULT '待审核'）
SET NAMES utf8mb4;

DROP DATABASE IF EXISTS db_jade;
CREATE DATABASE db_jade DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE db_jade;

-- =====================================================================
-- 一、基础参照表（UC-07 维护基础数据、UC-08 登录、UC-12 开户）
-- =====================================================================

-- 货品类别
CREATE TABLE t_category (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  name        VARCHAR(64)  NOT NULL COMMENT '类别名称（如翡翠/和田玉/碧玉/金丝玉）',
  remark      VARCHAR(255) NULL     COMMENT '备注',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_category_name (name)
) ENGINE=InnoDB COMMENT='货品类别（UC-07）';

-- 用户（含外部账户，UC-08 登录 / UC-12 开通外部账户；角色多对多见 t_user_role）
CREATE TABLE t_user (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  username      VARCHAR(64)  NOT NULL COMMENT '用户名（登录名）',
  name          VARCHAR(64)  NOT NULL COMMENT '姓名',
  password_hash VARCHAR(128) NOT NULL COMMENT '口令（加密保存，禁止明文）',
  status        VARCHAR(8)   NOT NULL DEFAULT '启用' COMMENT '状态：启用|停用',
  party_id      BIGINT UNSIGNED NULL COMMENT '外部账号绑定的往来方 -> t_source_party（供应商/师傅/商户），内部账号为空（应用层校验，未加外键）',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username)
) ENGINE=InnoDB COMMENT='用户与外部账户（UC-08/UC-12），角色经 t_user_role 关联';

-- 角色（用户-角色多对多，UC-12 开户时分配）
CREATE TABLE t_role (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  role_code  VARCHAR(16)  NOT NULL COMMENT '角色编码：BOSS|SALE|MERCHANT|SUPPLIER|CRAFTSMAN',
  role_name  VARCHAR(32)  NOT NULL COMMENT '角色名：老板|销售|合作商户|供应商|加工师傅',
  remark     VARCHAR(255) NULL COMMENT '备注',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB COMMENT='角色（UC-12 分配，用户-角色多对多）';

-- 权限（一个用例一条权限，角色-权限多对多；矩阵见正文权限矩阵表）
CREATE TABLE t_permission (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  perm_code  VARCHAR(16)  NOT NULL COMMENT '权限编码（对应用例编号，如 UC-01）',
  perm_name  VARCHAR(64)  NOT NULL COMMENT '权限名（用例名）',
  remark     VARCHAR(255) NULL COMMENT '备注',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_permission_code (perm_code)
) ENGINE=InnoDB COMMENT='权限（一用例一权限，角色-权限多对多）';

-- 来源方（供应商/加工师傅/同行商户合一，UC-07）
CREATE TABLE t_source_party (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  party_type     VARCHAR(16)  NOT NULL COMMENT '类别：供应商|加工师傅|同行商户',
  name           VARCHAR(128) NOT NULL COMMENT '名称（公司名/师傅姓名/店名）',
  contact_person VARCHAR(64)  NULL     COMMENT '联系人（名称为个人时留空）',
  phone          VARCHAR(32)  NULL     COMMENT '联系电话',
  remark         VARCHAR(255) NULL     COMMENT '备注',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_source_party (party_type, name)
) ENGINE=InnoDB COMMENT='来源方：供应商/加工师傅/同行商户（UC-07，类别列区分）';

-- 鉴定机构（UC-07）
CREATE TABLE t_cert_org (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  name        VARCHAR(128) NOT NULL COMMENT '鉴定机构名称',
  phone       VARCHAR(32)  NULL     COMMENT '联系电话',
  remark      VARCHAR(255) NULL     COMMENT '备注',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cert_org_name (name)
) ENGINE=InnoDB COMMENT='鉴定机构（UC-07）';

-- =====================================================================
-- 二、货品主档与入库（UC-02 登记货品入库）
-- =====================================================================

-- 入库批次（入库记录 = 入库时间 + 验收人 + 货品清单）
CREATE TABLE t_stock_in (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  in_time      DATETIME     NOT NULL COMMENT '入库时间（系统时间）',
  receiver_id  BIGINT UNSIGNED NOT NULL COMMENT '验收人 -> t_user',
  remark       VARCHAR(255) NULL COMMENT '备注',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_stock_in_receiver (receiver_id),
  CONSTRAINT fk_stock_in_receiver FOREIGN KEY (receiver_id) REFERENCES t_user (id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='入库记录/批次（UC-02）';

-- 货品档案（一物一码，状态八态）
CREATE TABLE t_goods (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  goods_no      VARCHAR(32)   NOT NULL COMMENT '货品编号（条形码，一物一码，系统生成）',
  name          VARCHAR(128)  NOT NULL COMMENT '品名',
  category_id   BIGINT UNSIGNED NOT NULL COMMENT '类别 -> t_category',
  material      VARCHAR(32)   NOT NULL COMMENT '料种（自定义填写，无法穷举）',
  origin        VARCHAR(32)   NOT NULL COMMENT '产地（自定义填写，鉴定证书不判产地，由老板凭眼力判断）',
  shape         VARCHAR(32)   NOT NULL COMMENT '器型（自定义填写，无法穷举）',
  form          VARCHAR(16)   NOT NULL COMMENT '产状：山料|籽料|山流水料|戈壁料',
  weight_g      DECIMAL(10,2) NOT NULL COMMENT '克重',
  size          VARCHAR(64)   NULL COMMENT '尺寸/圈口（如内径56mm）',
  cert_no       VARCHAR(64)   NULL COMMENT '证书号（取最新一张证书的编号，完整证书见 t_certificate）',
  image         VARCHAR(500)  NULL COMMENT '图片（文件路径/URL）',
  src_type      VARCHAR(16)   NOT NULL COMMENT '来源类型：采购|回收|调货|加工完工|寄售',
  src_party     VARCHAR(128)  NULL COMMENT '来源方（供应商/商户/师傅名）',
  src_doc       VARCHAR(64)   NULL COMMENT '来源单据号（寄售时为寄售合同编号）',
  cost          DECIMAL(12,2) NULL COMMENT '成本（调货调入不记成本只记往来；寄售=寄售价，作结算依据）',
  min_price     DECIMAL(12,2) NOT NULL COMMENT '最低售价（成交价不得低于此价）',
  transfer_price DECIMAL(12,2) NULL COMMENT '调货价（UC-11 商户端调货价目，在柜时维护）',
  in_time       DATETIME      NOT NULL COMMENT '入库时间（系统时间）',
  stock_in_id   BIGINT UNSIGNED NOT NULL COMMENT '入库批次 -> t_stock_in（货品清单成员）',
  status        VARCHAR(16)   NOT NULL DEFAULT '在柜' COMMENT '状态：在柜|已送检|预留|已售|已订|已调出|代销中|待处理',
  is_featured   TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '精选上柜位（门户/客户端推荐位展示）',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_goods_no (goods_no),
  KEY idx_goods_category (category_id),
  KEY idx_goods_stock_in (stock_in_id),
  KEY idx_goods_status (status),
  KEY idx_goods_featured (is_featured),
  CONSTRAINT fk_goods_category  FOREIGN KEY (category_id)  REFERENCES t_category (id)  ON DELETE RESTRICT,
  CONSTRAINT fk_goods_stock_in  FOREIGN KEY (stock_in_id)  REFERENCES t_stock_in (id)  ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='货品档案（UC-01/UC-02，状态机见 07a；状态变更流水见 t_stock_log）';

-- =====================================================================
-- 三、客户与营销（UC-04 维护客户信息 / UC-09 浏览 / UC-10 预约看货 / UC-13 预定商品）
-- =====================================================================

-- 客户
CREATE TABLE t_customer (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  customer_no   VARCHAR(32)   NOT NULL COMMENT '客户编号（系统生成）',
  name          VARCHAR(64)   NOT NULL COMMENT '姓名',
  phone         VARCHAR(32)   NOT NULL COMMENT '手机号',
  wechat        VARCHAR(64)   NULL COMMENT '微信号',
  source_channel VARCHAR(16)  NOT NULL COMMENT '来源渠道：闲鱼|微信朋友圈|朋友介绍|线下|直播|老客户复购|其他',
  introducer_id BIGINT UNSIGNED NULL COMMENT '介绍人 -> t_customer（来源渠道为朋友介绍时，UC-04）',
  pref_category VARCHAR(64)   NULL COMMENT '偏好品类',
  pref_size     VARCHAR(32)   NULL COMMENT '偏好圈口/尺寸',
  pref_budget   DECIMAL(12,2) NULL COMMENT '偏好预算',
  remark        VARCHAR(500)  NULL COMMENT '备注（含跟进记录摘要）',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_customer_no (customer_no),
  UNIQUE KEY uk_customer_phone (phone),
  KEY idx_customer_introducer (introducer_id),
  CONSTRAINT fk_customer_introducer FOREIGN KEY (introducer_id) REFERENCES t_customer (id) ON DELETE SET NULL
) ENGINE=InnoDB COMMENT='客户（UC-04；标签见 t_customer_tag，介绍人自引用）';

-- 预约看货（UC-10 预约查看货品）
CREATE TABLE t_reservation (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  resv_no      VARCHAR(32)  NOT NULL COMMENT '预约编号',
  goods_id     BIGINT UNSIGNED NOT NULL COMMENT '货品 -> t_goods（预约查看的目标）',
  name         VARCHAR(64)  NOT NULL COMMENT '姓名',
  phone        VARCHAR(32)  NOT NULL COMMENT '手机号',
  ip           VARCHAR(45)  NULL COMMENT '提交来源 IP（限流与审计）',
  expect_slot  DATETIME     NOT NULL COMMENT '到店时间（半小时对齐）',
  status       VARCHAR(16)  NOT NULL DEFAULT '待确认' COMMENT '状态：待确认|已到店|已取消|已失效',
  exempt       TINYINT      NOT NULL DEFAULT 0 COMMENT '违约是否已豁免：0 否 1 是',
  exempt_by    BIGINT UNSIGNED NULL COMMENT '解除操作人 -> t_user',
  exempt_at    DATETIME     NULL COMMENT '解除时间',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_reservation_no (resv_no),
  -- 部分唯一索引：只在「待确认」时生效，同时兜住「整件货独占」与「同一时段独占」
  UNIQUE KEY uk_resv_slot_active ((CASE WHEN status = '待确认' THEN expect_slot END)),
  UNIQUE KEY uk_resv_goods_active ((CASE WHEN status = '待确认' THEN goods_id END)),
  KEY idx_reservation_goods (goods_id),
  KEY idx_resv_slot (expect_slot),
  KEY idx_resv_ip (ip),
  CONSTRAINT fk_reservation_goods FOREIGN KEY (goods_id) REFERENCES t_goods (id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='预约看货（UC-10；UC-27 凭手机号后四位查询）';

-- 预定记录（UC-13 预定商品；与预留不同：锁定购买意向，一手机号 3 次额度）
CREATE TABLE t_booking (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  booking_no   VARCHAR(32)  NOT NULL COMMENT '预定编号',
  goods_id     BIGINT UNSIGNED NOT NULL COMMENT '货品 -> t_goods',
  name         VARCHAR(64)  NOT NULL COMMENT '姓名',
  phone        VARCHAR(32)  NOT NULL COMMENT '手机号（3 次额度按此计）',
  ip           VARCHAR(45)  NULL COMMENT '来源 IP（防刷限流，口径同预约）',
  customer_id  BIGINT UNSIGNED NULL COMMENT '客户 -> t_customer（已建档客户按手机号关联，散客可空）',
  submit_time  DATETIME     NOT NULL COMMENT '提交时间',
  contact_time DATETIME     NULL     COMMENT '联系时间（UC-28 标记已联系时写入）',
  status       VARCHAR(16)  NOT NULL DEFAULT '待到店' COMMENT '状态：待到店|已联系|已达成|已爽约|已取消',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_booking_no (booking_no),
  KEY idx_booking_goods (goods_id),
  KEY idx_booking_phone (phone),
  KEY idx_booking_customer (customer_id),
  KEY idx_booking_ip (ip),
  UNIQUE KEY uk_booking_goods_active ((case when status in ('待到店','已联系') then goods_id end)),
  CONSTRAINT fk_booking_goods    FOREIGN KEY (goods_id)    REFERENCES t_goods (id)    ON DELETE RESTRICT,
  CONSTRAINT fk_booking_customer FOREIGN KEY (customer_id) REFERENCES t_customer (id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='预定记录（UC-13 预定、UC-28 到店跟进，状态机见规约；区别于 UC-01 规则③ 预留）';

-- =====================================================================
-- 四、送检与证书（UC-05 登记送检与证书）
-- =====================================================================

-- 送检记录
CREATE TABLE t_inspection (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  insp_time    DATETIME     NOT NULL COMMENT '送检时间',
  goods_id     BIGINT UNSIGNED NOT NULL COMMENT '货品 -> t_goods',
  org_id       BIGINT UNSIGNED NOT NULL COMMENT '送检机构 -> t_cert_org',
  submitter_id BIGINT UNSIGNED NOT NULL COMMENT '送检人 -> t_user',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_inspection_goods (goods_id),
  KEY idx_inspection_org (org_id),
  KEY idx_inspection_submitter (submitter_id),
  CONSTRAINT fk_inspection_goods   FOREIGN KEY (goods_id)     REFERENCES t_goods (id)     ON DELETE RESTRICT,
  CONSTRAINT fk_inspection_org     FOREIGN KEY (org_id)       REFERENCES t_cert_org (id)  ON DELETE RESTRICT,
  CONSTRAINT fk_inspection_submit  FOREIGN KEY (submitter_id) REFERENCES t_user (id)      ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='送检记录（UC-05）';

-- 鉴定证书（机构印制，扫码可查线上证书）
CREATE TABLE t_certificate (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  cert_no       VARCHAR(64)  NOT NULL COMMENT '证书编号',
  org_id        BIGINT UNSIGNED NOT NULL COMMENT '鉴定机构 -> t_cert_org',
  insp_id       BIGINT UNSIGNED NULL COMMENT '送检记录 -> t_inspection（证书由送检出具，随货品带入的证书可空）',
  goods_id      BIGINT UNSIGNED NOT NULL COMMENT '货品 -> t_goods（证书挂到货品）',
  conclusion    VARCHAR(64)  NOT NULL COMMENT '检测结论',
  issue_date    DATE         NOT NULL COMMENT '签发日期',
  cert_qrcode   VARCHAR(500) NULL COMMENT '证书二维码（机构印制，扫码可查）',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_certificate_no (cert_no),
  KEY idx_certificate_goods (goods_id),
  KEY idx_certificate_org (org_id),
  KEY idx_certificate_insp (insp_id),
  CONSTRAINT fk_certificate_org   FOREIGN KEY (org_id)   REFERENCES t_cert_org (id) ON DELETE RESTRICT,
  CONSTRAINT fk_certificate_insp  FOREIGN KEY (insp_id)  REFERENCES t_inspection (id) ON DELETE RESTRICT,
  CONSTRAINT fk_certificate_goods FOREIGN KEY (goods_id) REFERENCES t_goods (id)    ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='鉴定证书（UC-05）';

-- =====================================================================
-- 五、销售与售后（UC-01 登记成交 / UC-03 登记退货 / UC-24 分期 / UC-25 还款）
-- =====================================================================

-- 成交记录（一物一码，一件货品同时只有一条有效成交记录）
CREATE TABLE t_sale (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  sale_no       VARCHAR(32)   NOT NULL COMMENT '成交编号',
  goods_id      BIGINT UNSIGNED NOT NULL COMMENT '货品 -> t_goods',
  sale_time     DATETIME      NOT NULL COMMENT '成交发生时间（以系统时间为准）',
  price         DECIMAL(12,2) NOT NULL COMMENT '成交价（不得低于最低售价）',
  pay_method    VARCHAR(8)    NOT NULL COMMENT '支付方式：全款|定金',
  received      DECIMAL(12,2) NOT NULL COMMENT '已收金额（定金成交时为定金额）',
  customer_id   BIGINT UNSIGNED NULL COMMENT '客户 -> t_customer（散客可空）',
  operator_id   BIGINT UNSIGNED NOT NULL COMMENT '经手销售 -> t_user',
  recorded_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '录入时间（与发生时间分别记录）',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sale_no (sale_no),
  KEY idx_sale_goods (goods_id),
  KEY idx_sale_customer (customer_id),
  KEY idx_sale_operator (operator_id),
  CONSTRAINT fk_sale_goods    FOREIGN KEY (goods_id)    REFERENCES t_goods (id)    ON DELETE RESTRICT,
  CONSTRAINT fk_sale_customer FOREIGN KEY (customer_id) REFERENCES t_customer (id) ON DELETE RESTRICT,
  CONSTRAINT fk_sale_operator FOREIGN KEY (operator_id) REFERENCES t_user (id)     ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='成交记录（UC-01，UC-06 统计的数据源；分期见 t_installment）';

-- 退货记录
CREATE TABLE t_return (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  return_time   DATETIME     NOT NULL COMMENT '退货时间（成交后 7 天内）',
  sale_id       BIGINT UNSIGNED NOT NULL COMMENT '原成交记录 -> t_sale',
  reason        VARCHAR(16)  NOT NULL COMMENT '退货原因：真假存疑|质量问题',
  refund        DECIMAL(12,2) NOT NULL COMMENT '退款金额',
  operator_id   BIGINT UNSIGNED NOT NULL COMMENT '经手人 -> t_user',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_return_sale (sale_id),
  KEY idx_return_operator (operator_id),
  CONSTRAINT fk_return_sale     FOREIGN KEY (sale_id)     REFERENCES t_sale (id)  ON DELETE RESTRICT,
  CONSTRAINT fk_return_operator FOREIGN KEY (operator_id) REFERENCES t_user (id)  ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='退货记录（UC-03，冲减销售统计）';

-- 分期计划（UC-24 登记 / UC-25 记录还款；一成交至多一份计划）
CREATE TABLE t_installment (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  plan_no       VARCHAR(32)   NOT NULL COMMENT '计划编号',
  sale_id       BIGINT UNSIGNED NOT NULL COMMENT '成交记录 -> t_sale（总额=成交价，业务层把关）',
  periods       INT           NOT NULL COMMENT '分期期数',
  period_amount DECIMAL(12,2) NOT NULL COMMENT '每期应还金额',
  paid_amount   DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '已还金额',
  next_due_date DATE          NOT NULL COMMENT '下一期到期日（超过未结清即逾期）',
  start_date    DATE          NOT NULL COMMENT '计划生效日',
  status        VARCHAR(8)    NOT NULL DEFAULT '未还' COMMENT '状态：未还|逾期|已结清（状态机见 07f）',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_installment_no (plan_no),
  KEY idx_installment_sale (sale_id),
  CONSTRAINT fk_installment_sale FOREIGN KEY (sale_id) REFERENCES t_sale (id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='分期计划（UC-24/UC-25，大额成交分期付款）';

-- =====================================================================
-- 六、同行调货（UC-20 维护调货记录 / UC-23 结算调货台账）
-- =====================================================================

-- 调货记录（调入/调出，货权随状态转移）
CREATE TABLE t_transfer (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  record_no    VARCHAR(32)  NOT NULL COMMENT '记录编号',
  direction    VARCHAR(8)   NOT NULL COMMENT '方向：调入|调出',
  merchant_id  BIGINT UNSIGNED NOT NULL COMMENT '合作商户 -> t_source_party（类别=同行商户）',
  goods_id     BIGINT UNSIGNED NOT NULL COMMENT '货品 -> t_goods',
  quantity     INT          NOT NULL DEFAULT 1 COMMENT '数量',
  status       VARCHAR(8)   NOT NULL DEFAULT '在调' COMMENT '状态：在调|已售|已退回',
  settled      VARCHAR(4)   NOT NULL DEFAULT '否' COMMENT '已结算：否|是（UC-23 台账结算后置是）',
  record_time  DATETIME     NOT NULL COMMENT '时间（鉴赏期结算时点见 UC-20 规则③）',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_transfer_no (record_no),
  KEY idx_transfer_merchant (merchant_id),
  KEY idx_transfer_goods (goods_id),
  UNIQUE KEY uk_transfer_goods_open ((case when settled = '否' then goods_id end)),
  KEY idx_transfer_settled (settled),
  CONSTRAINT fk_transfer_merchant FOREIGN KEY (merchant_id) REFERENCES t_source_party (id) ON DELETE RESTRICT,
  CONSTRAINT fk_transfer_goods    FOREIGN KEY (goods_id)    REFERENCES t_goods (id)       ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='调货记录（UC-20，第一痛点，调货库存一本账；结算见 t_settlement）';

-- =====================================================================
-- 七、供应商出让与审核（UC-14 提交出让申请 / UC-18 审核出让申请）
-- =====================================================================

-- 出让申请（含审核字段）
CREATE TABLE t_transfer_apply (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  apply_no     VARCHAR(32)  NOT NULL COMMENT '申请编号',
  supplier_id  BIGINT UNSIGNED NOT NULL COMMENT '供应商 -> t_source_party（类别=供应商，含回流出让方）',
  out_type     VARCHAR(8)   NOT NULL COMMENT '出让类型：玉料|成品|回流',
  name         VARCHAR(128) NOT NULL COMMENT '品名',
  quantity     INT          NOT NULL COMMENT '数量',
  expect_price DECIMAL(12,2) NOT NULL COMMENT '期望出让价',
  description  VARCHAR(500) NULL COMMENT '描述',
  submit_time  DATETIME     NOT NULL COMMENT '提交时间',
  status       VARCHAR(8)   NOT NULL DEFAULT '待审核' COMMENT '状态：待审核|已通过|已驳回（状态机见 07b）',
  audit_opinion VARCHAR(255) NULL COMMENT '审核意见（UC-18）',
  audit_time   DATETIME     NULL COMMENT '审核时间（UC-18）',
  auditor_id   BIGINT UNSIGNED NULL COMMENT '审核人 -> t_user（UC-18）',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_transfer_apply_no (apply_no),
  KEY idx_transfer_apply_supplier (supplier_id),
  KEY idx_transfer_apply_auditor (auditor_id),
  CONSTRAINT fk_transfer_apply_supplier FOREIGN KEY (supplier_id) REFERENCES t_source_party (id) ON DELETE RESTRICT,
  CONSTRAINT fk_transfer_apply_auditor  FOREIGN KEY (auditor_id)  REFERENCES t_user (id)         ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='出让申请含审核（UC-14/UC-18）';

-- =====================================================================
-- 八、加工与上门（UC-15 预约上门 / UC-16 查看加工任务 / UC-17 提交完工交付申请）
-- =====================================================================

-- 加工任务
CREATE TABLE t_process_task (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  task_no       VARCHAR(32)  NOT NULL COMMENT '任务编号',
  goods_id      BIGINT UNSIGNED NOT NULL COMMENT '货品 -> t_goods（加工对象）',
  craftsman_id  BIGINT UNSIGNED NOT NULL COMMENT '加工师傅 -> t_source_party（类别=加工师傅，分派对象）',
  requirement   VARCHAR(500) NOT NULL COMMENT '加工要求',
  labor_fee     DECIMAL(12,2) NOT NULL COMMENT '工费',
  deadline      DATE         NOT NULL COMMENT '期限',
  status        VARCHAR(8)   NOT NULL DEFAULT '待加工' COMMENT '状态：待加工|在加工|待交付|已交付',
  urge_count    INT          NOT NULL DEFAULT 0 COMMENT '门店催办次数（「逾期」为派生态：待加工/在加工且过期限，不落库）',
  last_urge_at  DATETIME     NULL COMMENT '最近一次催办时间，师傅端据此提示',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_process_task_no (task_no),
  KEY idx_process_task_goods (goods_id),
  KEY idx_process_task_craftsman (craftsman_id),
  CONSTRAINT fk_process_task_goods     FOREIGN KEY (goods_id)     REFERENCES t_goods (id)        ON DELETE RESTRICT,
  CONSTRAINT fk_process_task_craftsman FOREIGN KEY (craftsman_id) REFERENCES t_source_party (id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='加工任务（UC-16）';

-- 完工交付申请
CREATE TABLE t_delivery_apply (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  apply_no     VARCHAR(32)  NOT NULL COMMENT '申请编号',
  task_id      BIGINT UNSIGNED NOT NULL COMMENT '任务编号 -> t_process_task',
  note         VARCHAR(500) NULL COMMENT '完工说明',
  submit_time  DATETIME     NOT NULL COMMENT '提交时间',
  status       VARCHAR(8)   NOT NULL DEFAULT '待确认' COMMENT '状态：待确认|已确认（确认后安排入库）',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_delivery_apply_no (apply_no),
  KEY idx_delivery_apply_task (task_id),
  CONSTRAINT fk_delivery_apply_task FOREIGN KEY (task_id) REFERENCES t_process_task (id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='完工交付申请（UC-17）';

-- 上门预约（置于完工交付申请之后，保证外键引用的表已存在）
CREATE TABLE t_visit_appt (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  appt_no      VARCHAR(32)  NOT NULL COMMENT '预约编号',
  appt_party   VARCHAR(128) NOT NULL COMMENT '预约方（供应商/加工师傅）',
  matter       VARCHAR(255) NOT NULL COMMENT '事项（上门交接内容）',
  expect_date  DATE         NOT NULL COMMENT '期望上门日期（UC-15 规则②：原来是自由文本，无法判重与比对，2026-10-26 拆成结构化字段）',
  start_time   TIME         NOT NULL COMMENT '期望时段起（10:00–19:00，半小时对齐）',
  end_time     TIME         NOT NULL COMMENT '期望时段止（服务端按 start_time + 1 小时写入，不接受客户端传值）',
  submit_time  DATETIME     NOT NULL COMMENT '提交时间',
  status       VARCHAR(8)   NOT NULL DEFAULT '待上门' COMMENT '状态：待上门|已完成|已取消',
  transfer_apply_id BIGINT UNSIGNED NULL COMMENT '关联出让申请 -> t_transfer_apply（UC-15 规则①，二选一必有其一）',
  delivery_apply_id BIGINT UNSIGNED NULL COMMENT '关联完工交付申请 -> t_delivery_apply（UC-15 规则①，二选一必有其一）',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  -- 同一时段只安排一次交接：整段完全相同的并发重复提交由这个函数唯一索引兜底，
  -- 区间部分重叠（同日 10:00-11:00 与 10:30-11:30）索引表达不了，在服务层判重
  UNIQUE KEY uk_visit_slot_open ((case when status = '待上门' then concat(expect_date, '#', start_time, '#', end_time) end)),
  UNIQUE KEY uk_visit_appt_no (appt_no),
  KEY idx_visit_expect (expect_date, status),
  KEY idx_visit_transfer (transfer_apply_id),
  KEY idx_visit_delivery (delivery_apply_id),
  CONSTRAINT fk_visit_transfer FOREIGN KEY (transfer_apply_id) REFERENCES t_transfer_apply (id) ON DELETE RESTRICT,
  CONSTRAINT fk_visit_delivery FOREIGN KEY (delivery_apply_id) REFERENCES t_delivery_apply (id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='上门预约（UC-15，规则①要求关联已通过的出让申请或已确认的完工交付申请）';

-- =====================================================================
-- 九、货款往来（UC-19 登记货款记录）
-- =====================================================================

-- 货款记录（供货货款/调货货款/加工工费；关联单据用逻辑外键，避免多态硬外键）
CREATE TABLE t_payment (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  record_no     VARCHAR(32)   NOT NULL COMMENT '记录编号',
  biz_type      VARCHAR(16)   NOT NULL COMMENT '业务类型：采购货款|调货货款|加工工费|寄售货款',
  related_type  VARCHAR(32)   NOT NULL COMMENT '关联单据类型（入库批次/调货记录/加工任务/台账结算）',
  related_no    VARCHAR(32)   NOT NULL COMMENT '关联单据编号（逻辑引用，不建硬外键）',
  amount        DECIMAL(12,2) NOT NULL COMMENT '金额',
  direction     VARCHAR(8)    NOT NULL COMMENT '收付方向：支付|收取',
  operator_id   BIGINT UNSIGNED NOT NULL COMMENT '经手人 -> t_user',
  pay_time      DATETIME      NOT NULL COMMENT '时间',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_payment_no (record_no),
  KEY idx_payment_related (related_type, related_no),
  KEY idx_payment_operator (operator_id),
  CONSTRAINT fk_payment_operator FOREIGN KEY (operator_id) REFERENCES t_user (id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='货款记录（UC-19，货款一本账，资金流水；结算凭证见 t_settlement）';

-- =====================================================================
-- 十、寄售与台账结算（UC-21 登记寄售合同 / UC-22 结算寄售货款 / UC-23 结算调货台账）
-- =====================================================================

-- 寄售合同（供应商寄存货品，售出后按寄售价结算）
CREATE TABLE t_consignment (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  consign_no   VARCHAR(32)  NOT NULL COMMENT '合同编号',
  supplier_id  BIGINT UNSIGNED NOT NULL COMMENT '寄售供应商 -> t_source_party（类别=供应商）',
  sign_time    DATETIME     NOT NULL COMMENT '签订时间',
  expire_date  DATE         NOT NULL COMMENT '到期日（到期未终止即置已到期）',
  settle_cycle VARCHAR(16)  NOT NULL DEFAULT '售出即结' COMMENT '结算方式：售出即结|按月结算',
  status       VARCHAR(8)   NOT NULL DEFAULT '生效' COMMENT '状态：生效|已终止|已到期（状态机见 07e）',
  remark       VARCHAR(255) NULL COMMENT '备注',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_consignment_no (consign_no),
  KEY idx_consignment_supplier (supplier_id),
  CONSTRAINT fk_consignment_supplier FOREIGN KEY (supplier_id) REFERENCES t_source_party (id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='寄售合同（UC-21，寄售入库与结算的依据）';

-- 寄售明细（一份合同带多件货品，多值拆表；组合外键非空）
CREATE TABLE t_consignment_item (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  consignment_id BIGINT UNSIGNED NOT NULL COMMENT '寄售合同 -> t_consignment',
  goods_id       BIGINT UNSIGNED NOT NULL COMMENT '货品 -> t_goods（入库来源类型=寄售，成本=寄售价）',
  consign_price  DECIMAL(12,2) NOT NULL COMMENT '寄售价（售出后按此价与供应商结算）',
  settle_status  VARCHAR(8)    NOT NULL DEFAULT '未结算' COMMENT '结算状态：未结算|已结算（UC-22）',
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_consignment_item_goods (goods_id),
  KEY idx_consignment_item_consignment (consignment_id),
  CONSTRAINT fk_consignment_item_consignment FOREIGN KEY (consignment_id) REFERENCES t_consignment (id) ON DELETE CASCADE,
  CONSTRAINT fk_consignment_item_goods       FOREIGN KEY (goods_id)       REFERENCES t_goods (id)       ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='寄售明细（UC-21，一合同多件货品；一货品在档至多一条明细，重复寄售前须结清原合同）';

-- =====================================================================
-- 十一、台账结算（UC-22 寄售货款结算 / UC-23 调货台账结算）
-- =====================================================================

-- 台账结算（一单一据：调货记录与寄售明细二选一必有其一，业务层把关）
CREATE TABLE t_settlement (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  settle_no       VARCHAR(32)   NOT NULL COMMENT '结算编号',
  party_id        BIGINT UNSIGNED NOT NULL COMMENT '结算往来方 -> t_source_party',
  settle_type     VARCHAR(8)    NOT NULL COMMENT '结算类型：调货|寄售',
  transfer_id     BIGINT UNSIGNED NULL COMMENT '调货记录 -> t_transfer（结算类型=调货时非空）',
  consign_item_id BIGINT UNSIGNED NULL COMMENT '寄售明细 -> t_consignment_item（结算类型=寄售时非空）',
  amount          DECIMAL(12,2) NOT NULL COMMENT '结算金额',
  settle_method   VARCHAR(16)   NOT NULL COMMENT '结算方式：现金|转账|冲抵',
  settle_time     DATETIME      NOT NULL COMMENT '结算时间',
  operator_id     BIGINT UNSIGNED NOT NULL COMMENT '经手人 -> t_user',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_settlement_no (settle_no),
  KEY idx_settlement_party (party_id),
  KEY idx_settlement_transfer (transfer_id),
  KEY idx_settlement_consign_item (consign_item_id),
  KEY idx_settlement_operator (operator_id),
  CONSTRAINT fk_settlement_party    FOREIGN KEY (party_id)        REFERENCES t_source_party (id)      ON DELETE RESTRICT,
  CONSTRAINT fk_settlement_transfer FOREIGN KEY (transfer_id)     REFERENCES t_transfer (id)          ON DELETE RESTRICT,
  CONSTRAINT fk_settlement_consign  FOREIGN KEY (consign_item_id) REFERENCES t_consignment_item (id)  ON DELETE RESTRICT,
  CONSTRAINT fk_settlement_operator FOREIGN KEY (operator_id)     REFERENCES t_user (id)              ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='台账结算（UC-22/UC-23，结算后单据置已结算；资金流水另见 t_payment）';

-- =====================================================================
-- 十二、库存流水（UC-26 查看库存流水）
-- =====================================================================

-- 库存流水（货品每次状态变更必写一条，写入后不可改不可删）
CREATE TABLE t_stock_log (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  goods_id    BIGINT UNSIGNED NOT NULL COMMENT '货品 -> t_goods',
  from_status VARCHAR(16)  NULL COMMENT '变更前状态（入库为第一条流水时为空）',
  to_status   VARCHAR(16)  NOT NULL COMMENT '变更后状态（取值同 t_goods.status）',
  ref_type    VARCHAR(16)  NULL COMMENT '关联单据类型（成交/退货/调货记录/加工任务/送检记录/入库批次/寄售合同）',
  ref_no      VARCHAR(32)  NULL COMMENT '关联单据编号（逻辑引用，同 t_payment 口径）',
  operator_id BIGINT UNSIGNED NOT NULL COMMENT '经手人 -> t_user',
  log_time    DATETIME     NOT NULL COMMENT '流水时间（系统时间）',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_stock_log_goods (goods_id),
  KEY idx_stock_log_time (log_time),
  KEY idx_stock_log_operator (operator_id),
  CONSTRAINT fk_stock_log_goods    FOREIGN KEY (goods_id)    REFERENCES t_goods (id) ON DELETE RESTRICT,
  CONSTRAINT fk_stock_log_operator FOREIGN KEY (operator_id) REFERENCES t_user (id)  ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='库存流水（UC-26，状态变更必写、不可改不可删，调货库存一本账的账本）';

-- =====================================================================
-- 多值属性拆表与多对多拆表（组合外键非空，删主记录连带删明细）
-- =====================================================================

-- 客户标签（多选标签独立成表，满足第一范式：原子属性）
CREATE TABLE t_customer_tag (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  customer_id BIGINT UNSIGNED NOT NULL COMMENT '客户 -> t_customer',
  tag         VARCHAR(16)  NOT NULL COMMENT '标签：大客户|懂行|爽快|要还价',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_customer_tag (customer_id, tag),
  CONSTRAINT fk_customer_tag_customer FOREIGN KEY (customer_id) REFERENCES t_customer (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='客户标签（UC-04，原逗号分隔字段拆表）';

-- 出让申请图片（多图独立成表）
CREATE TABLE t_apply_image (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  apply_id   BIGINT UNSIGNED NOT NULL COMMENT '出让申请 -> t_transfer_apply',
  seq        INT          NOT NULL COMMENT '显示顺序',
  image_path VARCHAR(255) NOT NULL COMMENT '图片路径',
  PRIMARY KEY (id),
  UNIQUE KEY uk_apply_image_seq (apply_id, seq),
  CONSTRAINT fk_apply_image_apply FOREIGN KEY (apply_id) REFERENCES t_transfer_apply (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='出让申请图片（UC-14，原逗号分隔字段拆表）';

-- 完工交付过程图（加工前/加工中/完工各一张，独立成表）
CREATE TABLE t_delivery_image (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  apply_id   BIGINT UNSIGNED NOT NULL COMMENT '完工交付申请 -> t_delivery_apply',
  stage      VARCHAR(16)  NOT NULL COMMENT '阶段：加工前|加工中|完工',
  image_path VARCHAR(255) NOT NULL COMMENT '图片路径',
  PRIMARY KEY (id),
  UNIQUE KEY uk_delivery_image_stage (apply_id, stage),
  CONSTRAINT fk_delivery_image_apply FOREIGN KEY (apply_id) REFERENCES t_delivery_apply (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='完工交付过程图（UC-17，原逗号分隔字段拆表）';

-- 用户-角色（多对多拆表，组合外键非空）
-- 操作日志（管理端留痕）：登录后的改操作由 AuthInterceptor 自动记录，公开匿名接口不记
CREATE TABLE t_op_log (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  user_id    BIGINT UNSIGNED NULL COMMENT '操作人 -> t_user',
  username   VARCHAR(32)  NULL COMMENT '操作时账号快照',
  method     VARCHAR(8)   NOT NULL COMMENT 'HTTP 方法',
  path       VARCHAR(255) NOT NULL COMMENT '请求路径（含目标 id）',
  query      VARCHAR(200) NULL COMMENT '查询串',
  status     INT          NOT NULL COMMENT 'HTTP 状态码，4xx/5xx 即失败操作也留痕',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (id),
  KEY idx_op_log_user (user_id),
  KEY idx_op_log_time (created_at)
) ENGINE=InnoDB COMMENT='操作日志（UC-12 查阅）';

CREATE TABLE t_user_role (
  id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户 -> t_user',
  role_id BIGINT UNSIGNED NOT NULL COMMENT '角色 -> t_role',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES t_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES t_role (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='用户-角色（UC-12，多对多拆表，一用户可多角色）';

-- 角色-权限（多对多拆表，权限矩阵的物理落表）
CREATE TABLE t_role_permission (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  role_id       BIGINT UNSIGNED NOT NULL COMMENT '角色 -> t_role',
  permission_id BIGINT UNSIGNED NOT NULL COMMENT '权限 -> t_permission',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_permission (role_id, permission_id),
  CONSTRAINT fk_role_perm_role FOREIGN KEY (role_id)       REFERENCES t_role (id)       ON DELETE CASCADE,
  CONSTRAINT fk_role_perm_perm FOREIGN KEY (permission_id) REFERENCES t_permission (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='角色-权限（多对多拆表，一角色多权限）';

-- =====================================================================
-- 结束。共 32 张表：基础参照 6 + 业务 19 + 拆表 6（多值属性 4 + 多对多 2）+ 运维 1（t_op_log 操作留痕，2026-10-26 增，论文 ERD 未含）。
-- =====================================================================
