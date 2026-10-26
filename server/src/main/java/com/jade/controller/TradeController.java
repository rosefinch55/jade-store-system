package com.jade.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jade.common.BizException;
import com.jade.common.CurrentUser;
import com.jade.common.RequirePerm;
import com.jade.common.IdGen;
import com.jade.common.Result;
import com.jade.entity.*;
import com.jade.mapper.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 同行往来、寄售结算、加工与上门。对应 specs/004/005/006。
 */
@RestController
@RequestMapping("/api")
public class TradeController {

    private final TransferMapper transferMapper;
    private final TransferApplyMapper applyMapper;
    private final SettlementMapper settlementMapper;
    private final ConsignmentMapper consignMapper;
    private final ConsignmentItemMapper itemMapper;
    private final CategoryMapper categoryMapper;
    private final ProcessTaskMapper taskMapper;
    private final DeliveryApplyMapper deliveryMapper;
    private final DeliveryImageMapper deliveryImageMapper;
    private final ReservationMapper reservationMapper;
    private final VisitApptMapper visitMapper;
    private final GoodsMapper goodsMapper;
    private final SourcePartyMapper partyMapper;
    private final SaleMapper saleMapper;
    private final PaymentMapper paymentMapper;
    private final StockLogMapper stockLogMapper;
    private final StockInMapper stockInMapper;
    private final UserMapper userMapper;

    public TradeController(TransferMapper transferMapper, TransferApplyMapper applyMapper,
                           SettlementMapper settlementMapper, ConsignmentMapper consignMapper,
                           ConsignmentItemMapper itemMapper, ProcessTaskMapper taskMapper,
                           DeliveryApplyMapper deliveryMapper, DeliveryImageMapper deliveryImageMapper,
                           VisitApptMapper visitMapper, GoodsMapper goodsMapper,
                           SourcePartyMapper partyMapper, SaleMapper saleMapper,
                           PaymentMapper paymentMapper, StockLogMapper stockLogMapper,
                           StockInMapper stockInMapper, UserMapper userMapper,
                           ReservationMapper reservationMapper, CategoryMapper categoryMapper) {
        this.transferMapper = transferMapper;
        this.applyMapper = applyMapper;
        this.settlementMapper = settlementMapper;
        this.consignMapper = consignMapper;
        this.itemMapper = itemMapper;
        this.categoryMapper = categoryMapper;
        this.taskMapper = taskMapper;
        this.deliveryMapper = deliveryMapper;
        this.deliveryImageMapper = deliveryImageMapper;
        this.reservationMapper = reservationMapper;
        this.visitMapper = visitMapper;
        this.goodsMapper = goodsMapper;
        this.partyMapper = partyMapper;
        this.saleMapper = saleMapper;
        this.paymentMapper = paymentMapper;
        this.stockLogMapper = stockLogMapper;
        this.stockInMapper = stockInMapper;
        this.userMapper = userMapper;
    }

    // ==================== 商户：调货价目 ====================

    @GetMapping("/merchant/price-list")
    @RequirePerm(value = "UC-11", roles = {"MERCHANT"})
    public Result<Map<String, Object>> priceList(@RequestParam(required = false) Long categoryId,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        if (size > 100) size = 100;
        QueryWrapper<Goods> q = new QueryWrapper<>();
        q.eq("status", "在柜").isNotNull("transfer_price");
        if (categoryId != null) q.eq("category_id", categoryId);
        if (keyword != null && !keyword.isBlank()) q.like("name", keyword);
        q.orderByDesc("id");
        Page<Goods> p = goodsMapper.selectPage(new Page<>(page, size), q);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Goods g : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("goodsNo", g.getGoodsNo());
            m.put("name", g.getName());
            m.put("shape", g.getShape());
            m.put("size", g.getSize());
            m.put("weightG", g.getWeightG());
            m.put("transferPrice", g.getTransferPrice());
            m.put("image", g.getImage());
            items.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", p.getTotal());
        data.put("page", page);
        data.put("size", size);
        data.put("items", items);
        return Result.ok(data);
    }

    /** 商户看单件详情：只给对外可见的字段，成本、最低售价、参考价一律不出库 */
    @GetMapping("/merchant/price-list/{goodsNo}")
    @RequirePerm(value = "UC-11", roles = {"MERCHANT"})
    public Result<Map<String, Object>> priceDetail(@PathVariable String goodsNo) {
        Goods g = goodsMapper.selectOne(new QueryWrapper<Goods>().eq("goods_no", goodsNo));
        if (g == null || !"在柜".equals(g.getStatus()) || g.getTransferPrice() == null) {
            throw BizException.notFound("该货品当前不可调");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("goodsNo", g.getGoodsNo());
        m.put("name", g.getName());
        m.put("categoryName", categoryName(g.getCategoryId()));
        m.put("material", g.getMaterial());
        m.put("origin", g.getOrigin());
        m.put("shape", g.getShape());
        m.put("form", g.getForm());
        m.put("weightG", g.getWeightG());
        m.put("size", g.getSize());
        m.put("certNo", g.getCertNo());
        m.put("image", g.getImage());
        m.put("transferPrice", g.getTransferPrice());
        return Result.ok(m);
    }

    private String categoryName(Long categoryId) {
        if (categoryId == null) return null;
        Category c = categoryMapper.selectById(categoryId);
        return c == null ? null : c.getName();
    }

    // ==================== 供应商：出让申请 ====================

    public static class ApplyReq {
        public String outType;
        public String name;
        public Integer quantity;
        public BigDecimal expectPrice;
        public String description;
    }

    @PostMapping("/supplier/applies")
    @RequirePerm(value = "UC-14", roles = {"SUPPLIER"})
    public Result<Map<String, Object>> submitApply(@RequestBody ApplyReq req) {
        if (!List.of("玉料", "成品", "回流").contains(req.outType)) {
            throw BizException.badRequest("出让类型不合法");
        }
        if (req.name == null || req.name.isBlank()) throw BizException.badRequest("品名不能为空");
        if (req.quantity == null || req.quantity <= 0) throw BizException.badRequest("数量必须大于 0");
        if (req.expectPrice == null) throw BizException.badRequest("期望出让价不能为空");

        TransferApply a = new TransferApply();
        a.setApplyNo(nextNo("CR"));
        a.setSupplierId(partyIdOfCurrentUser());
        a.setOutType(req.outType);
        a.setName(req.name);
        a.setQuantity(req.quantity);
        a.setExpectPrice(req.expectPrice);
        a.setDescription(req.description);
        a.setSubmitTime(LocalDateTime.now());
        a.setStatus("待审核");
        applyMapper.insert(a);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("applyNo", a.getApplyNo());
        data.put("status", a.getStatus());
        data.put("submitTime", a.getSubmitTime());
        return Result.ok(data);
    }

    @GetMapping("/supplier/applies")
    @RequirePerm(value = "UC-14", roles = {"SUPPLIER"})
    public Result<List<TransferApply>> myApplies() {
        Long pid = partyIdOfCurrentUser();
        return Result.ok(applyMapper.selectList(new QueryWrapper<TransferApply>()
                .eq("supplier_id", pid).orderByDesc("submit_time")));
    }

    // ==================== 内部：审核 ====================

    @RequirePerm(value = {}, roles = {"BOSS", "SALE"})
    @GetMapping("/transfer-applies")
    public Result<List<Map<String, Object>>> listApplies(@RequestParam(required = false) String status) {
        QueryWrapper<TransferApply> q = new QueryWrapper<>();
        if (status != null && !status.isBlank()) q.eq("status", status);
        q.orderByAsc("submit_time");
        List<TransferApply> list = applyMapper.selectList(q);
        List<Map<String, Object>> out = new ArrayList<>();
        for (TransferApply a : list) {
            SourceParty sp = partyMapper.selectById(a.getSupplierId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("applyNo", a.getApplyNo());
            m.put("supplierName", sp == null ? null : sp.getName());
            m.put("outType", a.getOutType());
            m.put("name", a.getName());
            m.put("quantity", a.getQuantity());
            // FR-011：期望出让价仅供审核（老板）参考，不对供应商之外的任何角色展示
            if (CurrentUser.get() != null && CurrentUser.get().isBoss()) {
                m.put("expectPrice", a.getExpectPrice());
            }
            m.put("description", a.getDescription());
            m.put("submitTime", a.getSubmitTime());
            m.put("status", a.getStatus());
            m.put("auditOpinion", a.getAuditOpinion());
            out.add(m);
        }
        return Result.ok(out);
    }

    public static class AuditReq {
        public Boolean pass;
        public String opinion;
    }

    @PostMapping("/transfer-applies/{id}/audit")
    @RequirePerm(value = "UC-18", roles = {"BOSS"})
    public Result<Void> audit(@PathVariable Long id, @RequestBody AuditReq req) {
        TransferApply a = applyMapper.selectById(id);
        if (a == null) throw BizException.notFound("申请不存在");
        if (!"待审核".equals(a.getStatus())) throw BizException.conflict("该申请已审核");
        if (!Boolean.TRUE.equals(req.pass) && (req.opinion == null || req.opinion.isBlank())) {
            throw BizException.badRequest("驳回必须填写原因");
        }
        a.setStatus(Boolean.TRUE.equals(req.pass) ? "已通过" : "已驳回");
        a.setAuditOpinion(req.opinion);
        a.setAuditTime(LocalDateTime.now());
        a.setAuditorId(CurrentUser.require().getUserId());
        applyMapper.updateById(a);
        return Result.ok();
    }

    // ==================== 内部：调货记录 ====================

    @RequirePerm(value = {}, roles = {"BOSS", "SALE"})
    @GetMapping("/transfers")
    public Result<Map<String, Object>> listTransfers(@RequestParam(required = false) Long merchantId,
                                                     @RequestParam(required = false) String settled,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        if (size > 100) size = 100;
        QueryWrapper<Transfer> q = new QueryWrapper<>();
        if (merchantId != null) q.eq("merchant_id", merchantId);
        if (settled != null && !settled.isBlank()) q.eq("settled", settled);
        q.orderByDesc("record_time");
        Page<Transfer> p = transferMapper.selectPage(new Page<>(page, size), q);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Transfer t : p.getRecords()) {
            items.add(transferVO(t));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", p.getTotal());
        data.put("page", page);
        data.put("size", size);
        data.put("items", items);
        return Result.ok(data);
    }

    public static class TransferReq {
        public String direction;
        public Long merchantId;
        public String goodsNo;
        public String recordTime;
    }

    @PostMapping("/transfers")
    @RequirePerm(value = {"UC-20"}, roles = {"BOSS"})
    @Transactional
    public Result<Map<String, Object>> createTransfer(@RequestBody TransferReq req) {
        if (!List.of("调入", "调出").contains(req.direction)) {
            throw BizException.badRequest("方向只能为「调入」或「调出」");
        }
        SourceParty sp = partyMapper.selectById(req.merchantId);
        if (sp == null || !"同行商户".equals(sp.getPartyType())) {
            throw BizException.badRequest("合作商户不存在或不是同行商户");
        }
        Goods g = goodsMapper.selectOne(new QueryWrapper<Goods>().eq("goods_no", req.goodsNo));
        if (g == null) throw BizException.notFound("货品不存在");
        Long dup = transferMapper.selectCount(new QueryWrapper<Transfer>()
                .eq("goods_id", g.getId()).eq("settled", "否"));
        if (dup != null && dup > 0) throw BizException.conflict("该货品已有未结算的调货记录");
        if ("调出".equals(req.direction) && !"在柜".equals(g.getStatus())) {
            throw BizException.conflict("只有「在柜」货品可以调出");
        }

        Transfer t = new Transfer();
        t.setRecordNo(nextNo("DH"));
        t.setDirection(req.direction);
        t.setMerchantId(req.merchantId);
        t.setGoodsId(g.getId());
        t.setQuantity(1);
        t.setStatus("在调");
        t.setSettled("否");
        t.setRecordTime(req.recordTime == null ? LocalDateTime.now()
                : LocalDateTime.parse(req.recordTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        try {
            transferMapper.insert(t);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // uk_transfer_goods_open 函数唯一索引兜底：并发双登记只放行一条
            throw BizException.conflict("该货品已有未结算的调货记录");
        }

        if ("调出".equals(req.direction)) {
            // UC-01 规则⑤：寄卖在同行处的货权仍归本店，状态为「代销中」，不是「已调出」
            String from = g.getStatus();
            g.setStatus("代销中");
            goodsMapper.updateById(g);
            writeLog(g.getId(), from, "代销中", "调货记录", t.getRecordNo());
        }
        return Result.ok(transferVO(t));
    }

    public static class StatusReq {
        public String status;
    }

    @PutMapping("/transfers/{id}/status")
    @RequirePerm(value = {"UC-20"}, roles = {"BOSS"})
    @Transactional
    public Result<Void> changeTransferStatus(@PathVariable Long id, @RequestBody StatusReq req) {
        Transfer t = transferMapper.selectById(id);
        if (t == null) throw BizException.notFound("调货记录不存在");
        if (!List.of("已售", "已退回").contains(req.status)) {
            throw BizException.badRequest("状态只能改为「已售」或「已退回」");
        }
        if ("是".equals(t.getSettled())) throw BizException.conflict("已结算的记录不能改状态");
        if (!"在调".equals(t.getStatus())) throw BizException.conflict("只有「在调」的记录可以改状态");
        t.setStatus(req.status);
        transferMapper.updateById(t);
        Goods g = goodsMapper.selectById(t.getGoodsId());
        if (g != null) {
            String from = g.getStatus();
            String to = "已售".equals(req.status) ? "已售" : "在柜";
            g.setStatus(to);
            goodsMapper.updateById(g);
            writeLog(g.getId(), from, to, "调货记录", t.getRecordNo());
        }
        return Result.ok();
    }

    // ==================== 内部：调货结算 ====================

    @RequirePerm(value = {"UC-23"}, roles = {"BOSS"})
    @GetMapping("/settlements/pending")
    public Result<Map<String, Object>> pending(@RequestParam Long merchantId) {
        SourceParty sp = partyMapper.selectById(merchantId);
        List<Transfer> list = transferMapper.selectList(new QueryWrapper<Transfer>()
                .eq("merchant_id", merchantId).eq("settled", "否").ne("status", "在调"));
        BigDecimal sum = BigDecimal.ZERO;
        List<Map<String, Object>> items = new ArrayList<>();
        for (Transfer t : list) {
            Goods g = goodsMapper.selectById(t.getGoodsId());
            BigDecimal amount = amountOf(g);
            sum = sum.add(amount);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("transferId", t.getId());
            m.put("recordNo", t.getRecordNo());
            m.put("goodsName", g == null ? null : g.getName());
            m.put("direction", t.getDirection());
            m.put("amount", amount);
            m.put("status", t.getStatus());
            m.put("recordTime", t.getRecordTime());
            long overdue = ChronoUnit.DAYS.between(t.getRecordTime().toLocalDate().plusDays(7), LocalDate.now());
            m.put("overdueDays", overdue);
            items.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("merchantId", merchantId);
        data.put("merchantName", sp == null ? null : sp.getName());
        data.put("total", items.size());
        data.put("amountSum", sum);
        data.put("items", items);
        return Result.ok(data);
    }

    public static class SettleBatchReq {
        public Long merchantId;
        public List<Long> transferIds;
        public String settleMethod;
        public String settleTime;
    }

    @PostMapping("/settlements/batch")
    @RequirePerm(value = {"UC-23"}, roles = {"BOSS"})
    @Transactional
    public Result<Map<String, Object>> settleBatch(@RequestBody SettleBatchReq req) {
        if (req.transferIds == null || req.transferIds.isEmpty()) {
            throw BizException.badRequest("请选择要结算的记录");
        }
        Long uid = CurrentUser.require().getUserId();
        BigDecimal sum = BigDecimal.ZERO;
        List<Map<String, Object>> created = new ArrayList<>();
        LocalDateTime when = req.settleTime == null ? LocalDateTime.now()
                : LocalDateTime.parse(req.settleTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        for (Long tid : req.transferIds) {
            Transfer t = transferMapper.selectById(tid);
            if (t == null) throw BizException.notFound("调货记录不存在：" + tid);
            if (!Objects.equals(t.getMerchantId(), req.merchantId)) {
                throw BizException.badRequest("记录不属于该商户：" + tid);
            }
            if ("是".equals(t.getSettled())) throw BizException.conflict("记录已结算：" + tid);
            if ("在调".equals(t.getStatus())) throw BizException.unprocessable("货品尚未售出或退回，不能结算：" + tid);

            Goods g = goodsMapper.selectById(t.getGoodsId());
            BigDecimal amount = amountOf(g);
            Settlement s = new Settlement();
            s.setSettleNo(nextNo("JS"));
            s.setPartyId(req.merchantId);
            s.setSettleType("调货");
            s.setTransferId(tid);
            s.setAmount(amount);
            s.setSettleMethod(req.settleMethod == null ? "现金" : req.settleMethod);
            s.setSettleTime(when);
            s.setOperatorId(uid);
            settlementMapper.insert(s);

            t.setSettled("是");
            transferMapper.updateById(t);
            sum = sum.add(amount);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("settleNo", s.getSettleNo());
            m.put("transferId", tid);
            m.put("amount", amount);
            created.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("settledCount", created.size());
        data.put("amountSum", sum);
        data.put("settlements", created);
        return Result.ok(data);
    }

    // ==================== 寄售 ====================

    @RequirePerm(value = {}, roles = {"BOSS", "SALE"})
    @GetMapping("/consignments")
    public Result<List<Map<String, Object>>> listConsignments() {
        List<Consignment> list = consignMapper.selectList(
                new QueryWrapper<Consignment>().orderByDesc("sign_time"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Consignment c : list) {
            SourceParty sp = partyMapper.selectById(c.getSupplierId());
            List<ConsignmentItem> its = itemMapper.selectList(
                    new QueryWrapper<ConsignmentItem>().eq("consignment_id", c.getId()));
            long settled = its.stream().filter(i -> "已结算".equals(i.getSettleStatus())).count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("consignNo", c.getConsignNo());
            m.put("supplierName", sp == null ? null : sp.getName());
            m.put("signTime", c.getSignTime());
            m.put("expireDate", c.getExpireDate());
            m.put("settleCycle", c.getSettleCycle());
            m.put("status", c.getStatus());
            m.put("itemCount", its.size());
            m.put("settledCount", settled);
            m.put("allSettled", !its.isEmpty() && settled == its.size());
            out.add(m);
        }
        return Result.ok(out);
    }

    @RequirePerm(value = {}, roles = {"BOSS", "SALE"})
    @GetMapping("/consignments/{id}")
    public Result<Map<String, Object>> consignmentDetail(@PathVariable Long id) {
        Consignment c = consignMapper.selectById(id);
        if (c == null) throw BizException.notFound("合同不存在");
        SourceParty sp = partyMapper.selectById(c.getSupplierId());
        List<ConsignmentItem> its = itemMapper.selectList(
                new QueryWrapper<ConsignmentItem>().eq("consignment_id", id));
        List<Map<String, Object>> items = new ArrayList<>();
        long sold = 0, settled = 0;
        BigDecimal unsettledAmt = BigDecimal.ZERO;
        for (ConsignmentItem i : its) {
            Goods g = goodsMapper.selectById(i.getGoodsId());
            boolean isSold = g != null && "已售".equals(g.getStatus());
            if (isSold) sold++;
            if ("已结算".equals(i.getSettleStatus())) settled++;
            else unsettledAmt = unsettledAmt.add(i.getConsignPrice());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.getId());
            m.put("goodsNo", g == null ? null : g.getGoodsNo());
            m.put("name", g == null ? null : g.getName());
            m.put("status", g == null ? null : g.getStatus());
            m.put("consignPrice", i.getConsignPrice());
            m.put("settleStatus", i.getSettleStatus());
            items.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", c.getId());
        data.put("consignNo", c.getConsignNo());
        data.put("supplierName", sp == null ? null : sp.getName());
        data.put("signTime", c.getSignTime());
        data.put("expireDate", c.getExpireDate());
        data.put("status", c.getStatus());
        data.put("settleCycle", c.getSettleCycle());
        data.put("itemCount", its.size());
        data.put("soldCount", sold);
        data.put("settledCount", settled);
        data.put("unsettledAmount", unsettledAmt);
        data.put("settled", !its.isEmpty() && settled == its.size());
        data.put("items", items);
        return Result.ok(data);
    }

    public static class ConsignReq {
        public Long supplierId;
        public String signTime;
        public String expireDate;
        public String settleCycle;
        public List<Map<String, Object>> items;
    }

    @PostMapping("/consignments")
    @RequirePerm(value = {"UC-21"}, roles = {"BOSS"})
    @Transactional
    public Result<Map<String, Object>> createConsignment(@RequestBody ConsignReq req) {
        SourceParty sp = partyMapper.selectById(req.supplierId);
        if (sp == null || !"供应商".equals(sp.getPartyType())) {
            throw BizException.unprocessable("供应商不存在，请先在基础数据建档");
        }
        if (req.items == null || req.items.isEmpty()) throw BizException.badRequest("货品清单不能为空");
        if (req.expireDate != null && req.signTime != null
                && LocalDate.parse(req.expireDate).isBefore(LocalDate.parse(req.signTime.substring(0, 10)))) {
            throw BizException.badRequest("到期日不能早于签订时间");
        }

        Consignment c = new Consignment();
        c.setConsignNo(nextNo("JSHT"));
        c.setSupplierId(req.supplierId);
        c.setSignTime(req.signTime == null ? LocalDateTime.now()
                : LocalDateTime.parse(req.signTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        c.setExpireDate(req.expireDate == null ? LocalDate.now().plusYears(1) : LocalDate.parse(req.expireDate));
        c.setSettleCycle(req.settleCycle == null ? "售出即结" : req.settleCycle);
        c.setStatus("生效");
        consignMapper.insert(c);

        // 寄售入库要有一条真实批次，不能硬编码 stockInId
        StockIn si = new StockIn();
        si.setInTime(LocalDateTime.now());
        si.setReceiverId(CurrentUser.require().getUserId());
        si.setRemark("寄售入库 " + c.getConsignNo());
        stockInMapper.insert(si);

        List<Map<String, Object>> created = new ArrayList<>();
        for (Map<String, Object> it : req.items) {
            BigDecimal price = GoodsController.dec(it.get("consignPrice"));
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                throw BizException.badRequest("寄售价必须大于 0");
            }
            Goods g = new Goods();
            g.setGoodsNo(nextGoodsNo());
            g.setName(GoodsController.str(it.get("name")));
            g.setCategoryId(GoodsController.num(it.get("categoryId")));
            g.setMaterial(Objects.toString(it.get("material"), "未填"));
            g.setOrigin(Objects.toString(it.get("origin"), "未填"));
            g.setShape(Objects.toString(it.get("shape"), "其他"));
            g.setForm(Objects.toString(it.get("form"), "山料"));
            g.setWeightG(GoodsController.dec(it.get("weightG")));
            g.setSize(GoodsController.str(it.get("size")));
            g.setSrcType("寄售");
            g.setSrcParty(sp.getName());
            g.setCost(price);
            // 寄售价是结算依据（成本），最低售价按寄售价上浮 25% 估，两者不该相等
            g.setMinPrice(price.multiply(new BigDecimal("1.25")).setScale(2, RoundingMode.HALF_UP));
            g.setInTime(LocalDateTime.now());
            g.setStockInId(si.getId());
            g.setStatus("在柜");
            g.setIsFeatured(0);
            goodsMapper.insert(g);
            // 寄售入库也要留流水：货品是新记录，变更前状态为空
            writeLog(g.getId(), null, "在柜", "寄售合同", c.getConsignNo());

            ConsignmentItem ci = new ConsignmentItem();
            ci.setConsignmentId(c.getId());
            ci.setGoodsId(g.getId());
            ci.setConsignPrice(price);
            ci.setSettleStatus("未结算");
            itemMapper.insert(ci);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("goodsNo", g.getGoodsNo());
            m.put("consignPrice", price);
            created.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("consignNo", c.getConsignNo());
        data.put("itemCount", created.size());
        data.put("goods", created);
        return Result.ok(data);
    }

    @GetMapping("/consignments/settleable")
    @RequirePerm(value = {"UC-22"}, roles = {"BOSS"})
    public Result<Map<String, Object>> settleable() {
        List<ConsignmentItem> list = itemMapper.selectList(
                new QueryWrapper<ConsignmentItem>().eq("settle_status", "未结算"));
        BigDecimal sum = BigDecimal.ZERO;
        List<Map<String, Object>> items = new ArrayList<>();
        for (ConsignmentItem i : list) {
            Goods g = goodsMapper.selectById(i.getGoodsId());
            if (g == null || !"已售".equals(g.getStatus())) continue;
            Consignment c = consignMapper.selectById(i.getConsignmentId());
            SourceParty sp = c == null ? null : partyMapper.selectById(c.getSupplierId());
            sum = sum.add(i.getConsignPrice());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemId", i.getId());
            m.put("consignNo", c == null ? null : c.getConsignNo());
            m.put("supplierName", sp == null ? null : sp.getName());
            m.put("goodsName", g.getName());
            m.put("consignPrice", i.getConsignPrice());
            items.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", items.size());
        data.put("amountSum", sum);
        data.put("items", items);
        return Result.ok(data);
    }

    public static class ConsignSettleReq {
        public List<Long> itemIds;
        public String settleMethod;
        public String settleTime;
    }

    @PostMapping("/consignments/settlements")
    @RequirePerm(value = {"UC-22"}, roles = {"BOSS"})
    @Transactional
    public Result<Map<String, Object>> settleConsign(@RequestBody ConsignSettleReq req) {
        if (req.itemIds == null || req.itemIds.isEmpty()) throw BizException.badRequest("请选择要结算的明细");
        Long uid = CurrentUser.require().getUserId();
        LocalDateTime when = req.settleTime == null ? LocalDateTime.now()
                : LocalDateTime.parse(req.settleTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        BigDecimal sum = BigDecimal.ZERO;
        List<Map<String, Object>> created = new ArrayList<>();

        for (Long iid : req.itemIds) {
            ConsignmentItem i = itemMapper.selectById(iid);
            if (i == null) throw BizException.notFound("寄售明细不存在：" + iid);
            if ("已结算".equals(i.getSettleStatus())) throw BizException.conflict("明细已结算：" + iid);
            Goods g = goodsMapper.selectById(i.getGoodsId());
            if (g == null || !"已售".equals(g.getStatus())) {
                throw BizException.unprocessable("货品未售出，不能结算：" + iid);
            }
            Consignment c = consignMapper.selectById(i.getConsignmentId());

            Settlement s = new Settlement();
            s.setSettleNo(nextNo("JS"));
            s.setPartyId(c == null ? null : c.getSupplierId());
            s.setSettleType("寄售");
            s.setConsignItemId(iid);
            s.setAmount(i.getConsignPrice());
            s.setSettleMethod(req.settleMethod == null ? "转账" : req.settleMethod);
            s.setSettleTime(when);
            s.setOperatorId(uid);
            settlementMapper.insert(s);

            i.setSettleStatus("已结算");
            itemMapper.updateById(i);

            // 同步写一条货款记录（UC-22 规则③）
            Payment p = new Payment();
            p.setRecordNo(nextNo("HK"));
            p.setBizType("寄售货款");
            p.setRelatedType("台账结算");
            p.setRelatedNo(s.getSettleNo());
            p.setAmount(i.getConsignPrice());
            p.setDirection("支付");
            p.setOperatorId(uid);
            p.setPayTime(when);
            paymentMapper.insert(p);

            sum = sum.add(i.getConsignPrice());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("settleNo", s.getSettleNo());
            m.put("itemId", iid);
            m.put("amount", i.getConsignPrice());
            created.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("settledCount", created.size());
        data.put("amountSum", sum);
        data.put("settlements", created);
        return Result.ok(data);
    }

    @PostMapping("/consignments/{id}/terminate")
    @RequirePerm(value = {"UC-21"}, roles = {"BOSS"})
    @Transactional
    public Result<Void> terminate(@PathVariable Long id) {
        Consignment c = consignMapper.selectById(id);
        if (c == null) throw BizException.notFound("合同不存在");
        if (!"生效".equals(c.getStatus())) throw BizException.conflict("该合同已终止或到期");
        List<ConsignmentItem> its = itemMapper.selectList(
                new QueryWrapper<ConsignmentItem>().eq("consignment_id", id).eq("settle_status", "未结算"));
        for (ConsignmentItem i : its) {
            Goods g = goodsMapper.selectById(i.getGoodsId());
            if (g != null && "已售".equals(g.getStatus())) {
                throw BizException.conflict("仍有已售未结算的货品，请先结算再终止");
            }
        }
        for (ConsignmentItem i : its) {
            Goods g = goodsMapper.selectById(i.getGoodsId());
            if (g != null && "在柜".equals(g.getStatus())) {
                g.setStatus("待处理");
                goodsMapper.updateById(g);
                writeLog(g.getId(), "在柜", "待处理", "寄售合同", c.getConsignNo());
            }
        }
        c.setStatus("已终止");
        consignMapper.updateById(c);
        return Result.ok();
    }

    // ==================== 加工：师傅端 ====================

    @GetMapping("/craftsman/tasks")
    @RequirePerm(value = {"UC-16"}, roles = {"CRAFTSMAN"})
    public Result<Map<String, Object>> myTasks(@RequestParam(required = false) String status) {
        Long pid = partyIdOfCurrentUser();
        QueryWrapper<ProcessTask> q = new QueryWrapper<ProcessTask>().eq("craftsman_id", pid);
        if (status != null && !status.isBlank()) q.in("status", Arrays.asList(status.split(",")));
        q.orderByAsc("deadline");
        List<ProcessTask> list = taskMapper.selectList(q);
        List<Map<String, Object>> items = new ArrayList<>();
        for (ProcessTask t : list) items.add(taskVO(t));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", items.size());
        data.put("items", items);
        return Result.ok(data);
    }

    @GetMapping("/craftsman/tasks/{id}")
    @RequirePerm(value = {"UC-16"}, roles = {"CRAFTSMAN"})
    public Result<Map<String, Object>> myTask(@PathVariable Long id) {
        ProcessTask t = taskMapper.selectById(id);
        if (t == null) throw BizException.notFound("任务不存在");
        if (!Objects.equals(t.getCraftsmanId(), partyIdOfCurrentUser())) {
            throw BizException.forbidden("无权查看他人任务");
        }
        return Result.ok(taskVO(t));
    }

    public static class DeliveryReq {
        public String note;
        public List<Map<String, String>> images;
    }

    @PostMapping("/craftsman/tasks/{id}/delivery")
    @RequirePerm(value = {"UC-17"}, roles = {"CRAFTSMAN"})
    @Transactional
    public Result<Map<String, Object>> submitDelivery(@PathVariable Long id, @RequestBody DeliveryReq req) {
        ProcessTask t = taskMapper.selectById(id);
        if (t == null) throw BizException.notFound("任务不存在");
        if (!Objects.equals(t.getCraftsmanId(), partyIdOfCurrentUser())) {
            throw BizException.forbidden("无权操作他人任务");
        }
        if (!"在加工".equals(t.getStatus())) {
            throw BizException.unprocessable("任务状态为「" + t.getStatus() + "」，不能提交完工");
        }
        Long pending = deliveryMapper.selectCount(new QueryWrapper<DeliveryApply>()
                .eq("task_id", id).eq("status", "待确认"));
        if (pending != null && pending > 0) throw BizException.conflict("已有待确认的完工申请");
        if (req.images == null || req.images.size() < 3) {
            throw BizException.badRequest("需上传加工前、加工中、完工三张图");
        }
        Set<String> stages = new HashSet<>();
        for (Map<String, String> im : req.images) {
            String st = im.get("stage");
            if (!List.of("加工前", "加工中", "完工").contains(st)) {
                throw BizException.badRequest("图片阶段不合法");
            }
            stages.add(st);
        }
        if (stages.size() < 3 || stages.size() != req.images.size()) {
            // 每阶段恰一张：集合去重后少了不行，原数组比集合多说明有重复阶段，
            // 会撞 uk_delivery_image_stage(apply_id, stage) 报 500
            throw BizException.badRequest("加工前、加工中、完工三个阶段各传一张，不可重复");
        }

        DeliveryApply d = new DeliveryApply();
        d.setApplyNo(nextNo("JGJF"));
        d.setTaskId(id);
        d.setNote(req.note);
        d.setSubmitTime(LocalDateTime.now());
        d.setStatus("待确认");
        deliveryMapper.insert(d);
        for (Map<String, String> im : req.images) {
            DeliveryImage di = new DeliveryImage();
            di.setApplyId(d.getId());
            di.setStage(im.get("stage"));
            di.setImagePath(im.get("path"));
            deliveryImageMapper.insert(di);
        }
        t.setStatus("待交付");
        taskMapper.updateById(t);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("applyNo", d.getApplyNo());
        data.put("taskStatus", t.getStatus());
        return Result.ok(data);
    }

    // ==================== 加工：老板端 ====================

    /** 「逾期」口径：还压在人手里且过了期限。待交付/已交付不算——活已经干完了，催没意义 */
    private static final List<String> OVERDUE_STATUS = List.of("待加工", "在加工");
    /** 上门时段：门店营业 10:00–20:00，一次交接占一小时，最晚 19:00 起 */
    private static final LocalTime VISIT_OPEN = LocalTime.of(10, 0);
    private static final LocalTime VISIT_LAST_START = LocalTime.of(19, 0);

    @RequirePerm(value = {}, roles = {"BOSS", "SALE"})
    @GetMapping("/process-tasks")
    public Result<Map<String, Object>> listTasks(@RequestParam(required = false) Long craftsmanId,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) Boolean overdueOnly,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        if (size > 100) size = 100;
        QueryWrapper<ProcessTask> q = new QueryWrapper<>();
        if (craftsmanId != null) q.eq("craftsman_id", craftsmanId);
        // 「逾期」是派生态，过滤必须在分页前下沉 SQL，否则 total 与列表对不上、
        // 末页可能整页被滤空（分期那边同样的写法）
        if (Boolean.TRUE.equals(overdueOnly)) {
            q.in("status", OVERDUE_STATUS).lt("deadline", LocalDate.now());
        } else if (status != null && !status.isBlank()) {
            q.eq("status", status);
        }
        q.orderByAsc("deadline");
        Page<ProcessTask> p = taskMapper.selectPage(new Page<>(page, size), q);
        List<Map<String, Object>> items = new ArrayList<>();
        for (ProcessTask t : p.getRecords()) items.add(taskVO(t));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", p.getTotal());
        data.put("page", page);
        data.put("size", size);
        // 逾期总数单独数一次：勾了「只看逾期」时工具栏上仍然要看它，
        // 不单独数就会等于 total，失去意义
        data.put("overdueCount", taskMapper.selectCount(new QueryWrapper<ProcessTask>()
                .in("status", OVERDUE_STATUS).lt("deadline", LocalDate.now())
                .eq(craftsmanId != null, "craftsman_id", craftsmanId)));
        data.put("items", items);
        return Result.ok(data);
    }

    /**
     * 催办：给逾期任务记一笔次数与时间，师傅端任务卡上显示「门店已催办 N 次」。
     * 只是留痕与提示——门店没有可用的消息推送通道（微信模板消息要备案域名），
     * 真催人还是靠电话，这里保证「催过没有、催了几次」有据可查。
     */
    @PostMapping("/process-tasks/{id}/urge")
    @RequirePerm(value = {}, roles = {"BOSS", "SALE"})
    public Result<Map<String, Object>> urgeTask(@PathVariable Long id) {
        ProcessTask t = taskMapper.selectById(id);
        if (t == null) throw BizException.notFound("任务不存在");
        if (!OVERDUE_STATUS.contains(t.getStatus())) {
            throw BizException.conflict("任务状态为「" + t.getStatus() + "」，不在催办口径内");
        }
        if (t.getDeadline() == null || !t.getDeadline().isBefore(LocalDate.now())) {
            throw BizException.unprocessable("这件还没到期，不用催");
        }
        t.setUrgeCount(t.getUrgeCount() == null ? 1 : t.getUrgeCount() + 1);
        t.setLastUrgeAt(LocalDateTime.now());
        taskMapper.updateById(t);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("urgeCount", t.getUrgeCount());
        data.put("lastUrgeAt", t.getLastUrgeAt());
        return Result.ok(data);
    }

    /** 开始加工：文档 §4.4 里老板没有 UC-16（那是师傅的查看权限），
     *  这个动作是「安排加工」的延续，归到 UC-02 货品入库 */
    @PostMapping("/process-tasks/{id}/start")
    @RequirePerm(value = {"UC-02"}, roles = {"BOSS"})
    public Result<Void> startTask(@PathVariable Long id) {
        ProcessTask t = taskMapper.selectById(id);
        if (t == null) throw BizException.notFound("任务不存在");
        if (!"待加工".equals(t.getStatus())) throw BizException.conflict("只有「待加工」的任务可以开始");
        t.setStatus("在加工");
        taskMapper.updateById(t);
        return Result.ok();
    }

    @RequirePerm(value = {}, roles = {"BOSS", "SALE", "CRAFTSMAN"})
    @GetMapping("/delivery-applies")
    public Result<List<Map<String, Object>>> listDeliveries(@RequestParam(required = false) String status) {
        QueryWrapper<DeliveryApply> q = new QueryWrapper<>();
        if (status != null && !status.isBlank()) q.eq("status", status);
        // 师傅角色只能看到自己任务的交付申请（BOSS/SALE 看全部）；
        // 外部端师傅「预约上门」要按已确认交付选单，没有这个口子功能不可达
        CurrentUser cu = CurrentUser.get();
        Long myParty = null;
        boolean craftsmanOnly = cu != null && cu.hasRole("CRAFTSMAN") && !cu.hasRole("BOSS");
        if (craftsmanOnly) {
            try { myParty = partyIdOfCurrentUser(); } catch (Exception e) { myParty = null; }
        }
        q.orderByDesc("submit_time");
        List<DeliveryApply> list = deliveryMapper.selectList(q);
        List<Map<String, Object>> out = new ArrayList<>();
        for (DeliveryApply d : list) {
            ProcessTask t = taskMapper.selectById(d.getTaskId());
            if (craftsmanOnly) {
                if (t == null || myParty == null || !myParty.equals(t.getCraftsmanId())) continue;
            }
            Goods g = t == null ? null : goodsMapper.selectById(t.getGoodsId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("applyNo", d.getApplyNo());
            m.put("taskNo", t == null ? null : t.getTaskNo());
            m.put("goodsName", g == null ? null : g.getName());
            m.put("note", d.getNote());
            m.put("status", d.getStatus());
            m.put("submitTime", d.getSubmitTime());
            List<DeliveryImage> imgs = deliveryImageMapper.selectList(
                    new QueryWrapper<DeliveryImage>().eq("apply_id", d.getId()));
            m.put("images", imgs);
            out.add(m);
        }
        return Result.ok(out);
    }

    public static class ConfirmReq {
        public Boolean accepted;
        public String opinion;
    }

    /** 确认完工：同理，归到 UC-02，师傅只有提交权没有确认权 */
    @PostMapping("/delivery-applies/{id}/confirm")
    @RequirePerm(value = {"UC-02"}, roles = {"BOSS"})
    @Transactional
    public Result<Void> confirmDelivery(@PathVariable Long id, @RequestBody ConfirmReq req) {
        DeliveryApply d = deliveryMapper.selectById(id);
        if (d == null) throw BizException.notFound("交付申请不存在");
        if (!"待确认".equals(d.getStatus())) throw BizException.conflict("该申请已处理");
        ProcessTask t = taskMapper.selectById(d.getTaskId());
        if (Boolean.TRUE.equals(req.accepted)) {
            d.setStatus("已确认");
            deliveryMapper.updateById(d);
            if (t != null) {
                t.setStatus("已交付");
                taskMapper.updateById(t);
            }
        } else {
            if (req.opinion == null || req.opinion.isBlank()) {
                throw BizException.badRequest("退回必须填写原因");
            }
            d.setNote((d.getNote() == null ? "" : d.getNote() + " | 退回：") + req.opinion);
            deliveryMapper.updateById(d);
            if (t != null) {
                t.setStatus("在加工");
                taskMapper.updateById(t);
            }
        }
        return Result.ok();
    }

    // ==================== 上门预约 ====================

    public static class VisitReq {
        public String matter;
        /** yyyy-MM-dd */
        public String expectDate;
        /** HH:mm，占一小时时段，结束时间由服务端算 */
        public String startTime;
        public Long transferApplyId;
        public Long deliveryApplyId;
    }

    @PostMapping("/visit-appts")
    @RequirePerm(value = {"UC-15"})
    public Result<Map<String, Object>> createVisit(@RequestBody VisitReq req) {
        boolean hasA = req.transferApplyId != null;
        boolean hasB = req.deliveryApplyId != null;
        if (hasA == hasB) throw BizException.badRequest("出让申请与交付申请须二选一");
        Long myParty = partyIdOfCurrentUser();
        if (hasA) {
            TransferApply a = applyMapper.selectById(req.transferApplyId);
            if (a == null || !"已通过".equals(a.getStatus())) {
                throw BizException.unprocessable("该出让申请未通过审核");
            }
            // 只能对自己的出让申请约上门，不能拿别人的申请号建预约
            if (myParty != null && !myParty.equals(a.getSupplierId())) {
                throw BizException.forbidden("只能对自己的出让申请预约上门");
            }
        } else {
            DeliveryApply d = deliveryMapper.selectById(req.deliveryApplyId);
            if (d == null || !"已确认".equals(d.getStatus())) {
                throw BizException.unprocessable("该完工申请未确认");
            }
            if (myParty != null) {
                ProcessTask pt = taskMapper.selectById(d.getTaskId());
                if (pt != null && !myParty.equals(pt.getCraftsmanId())) {
                    throw BizException.forbidden("只能对自己的完工申请预约上门");
                }
            }
        }
        LocalDate day;
        LocalTime from;
        try {
            day = LocalDate.parse(req.expectDate == null ? "" : req.expectDate.trim());
            from = LocalTime.parse(req.startTime == null ? "" : req.startTime.trim()).withSecond(0).withNano(0);
        } catch (DateTimeParseException e) {
            throw BizException.badRequest("上门日期格式 yyyy-MM-dd、时间格式 HH:mm");
        }
        if (day.isBefore(LocalDate.now())) throw BizException.unprocessable("上门日期不能早于今天");
        if (from.getMinute() != 0 && from.getMinute() != 30) {
            throw BizException.unprocessable("时段按半小时选（整点或半点）");
        }
        if (day.isAfter(LocalDate.now().plusDays(14))) throw BizException.unprocessable("上门日期最多约到 14 天内");
        if (from.isBefore(VISIT_OPEN) || from.isAfter(VISIT_LAST_START)) {
            throw BizException.unprocessable("上门时段请在 " + VISIT_OPEN + " 到 " + VISIT_LAST_START + " 之间选（每次占一小时）");
        }
        LocalTime to = from.plusHours(1);
        // UC-15 规则②「同一时段只安排一次交接」：以前 expect_slot 是文本，只能比字符串
        // 相等，10:00 和 10:30 写成不同值就都放行；结构化之后按区间重叠判
        for (VisitAppt o : visitMapper.selectList(new QueryWrapper<VisitAppt>()
                .eq("expect_date", day).eq("status", "待上门"))) {
            if (o.getStartTime() != null && o.getEndTime() != null
                    && from.isBefore(o.getEndTime()) && o.getStartTime().isBefore(to)) {
                throw BizException.conflict("该时段已有其他上门预约（" + o.getStartTime() + "–" + o.getEndTime() + "）");
            }
        }
        // FR-016 时段互斥是双向的：客户已约的到店时段，上门预约也要避让
        Long clash = reservationMapper.selectCount(new QueryWrapper<Reservation>()
                .eq("status", "待确认")
                .ge("expect_slot", LocalDateTime.of(day, from))
                .lt("expect_slot", LocalDateTime.of(day, to)));
        if (clash != null && clash > 0) throw BizException.conflict("该时段已有客户到店预约");

        VisitAppt v = new VisitAppt();
        v.setApptNo(nextNo("SM"));
        v.setApptParty(CurrentUser.require().getUsername());
        v.setMatter(req.matter == null ? "上门交接" : req.matter);
        v.setExpectDate(day);
        v.setStartTime(from);
        v.setEndTime(to);
        v.setSubmitTime(LocalDateTime.now());
        v.setStatus("待上门");
        v.setTransferApplyId(req.transferApplyId);
        v.setDeliveryApplyId(req.deliveryApplyId);
        try {
            visitMapper.insert(v);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // uk_visit_slot_open 函数唯一索引兜底：同时段并发只放行一条
            throw BizException.conflict("该时段已有其他上门预约");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apptNo", v.getApptNo());
        data.put("status", v.getStatus());
        return Result.ok(data);
    }

    @RequirePerm(value = {}, roles = {"BOSS", "SALE"})
    @GetMapping("/visit-appts")
    public Result<List<VisitAppt>> listVisits(@RequestParam(required = false) String status) {
        QueryWrapper<VisitAppt> q = new QueryWrapper<>();
        if (status != null && !status.isBlank()) q.eq("status", status);
        q.orderByDesc("submit_time");
        return Result.ok(visitMapper.selectList(q));
    }

    @PostMapping("/visit-appts/{id}/confirm")
    // 角色门槛而非 UC-15 权限码：种子数据老板没有 UC-15（矩阵里 UC-15 老板为 —），
    // 用权限码会造成「规约要求老板确认、代码却拦老板」的死锁
    @RequirePerm(value = {}, roles = {"BOSS"})
    public Result<Void> confirmVisit(@PathVariable Long id) {
        VisitAppt v = visitMapper.selectById(id);
        if (v == null) throw BizException.notFound("预约不存在");
        if (!"待上门".equals(v.getStatus())) throw BizException.conflict("只有「待上门」的预约可以确认");
        v.setStatus("已完成");
        visitMapper.updateById(v);
        return Result.ok();
    }

    @PostMapping("/visit-appts/{id}/cancel")
    @RequirePerm(value = {}, roles = {"BOSS"})
    public Result<Void> cancelVisit(@PathVariable Long id) {
        VisitAppt v = visitMapper.selectById(id);
        if (v == null) throw BizException.notFound("预约不存在");
        if (!"待上门".equals(v.getStatus())) throw BizException.conflict("只有「待上门」的预约可以取消");
        v.setStatus("已取消");
        visitMapper.updateById(v);
        return Result.ok();
    }

    // ==================== 工具 ====================

    /** 调货结算金额：优先调货价，为空回退最低售价 */
    private BigDecimal amountOf(Goods g) {
        if (g == null) return BigDecimal.ZERO;
        if (g.getTransferPrice() != null) return g.getTransferPrice();
        return g.getMinPrice() == null ? BigDecimal.ZERO : g.getMinPrice();
    }

    private Map<String, Object> transferVO(Transfer t) {
        Goods g = goodsMapper.selectById(t.getGoodsId());
        SourceParty sp = partyMapper.selectById(t.getMerchantId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("recordNo", t.getRecordNo());
        m.put("direction", t.getDirection());
        m.put("merchantId", t.getMerchantId());
        m.put("merchantName", sp == null ? null : sp.getName());
        m.put("goodsNo", g == null ? null : g.getGoodsNo());
        m.put("goodsName", g == null ? null : g.getName());
        m.put("status", t.getStatus());
        m.put("settled", t.getSettled());
        m.put("recordTime", t.getRecordTime());
        m.put("amount", amountOf(g));
        return m;
    }

    private Map<String, Object> taskVO(ProcessTask t) {
        Goods g = goodsMapper.selectById(t.getGoodsId());
        SourceParty sp = partyMapper.selectById(t.getCraftsmanId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("taskNo", t.getTaskNo());
        m.put("goodsNo", g == null ? null : g.getGoodsNo());
        m.put("goodsName", g == null ? null : g.getName());
        m.put("craftsmanId", t.getCraftsmanId());
        m.put("craftsmanName", sp == null ? null : sp.getName());
        m.put("requirement", t.getRequirement());
        m.put("laborFee", t.getLaborFee());
        m.put("deadline", t.getDeadline());
        m.put("status", t.getStatus());
        // 「逾期」是派生态，前端（管理端与师傅端）都读这两个字段，不再各自算一遍
        boolean overdue = OVERDUE_STATUS.contains(t.getStatus())
                && t.getDeadline() != null && t.getDeadline().isBefore(LocalDate.now());
        m.put("overdue", overdue);
        m.put("overdueDays", overdue
                ? ChronoUnit.DAYS.between(t.getDeadline(), LocalDate.now()) : 0);
        m.put("urgeCount", t.getUrgeCount() == null ? 0 : t.getUrgeCount());
        m.put("lastUrgeAt", t.getLastUrgeAt());
        return m;
    }

    /**
     * 师傅/供应商账号与 t_source_party 的关联：约定用户名 craftsman_{id} / supplier_{id}。
     * 见 specs/006-processing/contracts/api.md 的「身份关联」一节。
     */
    private Long partyIdOfCurrentUser() {
        Long uid = CurrentUser.require().getUserId();
        User u = uid == null ? null : userMapper.selectById(uid);
        // 优先读账号上的往来方字段；老数据没有该字段时退回用户名后缀
        if (u != null && u.getPartyId() != null) return u.getPartyId();
        String uname = u != null && u.getUsername() != null
                ? u.getUsername() : CurrentUser.require().getUsername();
        int idx = uname.indexOf('_');
        if (idx > 0) {
            try {
                return Long.valueOf(uname.substring(idx + 1));
            } catch (NumberFormatException ignored) {
                // 落到下面报错
            }
        }
        throw BizException.forbidden("账号未关联外部档案，请联系老板开通");
    }

    private void writeLog(Long goodsId, String from, String to, String refType, String refNo) {
        StockLog l = new StockLog();
        l.setGoodsId(goodsId);
        l.setFromStatus(from);
        l.setToStatus(to);
        l.setRefType(refType);
        l.setRefNo(refNo);
        l.setOperatorId(CurrentUser.require().getUserId());
        l.setLogTime(LocalDateTime.now());
        stockLogMapper.insert(l);
    }

    private String nextNo(String prefix) {
        return IdGen.next(prefix);
    }

    private String nextGoodsNo() {
        return IdGen.next("JD");
    }
}
