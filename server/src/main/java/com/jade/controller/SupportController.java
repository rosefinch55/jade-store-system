package com.jade.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jade.common.BizException;
import com.jade.common.CurrentUser;
import com.jade.common.RequirePerm;
import com.jade.common.Result;
import com.jade.entity.*;
import com.jade.mapper.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 补齐的零散接口：预留/取消预留、批次详情、申请详情、结算历史、权限列表、登出、送检与证书。
 * 与主 Controller 分开放，避免主文件继续膨胀。
 */
@RestController
@RequestMapping("/api")
public class SupportController {

    private final GoodsMapper goodsMapper;
    private final StockInMapper stockInMapper;
    private final StockLogMapper stockLogMapper;
    private final TransferApplyMapper applyMapper;
    private final SettlementMapper settlementMapper;
    private final PermissionMapper permMapper;
    private final ProcessTaskMapper taskMapper;
    private final ConsignmentMapper consignMapper;
    private final ConsignmentItemMapper itemMapper;
    private final SourcePartyMapper partyMapper;
    private final InspectionMapper inspectionMapper;
    private final CertificateMapper certMapper;
    private final CertOrgMapper certOrgMapper;
    private final DeliveryApplyMapper deliveryMapper;

    public SupportController(GoodsMapper goodsMapper, StockInMapper stockInMapper,
                             StockLogMapper stockLogMapper, TransferApplyMapper applyMapper,
                             SettlementMapper settlementMapper, PermissionMapper permMapper,
                             ProcessTaskMapper taskMapper, ConsignmentMapper consignMapper,
                             ConsignmentItemMapper itemMapper, SourcePartyMapper partyMapper,
                             InspectionMapper inspectionMapper, CertificateMapper certMapper,
                             CertOrgMapper certOrgMapper, DeliveryApplyMapper deliveryMapper) {
        this.goodsMapper = goodsMapper;
        this.stockInMapper = stockInMapper;
        this.stockLogMapper = stockLogMapper;
        this.applyMapper = applyMapper;
        this.settlementMapper = settlementMapper;
        this.permMapper = permMapper;
        this.taskMapper = taskMapper;
        this.consignMapper = consignMapper;
        this.itemMapper = itemMapper;
        this.partyMapper = partyMapper;
        this.inspectionMapper = inspectionMapper;
        this.certMapper = certMapper;
        this.certOrgMapper = certOrgMapper;
        this.deliveryMapper = deliveryMapper;
    }

    // ---------------- 登出 ----------------

    /** JWT 无状态，服务端只需让前端丢弃 token；接口存在是为了契约完整 */
    @PostMapping("/auth/logout")
    public Result<Void> logout() {
        return Result.ok();
    }

    // ---------------- 权限列表 ----------------

    @GetMapping("/permissions")
    @RequirePerm("UC-12")
    public Result<List<Permission>> permissions() {
        return Result.ok(permMapper.selectList(
                new QueryWrapper<Permission>().orderByAsc("perm_code")));
    }

    // ---------------- 货品预留 ----------------

    /** UC-01 规则③：客户明确要求预留但未付款，货品置「预留」，期间不得出售 */
    @PostMapping("/goods/{id}/reserve")
    @RequirePerm("UC-01")
    @Transactional
    public Result<Map<String, Object>> reserve(@PathVariable Long id) {
        Goods g = goodsMapper.selectById(id);
        if (g == null) throw BizException.notFound("货品不存在");
        if (!"在柜".equals(g.getStatus())) {
            throw BizException.conflict("只有「在柜」货品可以预留，当前为「" + g.getStatus() + "」");
        }
        String from = g.getStatus();
        g.setStatus("预留");
        goodsMapper.updateById(g);
        writeLog(g.getId(), from, "预留", "预留", null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("goodsNo", g.getGoodsNo());
        data.put("status", g.getStatus());
        return Result.ok(data);
    }

    @PostMapping("/goods/{id}/unreserve")
    @RequirePerm("UC-01")
    @Transactional
    public Result<Map<String, Object>> unreserve(@PathVariable Long id) {
        Goods g = goodsMapper.selectById(id);
        if (g == null) throw BizException.notFound("货品不存在");
        if (!"预留".equals(g.getStatus())) {
            throw BizException.conflict("该货品不在预留状态，当前为「" + g.getStatus() + "」");
        }
        String from = g.getStatus();
        g.setStatus("在柜");
        goodsMapper.updateById(g);
        writeLog(g.getId(), from, "在柜", "取消预留", null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("goodsNo", g.getGoodsNo());
        data.put("status", g.getStatus());
        return Result.ok(data);
    }

    // ---------------- 入库批次详情 ----------------

    @GetMapping("/stock-ins/{id}")
    public Result<Map<String, Object>> stockIn(@PathVariable Long id) {
        StockIn si = stockInMapper.selectById(id);
        if (si == null) throw BizException.notFound("入库批次不存在");
        List<Goods> gs = goodsMapper.selectList(new QueryWrapper<Goods>().eq("stock_in_id", id));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Goods g : gs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("goodsNo", g.getGoodsNo());
            m.put("name", g.getName());
            m.put("status", g.getStatus());
            m.put("minPrice", g.getMinPrice());
            items.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", si.getId());
        data.put("inTime", si.getInTime());
        data.put("receiverId", si.getReceiverId());
        data.put("remark", si.getRemark());
        data.put("goodsCount", items.size());
        data.put("goods", items);
        return Result.ok(data);
    }

    // ---------------- 出让申请详情 ----------------

    @GetMapping("/transfer-applies/{id}")
    @RequirePerm("UC-18")
    public Result<Map<String, Object>> applyDetail(@PathVariable Long id) {
        TransferApply a = applyMapper.selectById(id);
        if (a == null) throw BizException.notFound("申请不存在");
        SourceParty sp = partyMapper.selectById(a.getSupplierId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("applyNo", a.getApplyNo());
        m.put("supplierId", a.getSupplierId());
        m.put("supplierName", sp == null ? null : sp.getName());
        m.put("outType", a.getOutType());
        m.put("name", a.getName());
        m.put("quantity", a.getQuantity());
        m.put("expectPrice", a.getExpectPrice());
        m.put("description", a.getDescription());
        m.put("submitTime", a.getSubmitTime());
        m.put("status", a.getStatus());
        m.put("auditOpinion", a.getAuditOpinion());
        m.put("auditTime", a.getAuditTime());
        m.put("auditorId", a.getAuditorId());
        return Result.ok(m);
    }

    // ---------------- 结算历史 ----------------

    @GetMapping("/settlements")
    @RequirePerm(value = {"UC-23"}, roles = {"BOSS"})
    public Result<List<Map<String, Object>>> settlements(@RequestParam(required = false) Long partyId,
                                                         @RequestParam(required = false) String settleType) {
        QueryWrapper<Settlement> q = new QueryWrapper<>();
        if (partyId != null) q.eq("party_id", partyId);
        if (settleType != null && !settleType.isBlank()) q.eq("settle_type", settleType);
        q.orderByDesc("settle_time").last("limit 200");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Settlement s : settlementMapper.selectList(q)) {
            SourceParty sp = partyMapper.selectById(s.getPartyId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("settleNo", s.getSettleNo());
            m.put("partyName", sp == null ? null : sp.getName());
            m.put("settleType", s.getSettleType());
            m.put("amount", s.getAmount());
            m.put("settleMethod", s.getSettleMethod());
            m.put("settleTime", s.getSettleTime());
            out.add(m);
        }
        return Result.ok(out);
    }

    // ---------------- 加工任务详情 ----------------

    @GetMapping("/process-tasks/{id}")
    public Result<Map<String, Object>> taskDetail(@PathVariable Long id) {
        ProcessTask t = taskMapper.selectById(id);
        if (t == null) throw BizException.notFound("任务不存在");
        Goods g = goodsMapper.selectById(t.getGoodsId());
        SourceParty sp = partyMapper.selectById(t.getCraftsmanId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("taskNo", t.getTaskNo());
        m.put("goodsNo", g == null ? null : g.getGoodsNo());
        m.put("goodsName", g == null ? null : g.getName());
        m.put("craftsmanName", sp == null ? null : sp.getName());
        m.put("requirement", t.getRequirement());
        m.put("laborFee", t.getLaborFee());
        m.put("deadline", t.getDeadline());
        m.put("status", t.getStatus());
        // 与列表接口同一口径的派生「逾期」，别让详情页自己再算一套
        boolean overdue = List.of("待加工", "在加工").contains(t.getStatus())
                && t.getDeadline() != null && t.getDeadline().isBefore(LocalDate.now());
        m.put("overdue", overdue);
        m.put("overdueDays", overdue ? ChronoUnit.DAYS.between(t.getDeadline(), LocalDate.now()) : 0);
        m.put("urgeCount", t.getUrgeCount() == null ? 0 : t.getUrgeCount());
        m.put("lastUrgeAt", t.getLastUrgeAt());
        DeliveryApply d = deliveryMapper.selectOne(new QueryWrapper<DeliveryApply>()
                .eq("task_id", id).orderByDesc("submit_time").last("limit 1"));
        if (d != null) {
            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("applyNo", d.getApplyNo());
            dm.put("status", d.getStatus());
            dm.put("note", d.getNote());
            dm.put("submitTime", d.getSubmitTime());
            m.put("deliveryApply", dm);
        }
        return Result.ok(m);
    }

    // ---------------- 向已有合同追加寄售货品 ----------------

    public static class ConsignItemReq {
        public String name;
        public Long categoryId;
        public String material;
        public String origin;
        public String shape;
        public String form;
        public BigDecimal weightG;
        public String size;
        public BigDecimal consignPrice;
    }

    @PostMapping("/consignments/{id}/items")
    @RequirePerm(value = {"UC-21"}, roles = {"BOSS"})
    @Transactional
    public Result<Map<String, Object>> addItems(@PathVariable Long id,
                                                @RequestBody List<ConsignItemReq> items) {
        Consignment c = consignMapper.selectById(id);
        if (c == null) throw BizException.notFound("合同不存在");
        if (!"生效".equals(c.getStatus())) throw BizException.conflict("合同已终止或到期，不能追加货品");
        if (items == null || items.isEmpty()) throw BizException.badRequest("货品清单不能为空");

        SourceParty sp = partyMapper.selectById(c.getSupplierId());
        StockIn si = new StockIn();
        si.setInTime(LocalDateTime.now());
        si.setReceiverId(CurrentUser.require().getUserId());
        si.setRemark("寄售追加 " + c.getConsignNo());
        stockInMapper.insert(si);

        List<Map<String, Object>> created = new ArrayList<>();
        for (ConsignItemReq it : items) {
            if (it.consignPrice == null || it.consignPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw BizException.badRequest("寄售价必须大于 0");
            }
            if (it.name == null || it.name.isBlank()) throw BizException.badRequest("品名不能为空");
            Goods g = new Goods();
            g.setGoodsNo(com.jade.common.IdGen.next("JD"));
            g.setName(it.name);
            g.setCategoryId(it.categoryId);
            g.setMaterial(it.material == null ? "未填" : it.material);
            g.setOrigin(it.origin == null ? "未填" : it.origin);
            g.setShape(it.shape == null ? "其他" : it.shape);
            g.setForm(it.form == null ? "山料" : it.form);
            g.setWeightG(it.weightG == null ? BigDecimal.ONE : it.weightG);
            g.setSize(it.size);
            g.setSrcType("寄售");
            g.setSrcParty(sp == null ? null : sp.getName());
            g.setCost(it.consignPrice);
            g.setMinPrice(it.consignPrice.multiply(new BigDecimal("1.25"))
                    .setScale(2, java.math.RoundingMode.HALF_UP));
            g.setInTime(LocalDateTime.now());
            g.setStockInId(si.getId());
            g.setStatus("在柜");
            g.setIsFeatured(0);
            goodsMapper.insert(g);

            ConsignmentItem ci = new ConsignmentItem();
            ci.setConsignmentId(id);
            ci.setGoodsId(g.getId());
            ci.setConsignPrice(it.consignPrice);
            ci.setSettleStatus("未结算");
            itemMapper.insert(ci);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("goodsNo", g.getGoodsNo());
            m.put("consignPrice", it.consignPrice);
            created.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("consignNo", c.getConsignNo());
        data.put("addedCount", created.size());
        data.put("goods", created);
        return Result.ok(data);
    }

    // ---------------- 送检与证书（UC-05） ----------------

    public static class InspectReq {
        public String goodsNo;
        public Long orgId;
        public String conclusion;
        public String issueDate;
    }

    /**
     * 送检登记：货品状态「在柜」→「已送检」，可同时出具证书。
     * 报告 §2.3 UC-05 要求「送检后货品不在展示范围」。
     */
    @PostMapping("/inspections")
    @RequirePerm("UC-05")
    @Transactional
    public Result<Map<String, Object>> inspect(@RequestBody InspectReq req) {
        Goods g = goodsMapper.selectOne(new QueryWrapper<Goods>().eq("goods_no", req.goodsNo));
        if (g == null) throw BizException.notFound("货品不存在");
        if (!"在柜".equals(g.getStatus())) {
            throw BizException.conflict("只有「在柜」货品可以送检，当前为「" + g.getStatus() + "」");
        }
        CertOrg org = certOrgMapper.selectById(req.orgId);
        if (org == null) throw BizException.notFound("鉴定机构不存在");

        Inspection ins = new Inspection();
        ins.setInspTime(LocalDateTime.now());
        ins.setGoodsId(g.getId());
        ins.setOrgId(req.orgId);
        ins.setSubmitterId(CurrentUser.require().getUserId());
        inspectionMapper.insert(ins);

        String from = g.getStatus();
        g.setStatus("已送检");
        goodsMapper.updateById(g);
        writeLog(g.getId(), from, "已送检", "送检", String.valueOf(ins.getId()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("inspectionId", ins.getId());
        data.put("goodsStatus", g.getStatus());

        if (req.conclusion != null && !req.conclusion.isBlank()) {
            Certificate c = new Certificate();
            c.setCertNo("ZS" + com.jade.common.IdGen.next(""));
            c.setOrgId(req.orgId);
            c.setInspId(ins.getId());
            c.setGoodsId(g.getId());
            c.setConclusion(req.conclusion);
            c.setIssueDate(req.issueDate == null ? java.time.LocalDate.now()
                    : java.time.LocalDate.parse(req.issueDate));
            c.setCertQrcode(null);
            certMapper.insert(c);

            // 出证后货品回到在柜，可继续展示销售
            g.setStatus("在柜");
            g.setCertNo(c.getCertNo());
            goodsMapper.updateById(g);
            writeLog(g.getId(), "已送检", "在柜", "出证", c.getCertNo());

            data.put("certNo", c.getCertNo());
            data.put("goodsStatus", g.getStatus());
        }
        return Result.ok(data);
    }

    @GetMapping("/inspections")
    @RequirePerm("UC-05")
    public Result<List<Map<String, Object>>> inspections(@RequestParam(required = false) String goodsNo) {
        QueryWrapper<Inspection> q = new QueryWrapper<>();
        if (goodsNo != null && !goodsNo.isBlank()) {
            Goods g = goodsMapper.selectOne(new QueryWrapper<Goods>().eq("goods_no", goodsNo));
            if (g == null) return Result.ok(List.of());
            q.eq("goods_id", g.getId());
        }
        q.orderByDesc("insp_time").last("limit 100");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Inspection i : inspectionMapper.selectList(q)) {
            Goods g = goodsMapper.selectById(i.getGoodsId());
            CertOrg org = certOrgMapper.selectById(i.getOrgId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.getId());
            m.put("inspTime", i.getInspTime());
            m.put("goodsNo", g == null ? null : g.getGoodsNo());
            m.put("goodsName", g == null ? null : g.getName());
            m.put("orgName", org == null ? null : org.getName());
            Certificate c = certMapper.selectOne(new QueryWrapper<Certificate>()
                    .eq("insp_id", i.getId()).last("limit 1"));
            m.put("certNo", c == null ? null : c.getCertNo());
            m.put("conclusion", c == null ? null : c.getConclusion());
            out.add(m);
        }
        return Result.ok(out);
    }

    @GetMapping("/certificates")
    public Result<List<Map<String, Object>>> certificates(@RequestParam(required = false) String goodsNo) {
        QueryWrapper<Certificate> q = new QueryWrapper<>();
        if (goodsNo != null && !goodsNo.isBlank()) {
            Goods g = goodsMapper.selectOne(new QueryWrapper<Goods>().eq("goods_no", goodsNo));
            if (g == null) return Result.ok(List.of());
            q.eq("goods_id", g.getId());
        }
        q.orderByDesc("issue_date").last("limit 100");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Certificate c : certMapper.selectList(q)) {
            Goods g = goodsMapper.selectById(c.getGoodsId());
            CertOrg org = certOrgMapper.selectById(c.getOrgId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("certNo", c.getCertNo());
            m.put("goodsNo", g == null ? null : g.getGoodsNo());
            m.put("goodsName", g == null ? null : g.getName());
            m.put("orgName", org == null ? null : org.getName());
            m.put("conclusion", c.getConclusion());
            m.put("issueDate", c.getIssueDate());
            out.add(m);
        }
        return Result.ok(out);
    }

    // ---------------- 鉴定机构删除 ----------------

    @DeleteMapping("/cert-orgs/{id}")
    @RequirePerm("UC-07")
    public Result<Void> deleteCertOrg(@PathVariable Long id) {
        Long ref = inspectionMapper.selectCount(new QueryWrapper<Inspection>().eq("org_id", id));
        if (ref != null && ref > 0) {
            Map<String, Object> d = new HashMap<>();
            d.put("refCount", ref);
            throw BizException.conflict("该机构已有 " + ref + " 条送检记录，不能删除", d);
        }
        certOrgMapper.deleteById(id);
        return Result.ok();
    }

    // ---------------- 工具 ----------------

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
}
