-- =====================================================================
-- 玉器门店顾客服务与经营管理系统  数据库设计（D 阶段·数据层）
-- 数据库：db_jade    版本：MySQL 8.0
-- 字段取自各用例规约的「字段列表」，类-表 / 属性-列 / 对象-行 一一映射
-- 约定：
--   * 代理键 id（BIGINT，自增，唯一且恒定）作主键；业务编码单列并加唯一索引
--   * 外键放在“非 1”方（多方）。删除策略分三类：
--       - RESTRICT：业务行禁止物理删除（主体数据）
--       - CASCADE：三张多值拆表，删主记录连带删明细
--       - SET NULL：客户介绍人，介绍人删除后该列置空、客户记录保留
--   * 金额 DECIMAL(12,2)；克重 DECIMAL(10,2)；尺寸/圈口/料种等枚举用 VARCHAR 并存注释
--   * 每个表、每列均带中文 COMMENT；状态字段注释里列全取值（照 stage-AB 原样）
-- =====================================================================

DROP DATABASE IF EXISTS db_jade;
CREATE DATABASE db_jade DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE db_jade;

-- =====================================================================
-- 一、基础参照表（UC-07 维护基础数据、UC-08 登录）
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

-- 用户（含外部账户，UC-08 登录 / UC-12 开通外部账户）
CREATE TABLE t_user (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  username      VARCHAR(64)  NOT NULL COMMENT '用户名（登录名）',
  name          VARCHAR(64)  NOT NULL COMMENT '姓名',
  password_hash VARCHAR(128) NOT NULL COMMENT '口令（加密保存，禁止明文）',
  role          VARCHAR(16)  NOT NULL COMMENT '角色：老板|销售|合作商户|供应商|加工师傅',
  status        VARCHAR(8)   NOT NULL DEFAULT '启用' COMMENT '状态：启用|停用',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username)
) ENGINE=InnoDB COMMENT='用户与外部账户（UC-08/UC-12）';

-- 供应商（UC-07）
CREATE TABLE t_supplier (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  name           VARCHAR(128) NOT NULL COMMENT '供应商名称',
  contact_person VARCHAR(64)  NULL     COMMENT '联系人',
  phone          VARCHAR(32)  NULL     COMMENT '联系电话',
  remark         VARCHAR(255) NULL     COMMENT '备注',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_supplier_name (name)
) ENGINE=InnoDB COMMENT='供应商（UC-07）';

-- 加工师傅（UC-07）
CREATE TABLE t_craftsman (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  name           VARCHAR(64)  NOT NULL COMMENT '师傅姓名',
  contact_person VARCHAR(64)  NULL     COMMENT '联系人',
  phone          VARCHAR(32)  NULL     COMMENT '联系电话',
  remark         VARCHAR(255) NULL     COMMENT '备注',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_craftsman_name (name)
) ENGINE=InnoDB COMMENT='加工师傅（UC-07）';

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

-- 同行商户（UC-07，调货往来对象）
CREATE TABLE t_merchant (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  name           VARCHAR(128) NOT NULL COMMENT '同行商户名称',
  contact_person VARCHAR(64)  NULL     COMMENT '联系人',
  phone          VARCHAR(32)  NULL     COMMENT '联系电话',
  remark         VARCHAR(255) NULL     COMMENT '备注',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_merchant_name (name)
) ENGINE=InnoDB COMMENT='同行商户（UC-07）';

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
  src_type      VARCHAR(16)   NOT NULL COMMENT '来源类型：采购|回收|调货|加工完工',
  src_party     VARCHAR(128)  NULL COMMENT '来源方（供应商/商户/师傅名）',
  src_doc       VARCHAR(64)   NULL COMMENT '来源单据号',
  cost          DECIMAL(12,2) NULL COMMENT '成本（调货调入不记成本，只记往来）',
  min_price     DECIMAL(12,2) NOT NULL COMMENT '最低售价（成交价不得低于此价）',
  transfer_price DECIMAL(12,2) NULL COMMENT '调货价（UC-11 商户端调货价目，在柜时维护）',
  in_time       DATETIME      NOT NULL COMMENT '入库时间（系统时间）',
  stock_in_id   BIGINT UNSIGNED NOT NULL COMMENT '入库批次 -> t_stock_in（货品清单成员）',
  status        VARCHAR(16)   NOT NULL DEFAULT '在柜' COMMENT '状态：在柜|已送检|预留|已售|已订|已调出|代销中|待处理',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_goods_no (goods_no),
  KEY idx_goods_category (category_id),
  KEY idx_goods_stock_in (stock_in_id),
  KEY idx_goods_status (status),
  CONSTRAINT fk_goods_category  FOREIGN KEY (category_id)  REFERENCES t_category (id)  ON DELETE RESTRICT,
  CONSTRAINT fk_goods_stock_in  FOREIGN KEY (stock_in_id)  REFERENCES t_stock_in (id)  ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='货品档案（UC-01/UC-02，状态机见 07a）';

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
  KEY idx_customer_phone (phone),
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
  expect_slot  VARCHAR(64)  NOT NULL COMMENT '期望到店时段',
  status       VARCHAR(16)  NOT NULL DEFAULT '待确认' COMMENT '状态：待确认|已到店|已取消',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_reservation_no (resv_no),
  KEY idx_reservation_goods (goods_id),
  CONSTRAINT fk_reservation_goods FOREIGN KEY (goods_id) REFERENCES t_goods (id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='预约看货（UC-10）';

-- 预定记录（UC-13 预定商品；与预留不同：锁定购买意向，一手机号 3 次额度）
CREATE TABLE t_booking (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  booking_no   VARCHAR(32)  NOT NULL COMMENT '预定编号',
  goods_id     BIGINT UNSIGNED NOT NULL COMMENT '货品 -> t_goods',
  name         VARCHAR(64)  NOT NULL COMMENT '姓名',
  phone        VARCHAR(32)  NOT NULL COMMENT '手机号（3 次额度按此计）',
  customer_id  BIGINT UNSIGNED NULL COMMENT '客户 -> t_customer（已建档客户按手机号关联，散客可空）',
  submit_time  DATETIME     NOT NULL COMMENT '提交时间',
  status       VARCHAR(16)  NOT NULL DEFAULT '待到店' COMMENT '状态：待到店|已达成|已爽约|已取消',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_booking_no (booking_no),
  KEY idx_booking_goods (goods_id),
  KEY idx_booking_phone (phone),
  KEY idx_booking_customer (customer_id),
  CONSTRAINT fk_booking_goods    FOREIGN KEY (goods_id)    REFERENCES t_goods (id)    ON DELETE RESTRICT,
  CONSTRAINT fk_booking_customer FOREIGN KEY (customer_id) REFERENCES t_customer (id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='预定记录（UC-13，状态机见规约；区别于 UC-01 规则③ 预留）';

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
-- 五、销售与售后（UC-01 登记成交 / UC-03 登记退货）
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
) ENGINE=InnoDB COMMENT='成交记录（UC-01，UC-06 统计的数据源）';

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
  CONSTRAINT fk_return_sale    FOREIGN KEY (sale_id)     REFERENCES t_sale (id)   ON DELETE RESTRICT,
  CONSTRAINT fk_return_operator FOREIGN KEY (operator_id) REFERENCES t_user (id)  ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='退货记录（UC-03，冲减销售统计）';

-- =====================================================================
-- 六、同行调货（UC-20 维护调货记录）
-- =====================================================================

-- 调货记录（调入/调出，货权随状态转移）
CREATE TABLE t_transfer (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  record_no    VARCHAR(32)  NOT NULL COMMENT '记录编号',
  direction    VARCHAR(8)   NOT NULL COMMENT '方向：调入|调出',
  merchant_id  BIGINT UNSIGNED NOT NULL COMMENT '合作商户 -> t_merchant',
  goods_id     BIGINT UNSIGNED NOT NULL COMMENT '货品 -> t_goods',
  quantity     INT          NOT NULL DEFAULT 1 COMMENT '数量',
  status       VARCHAR(8)   NOT NULL DEFAULT '在调' COMMENT '状态：在调|已售|已退回',
  record_time  DATETIME     NOT NULL COMMENT '时间（鉴赏期结算时点见 UC-20 规则③）',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_transfer_no (record_no),
  KEY idx_transfer_merchant (merchant_id),
  KEY idx_transfer_goods (goods_id),
  CONSTRAINT fk_transfer_merchant FOREIGN KEY (merchant_id) REFERENCES t_merchant (id) ON DELETE RESTRICT,
  CONSTRAINT fk_transfer_goods    FOREIGN KEY (goods_id)    REFERENCES t_goods (id)    ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='调货记录（UC-20，第一痛点，调货库存一本账）';

-- =====================================================================
-- 七、供应商出让与审核（UC-14 提交出让申请 / UC-18 审核出让申请）
-- =====================================================================

-- 出让申请（含审核字段）
CREATE TABLE t_transfer_apply (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  apply_no     VARCHAR(32)  NOT NULL COMMENT '申请编号',
  supplier_id  BIGINT UNSIGNED NOT NULL COMMENT '供应商 -> t_supplier（含回流出让方）',
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
  CONSTRAINT fk_transfer_apply_supplier FOREIGN KEY (supplier_id) REFERENCES t_supplier (id) ON DELETE RESTRICT,
  CONSTRAINT fk_transfer_apply_auditor  FOREIGN KEY (auditor_id)  REFERENCES t_user (id)    ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='出让申请含审核（UC-14/UC-18）';

-- =====================================================================
-- 八、加工与上门（UC-15 预约上门 / UC-16 查看加工任务 / UC-17 提交完工交付申请）
-- =====================================================================

-- 上门预约
CREATE TABLE t_visit_appt (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  appt_no      VARCHAR(32)  NOT NULL COMMENT '预约编号',
  appt_party   VARCHAR(128) NOT NULL COMMENT '预约方（供应商/加工师傅）',
  matter       VARCHAR(255) NOT NULL COMMENT '事项（上门交接内容）',
  expect_slot  VARCHAR(64)  NOT NULL COMMENT '期望时段',
  submit_time  DATETIME     NOT NULL COMMENT '提交时间',
  status       VARCHAR(8)   NOT NULL DEFAULT '待上门' COMMENT '状态：待上门|已完成|已取消',
  transfer_apply_id BIGINT UNSIGNED NULL COMMENT '关联出让申请 -> t_transfer_apply（UC-15 规则①，二选一必有其一）',
  delivery_apply_id BIGINT UNSIGNED NULL COMMENT '关联完工交付申请 -> t_delivery_apply（UC-15 规则①，二选一必有其一）',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_visit_appt_no (appt_no),
  KEY idx_visit_transfer (transfer_apply_id),
  KEY idx_visit_delivery (delivery_apply_id),
  CONSTRAINT fk_visit_transfer FOREIGN KEY (transfer_apply_id) REFERENCES t_transfer_apply (id) ON DELETE RESTRICT,
  CONSTRAINT fk_visit_delivery FOREIGN KEY (delivery_apply_id) REFERENCES t_delivery_apply (id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='上门预约（UC-15，规则①要求关联已通过的出让申请或已确认的完工交付申请）';

-- 加工任务
CREATE TABLE t_process_task (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  task_no       VARCHAR(32)  NOT NULL COMMENT '任务编号',
  goods_id      BIGINT UNSIGNED NOT NULL COMMENT '货品 -> t_goods（加工对象）',
  craftsman_id  BIGINT UNSIGNED NOT NULL COMMENT '加工师傅 -> t_craftsman（分派对象）',
  requirement   VARCHAR(500) NOT NULL COMMENT '加工要求',
  labor_fee     DECIMAL(12,2) NOT NULL COMMENT '工费',
  deadline      DATE         NOT NULL COMMENT '期限',
  status        VARCHAR(8)   NOT NULL DEFAULT '待加工' COMMENT '状态：待加工|在加工|待交付|已交付',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_process_task_no (task_no),
  KEY idx_process_task_goods (goods_id),
  KEY idx_process_task_craftsman (craftsman_id),
  CONSTRAINT fk_process_task_goods     FOREIGN KEY (goods_id)     REFERENCES t_goods (id)     ON DELETE RESTRICT,
  CONSTRAINT fk_process_task_craftsman FOREIGN KEY (craftsman_id) REFERENCES t_craftsman (id) ON DELETE RESTRICT
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

-- =====================================================================
-- 九、货款往来（UC-19 登记货款记录）
-- =====================================================================

-- 货款记录（供货货款/调货货款/加工工费；关联单据用逻辑外键，避免多态硬外键）
CREATE TABLE t_payment (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '代理键',
  record_no     VARCHAR(32)   NOT NULL COMMENT '记录编号',
  biz_type      VARCHAR(16)   NOT NULL COMMENT '业务类型：采购货款|调货货款|加工工费',
  related_type  VARCHAR(32)   NOT NULL COMMENT '关联单据类型（入库批次/调货记录/加工任务）',
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
) ENGINE=InnoDB COMMENT='货款记录（UC-19，货款一本账）';

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

-- =====================================================================
-- 结束。共 24 张表：基础参照 6 + 业务 15 + 多值属性拆表 3。
-- =====================================================================
