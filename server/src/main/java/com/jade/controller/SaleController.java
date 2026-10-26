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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 成交、退货、分期、还款、货款、统计。对应 specs/002-sale。
 */
@RestController
@RequestMapping("/api")
public class SaleController {

    private final SaleMapper saleMapper;
    private final ReturnMapper returnMapper;
    private final InstallmentMapper installmentMapper;
    private final PaymentMapper paymentMapper;
    private final GoodsMapper goodsMapper;
    private final StockLogMapper stockLogMapper;
    private final CustomerMapper customerMapper;
    private final UserMapper userMapper;

    public SaleController(SaleMapper saleMapper, ReturnMapper returnMapper,
                          InstallmentMapper installmentMapper, PaymentMapper paymentMapper,
                          GoodsMapper goodsMapper, StockLogMapper stockLogMapper,
                          CustomerMapper customerMapper, UserMapper userMapper) {
        this.saleMapper = saleMapper;
        this.returnMapper = returnMapper;
        this.installmentMapper = installmentMapper;
        this.paymentMapper = paymentMapper;
        this.goodsMapper = goodsMapper;
        this.stockLogMapper = stockLogMapper;
        this.customerMapper = customerMapper;
        this.userMapper = userMapper;
    }

    // ---------------- 成交 ----------------

    public static class SaleReq {
        public String goodsNo;
        public BigDecimal price;
        public String payMethod;
        public BigDecimal received;
        public Long customerId;
        public Long authorizedBy;
        public String authNote;
    }

    @PostMapping("/sales")
    @RequirePerm("UC-01")
    @Transactional
    public Result<Map<String, Object>> create(@RequestBody SaleReq req) {
        if (req.goodsNo == null || req.goodsNo.isBlank()) throw BizException.badRequest("货品编号不能为空");
        Goods g = goodsMapper.selectOne(new QueryWrapper<Goods>().eq("goods_no", req.goodsNo));
        if (g == null) throw BizException.notFound("货品不存在");
        if (!"在柜".equals(g.getStatus())) {
            throw BizException.conflict("货品当前状态为「" + g.getStatus() + "」，不可成交");
        }
        if (req.price == null || req.price.compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.badRequest("成交价必须大于 0");
        }
        if (req.price.compareTo(g.getMinPrice()) < 0) {
            // 低于底价：authorizedBy 必须是真实存在且启用的老板账号 id，
            // 防止随手传个数字绕过底线（授权操作本身已进 op_log 留痕）
            if (req.authorizedBy == null) {
                throw BizException.unprocessable("成交价低于最低售价，需录入老板授权");
            }
            if (!CurrentUser.require().isBoss()) {
                User auth = userMapper.selectById(req.authorizedBy);
                Long isBoss = auth == null ? 0L : userMapper.selectCount(new QueryWrapper<User>()
                        .eq("id", auth.getId())
                        .inSql("id", "SELECT ur.user_id FROM t_user_role ur "
                                + "JOIN t_role r ON ur.role_id = r.id "
                                + "WHERE r.role_code = 'BOSS' AND ur.user_id = " + auth.getId()));
                if (auth == null || !"启用".equals(auth.getStatus()) || isBoss == null || isBoss == 0) {
                    throw BizException.unprocessable("授权人必须是启用的老板账号");
                }
            }
        }
        if (req.customerId != null && customerMapper.selectById(req.customerId) == null) {
            throw BizException.notFound("客户不存在");
        }
        boolean deposit = "定金".equals(req.payMethod);
        if (req.payMethod == null || (!"全款".equals(req.payMethod) && !deposit)) {
            throw BizException.badRequest("支付方式只能为「全款」或「定金」");
        }
        if (deposit && (req.received == null || req.received.compareTo(BigDecimal.ZERO) <= 0)) {
            // 定金成交必须如实填实收金额，缺省记全款会把「已订」货按全价入账
            throw BizException.badRequest("定金成交必须填写实收定金金额");
        }
        BigDecimal received = req.received == null ? req.price : req.received;
        if (received.compareTo(BigDecimal.ZERO) < 0 || received.compareTo(req.price) > 0) {
            throw BizException.badRequest("实收金额须在 0 与成交价之间");
        }
        // 一件货品同时只能有一条有效成交；已退货的成交不算数，货品回在柜后可以再售
        Long exists = saleMapper.selectCount(new QueryWrapper<Sale>()
                .eq("goods_id", g.getId())
                .notInSql("id", "SELECT sale_id FROM t_return"));
        if (exists != null && exists > 0) {
            throw BizException.conflict("该货品已有成交记录");
        }

        Sale s = new Sale();
        s.setSaleNo(nextNo("CJ"));
        s.setGoodsId(g.getId());
        s.setSaleTime(LocalDateTime.now());
        s.setPrice(req.price);
        s.setPayMethod(req.payMethod);
        s.setReceived(received);
        s.setCustomerId(req.customerId);
        s.setOperatorId(CurrentUser.require().getUserId());
        s.setRecordedAt(LocalDateTime.now());
        saleMapper.insert(s);

        String to = deposit ? "已订" : "已售";
        String from = g.getStatus();
        g.setStatus(to);
        goodsMapper.updateById(g);
        writeLog(g.getId(), from, to, "成交", s.getSaleNo());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", s.getId());
        data.put("saleNo", s.getSaleNo());
        data.put("goodsNo", g.getGoodsNo());
        data.put("goodsName", g.getName());
        data.put("price", s.getPrice());
        data.put("payMethod", s.getPayMethod());
        data.put("received", s.getReceived());
        data.put("goodsStatus", g.getStatus());
        data.put("saleTime", s.getSaleTime());
        return Result.ok(data);
    }

    /** 转成 LIKE 的匹配串，并把 ! % _ 按 ESCAPE '!' 的规则转义（先转义转义符本身） */
    private static String likePattern(String raw) {
        return "%" + raw.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    @GetMapping("/sales")
    public Result<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Long customerId,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        if (size > 100) size = 100;
        QueryWrapper<Sale> q = new QueryWrapper<>();
        if (customerId != null) q.eq("customer_id", customerId);
        if (keyword != null && !keyword.isBlank()) {
            // 关键字过滤下沉 SQL，分页后才过滤会让 total 与页内条数对不上。
            // 用 apply 的 {0} 占位（MyBatis-Plus 会转成 JDBC 绑定参数），不把关键字拼进 SQL 串：
            // 拼进去除了注释写的注入风险，% 和 _ 还会被当成 LIKE 通配符，搜「100%」能捞回一堆不相干的单。
            // 转义符显式写成 ESCAPE '!'，不依赖 MySQL 的反斜杠行为（NO_BACKSLASH_ESCAPES）。
            String kw = likePattern(keyword);
            q.and(w -> w.apply("sale_no LIKE {0} ESCAPE '!'", kw)
                    .or().apply("goods_id IN (SELECT id FROM t_goods"
                                    + " WHERE name LIKE {0} ESCAPE '!' OR goods_no LIKE {1} ESCAPE '!')",
                            kw, kw));
        }
        q.orderByDesc("sale_time");
        Page<Sale> p = saleMapper.selectPage(new Page<>(page, size), q);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Sale s : p.getRecords()) {
            Goods g = goodsMapper.selectById(s.getGoodsId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("saleNo", s.getSaleNo());
            m.put("goodsNo", g == null ? null : g.getGoodsNo());
            m.put("goodsName", g == null ? null : g.getName());
            m.put("price", s.getPrice());
            m.put("payMethod", s.getPayMethod());
            m.put("received", s.getReceived());
            m.put("customerId", s.getCustomerId());
            m.put("saleTime", s.getSaleTime());
            Long r = returnMapper.selectCount(new QueryWrapper<Return>().eq("sale_id", s.getId()));
            m.put("returned", r != null && r > 0);
            items.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", p.getTotal());
        data.put("page", page);
        data.put("size", size);
        data.put("items", items);
        return Result.ok(data);
    }

    @GetMapping("/sales/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Sale s = saleMapper.selectById(id);
        if (s == null) throw BizException.notFound("成交记录不存在");
        Goods g = goodsMapper.selectById(s.getGoodsId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("saleNo", s.getSaleNo());
        m.put("goodsNo", g == null ? null : g.getGoodsNo());
        m.put("goodsName", g == null ? null : g.getName());
        m.put("price", s.getPrice());
        m.put("payMethod", s.getPayMethod());
        m.put("received", s.getReceived());
        m.put("customerId", s.getCustomerId());
        m.put("saleTime", s.getSaleTime());
        m.put("recordedAt", s.getRecordedAt());
        Return r = returnMapper.selectOne(new QueryWrapper<Return>().eq("sale_id", id).last("limit 1"));
        if (r != null) {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("returnTime", r.getReturnTime());
            rm.put("reason", r.getReason());
            rm.put("refund", r.getRefund());
            m.put("returnRecord", rm);
        }
        Installment ins = installmentMapper.selectOne(new QueryWrapper<Installment>().eq("sale_id", id).last("limit 1"));
        if (ins != null) {
            m.put("installment", installmentVO(ins));
        }
        // 可退天数
        long days = ChronoUnit.DAYS.between(s.getSaleTime().toLocalDate(), LocalDate.now());
        m.put("daysSinceSale", days);
        m.put("returnable", days <= 7 && r == null);
        return Result.ok(m);
    }

    // ---------------- 退货 ----------------

    public static class ReturnReq {
        public String reason;
        public BigDecimal refund;
        public String note;
    }

    @PostMapping("/sales/{id}/returns")
    @RequirePerm("UC-03")
    @Transactional
    public Result<Map<String, Object>> doReturn(@PathVariable Long id, @RequestBody ReturnReq req) {
        Sale s = saleMapper.selectById(id);
        if (s == null) throw BizException.notFound("成交记录不存在");
        Long exists = returnMapper.selectCount(new QueryWrapper<Return>().eq("sale_id", id));
        if (exists != null && exists > 0) throw BizException.conflict("该成交已退货，不允许重复退货");
        long days = ChronoUnit.DAYS.between(s.getSaleTime().toLocalDate(), LocalDate.now());
        if (days > 7) throw BizException.unprocessable("已超过成交后 7 天，不予办理");
        if (!List.of("真假存疑", "质量问题").contains(req.reason)) {
            throw BizException.unprocessable("该原因不在可退范围内");
        }
        BigDecimal paid = s.getReceived() == null ? s.getPrice() : s.getReceived();
        BigDecimal refund = req.refund == null ? paid : req.refund;
        // 退款以实收为上限（定金成交没收到全价就不能退全价），差额需要说明
        if (refund.compareTo(BigDecimal.ZERO) <= 0 || refund.compareTo(paid) > 0) {
            throw BizException.unprocessable("退款金额须大于 0 且不超过实收金额");
        }
        if (refund.compareTo(paid) != 0 && (req.note == null || req.note.isBlank())) {
            throw BizException.unprocessable("退款金额与实收不一致，需说明差额原因");
        }

        Return r = new Return();
        r.setReturnTime(LocalDateTime.now());
        r.setSaleId(id);
        r.setReason(req.reason);
        r.setRefund(refund);
        r.setOperatorId(CurrentUser.require().getUserId());
        returnMapper.insert(r);

        Goods g = goodsMapper.selectById(s.getGoodsId());
        String from = g.getStatus();
        g.setStatus("在柜");
        goodsMapper.updateById(g);
        writeLog(g.getId(), from, "在柜", "退货", s.getSaleNo());

        // 退货后原分期计划随即终止：置为已结清，避免退回的货还挂着待还计划
        Installment ins = installmentMapper.selectOne(
                new QueryWrapper<Installment>().eq("sale_id", id).last("limit 1"));
        if (ins != null && !"已结清".equals(ins.getStatus())) {
            // 保留真实已还金额，只置结清状态；虚构 paidAmount=全价会让台账对不上实收
            ins.setStatus("已结清");
            installmentMapper.updateById(ins);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("returnId", r.getId());
        data.put("saleNo", s.getSaleNo());
        data.put("goodsStatus", g.getStatus());
        data.put("installmentTerminated", ins != null);
        data.put("refund", refund);
        data.put("refundedAt", r.getReturnTime());
        return Result.ok(data);
    }

    // ---------------- 分期 ----------------

    public static class InstallmentReq {
        public Integer periods;
        public String firstDueDate;
    }

    @PostMapping("/sales/{id}/installment")
    @RequirePerm("UC-24")
    @Transactional
    public Result<Map<String, Object>> createInstallment(@PathVariable Long id, @RequestBody InstallmentReq req) {
        Sale s = saleMapper.selectById(id);
        if (s == null) throw BizException.notFound("成交记录不存在");
        if (req.periods == null || req.periods < 1) throw BizException.badRequest("分期期数必须大于 0");
        Long exists = installmentMapper.selectCount(new QueryWrapper<Installment>().eq("sale_id", id));
        if (exists != null && exists > 0) throw BizException.conflict("一份成交只有一份分期计划");

        BigDecimal per = s.getPrice().divide(BigDecimal.valueOf(req.periods), 2, RoundingMode.DOWN);
        Installment i = new Installment();
        i.setPlanNo(nextNo("FQ"));
        i.setSaleId(id);
        i.setPeriods(req.periods);
        i.setPeriodAmount(per);
        i.setPaidAmount(BigDecimal.ZERO);
        i.setNextDueDate(req.firstDueDate == null ? LocalDate.now().plusMonths(1) : LocalDate.parse(req.firstDueDate));
        i.setStartDate(LocalDate.now());
        i.setStatus("未还");
        installmentMapper.insert(i);
        return Result.ok(installmentVO(i));
    }

    @GetMapping("/installments")
    public Result<Map<String, Object>> listInstallments(@RequestParam(required = false) String status,
                                                        @RequestParam(required = false) Boolean overdueOnly,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        if (size > 100) size = 100;
        QueryWrapper<Installment> q = new QueryWrapper<>();
        // 「逾期」是派生态（未还且过 next_due_date），过滤必须在分页前下沉 SQL，
        // 否则 total 与列表对不上、末页可能整页被滤空
        if ("逾期".equals(status) || Boolean.TRUE.equals(overdueOnly)) {
            q.eq("status", "未还").lt("next_due_date", LocalDate.now());
        } else if (status != null && !status.isBlank()) {
            q.eq("status", status);
        }
        q.orderByAsc("next_due_date");
        Page<Installment> p = installmentMapper.selectPage(new Page<>(page, size), q);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Installment i : p.getRecords()) {
            items.add(installmentVO(i));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", p.getTotal());
        data.put("page", page);
        data.put("size", size);
        data.put("items", items);
        return Result.ok(data);
    }

    @GetMapping("/installments/{id}")
    public Result<Map<String, Object>> installmentDetail(@PathVariable Long id) {
        Installment i = installmentMapper.selectById(id);
        if (i == null) throw BizException.notFound("分期计划不存在");
        return Result.ok(installmentVO(i));
    }

    public static class PayReq {
        public BigDecimal amount;
        public String payTime;
    }

    @PostMapping("/installments/{id}/payments")
    @RequirePerm("UC-25")
    @Transactional
    public Result<Map<String, Object>> pay(@PathVariable Long id, @RequestBody PayReq req) {
        Installment i = installmentMapper.selectById(id);
        if (i == null) throw BizException.notFound("分期计划不存在");
        if ("已结清".equals(i.getStatus())) throw BizException.conflict("该计划已结清");
        if (req.amount == null || req.amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.badRequest("还款金额必须大于 0");
        }
        Sale s = saleMapper.selectById(i.getSaleId());
        BigDecimal total = s.getPrice();
        BigDecimal remaining = total.subtract(i.getPaidAmount());
        if (req.amount.compareTo(remaining) > 0) {
            throw BizException.badRequest("还款金额不能超过未还余额 " + remaining);
        }
        // 原子累加防并发丢失更新；同时用行级条件「累加后不超总额」拦住
        // 两笔并发各自通过余额检查后合计超额的情况（0 行受更新 = 已被别人还满）
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Installment> uw =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        uw.eq("id", id)
          .apply("paid_amount + {0} <= " + total.toPlainString(), req.amount)
          .setSql("paid_amount = paid_amount + " + req.amount.toPlainString());
        int updated = installmentMapper.update(null, uw);
        if (updated == 0) {
            throw BizException.conflict("还款金额不能超过未还余额");
        }
        i = installmentMapper.selectById(id);
        BigDecimal paid = i.getPaidAmount();
        if (paid.compareTo(total) >= 0) {
            i.setStatus("已结清");
            // UC-01 规则④：分期留货的定金成交，尾款付清才计为完成销售
            Goods g = goodsMapper.selectById(s.getGoodsId());
            if (g != null && "已订".equals(g.getStatus())) {
                g.setStatus("已售");
                goodsMapper.updateById(g);
                writeLog(g.getId(), "已订", "已售", "分期结清", s.getSaleNo());
            }
        } else {
            i.setNextDueDate(i.getNextDueDate().plusMonths(1));
        }
        installmentMapper.updateById(i);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("planNo", i.getPlanNo());
        data.put("paidAmount", i.getPaidAmount());
        data.put("remaining", total.subtract(paid));
        data.put("nextDueDate", i.getNextDueDate());
        data.put("status", i.getStatus());
        return Result.ok(data);
    }

    private Map<String, Object> installmentVO(Installment i) {
        Sale s = saleMapper.selectById(i.getSaleId());
        BigDecimal total = s == null ? BigDecimal.ZERO : s.getPrice();
        BigDecimal remaining = total.subtract(i.getPaidAmount());
        boolean overdue = remaining.compareTo(BigDecimal.ZERO) > 0
                && i.getNextDueDate() != null && i.getNextDueDate().isBefore(LocalDate.now());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getId());
        m.put("planNo", i.getPlanNo());
        m.put("saleId", i.getSaleId());
        m.put("saleNo", s == null ? null : s.getSaleNo());
        m.put("periods", i.getPeriods());
        m.put("periodAmount", i.getPeriodAmount());
        m.put("totalAmount", total);
        m.put("paidAmount", i.getPaidAmount());
        m.put("remaining", remaining);
        m.put("nextDueDate", i.getNextDueDate());
        m.put("status", i.getStatus());
        m.put("displayStatus", "已结清".equals(i.getStatus()) ? "已结清" : (overdue ? "逾期" : "未还"));
        m.put("overdueDays", overdue
                ? ChronoUnit.DAYS.between(i.getNextDueDate(), LocalDate.now()) : 0);
        return m;
    }

    // ---------------- 货款 ----------------

    public static class PaymentReq {
        public String bizType;
        public String relatedType;
        public String relatedNo;
        public BigDecimal amount;
        public String direction;
        public String payTime;
    }

    @PostMapping("/payments")
    @RequirePerm("UC-19")
    public Result<Map<String, Object>> addPayment(@RequestBody PaymentReq req) {
        if (!List.of("采购货款", "调货货款", "加工工费", "寄售货款").contains(req.bizType)) {
            throw BizException.badRequest("业务类型不合法");
        }
        if (!List.of("入库批次", "调货记录", "加工任务", "台账结算").contains(req.relatedType)) {
            throw BizException.badRequest("单据类型不合法");
        }
        if (!List.of("支付", "收取").contains(req.direction)) {
            throw BizException.badRequest("收付方向不合法");
        }
        Payment p = new Payment();
        if (req.amount == null || req.amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.badRequest("收款金额必须大于 0");
        }
        p.setRecordNo(nextNo("HK"));
        p.setBizType(req.bizType);
        p.setRelatedType(req.relatedType);
        p.setRelatedNo(req.relatedNo);
        p.setAmount(req.amount);
        p.setDirection(req.direction);
        p.setOperatorId(CurrentUser.require().getUserId());
        p.setPayTime(req.payTime == null ? LocalDateTime.now()
                : LocalDateTime.parse(req.payTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        paymentMapper.insert(p);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", p.getId());
        data.put("recordNo", p.getRecordNo());
        return Result.ok(data);
    }

    @GetMapping("/payments")
    @RequirePerm("UC-19")
    public Result<Map<String, Object>> listPayments(@RequestParam(required = false) String bizType,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        if (size > 100) size = 100;
        QueryWrapper<Payment> q = new QueryWrapper<>();
        if (bizType != null && !bizType.isBlank()) q.eq("biz_type", bizType);
        q.orderByDesc("pay_time");
        Page<Payment> p = paymentMapper.selectPage(new Page<>(page, size), q);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", p.getTotal());
        data.put("page", page);
        data.put("size", size);
        data.put("items", p.getRecords());
        return Result.ok(data);
    }

    // ---------------- 统计 ----------------

    @GetMapping("/stats/sales")
    @RequirePerm("UC-06")
    public Result<Map<String, Object>> salesStats(@RequestParam String from, @RequestParam String to) {
        LocalDateTime f = LocalDate.parse(from).atStartOfDay();
        LocalDateTime t = LocalDate.parse(to).plusDays(1).atStartOfDay();
        List<Sale> sales = saleMapper.selectList(new QueryWrapper<Sale>()
                .ge("sale_time", f).lt("sale_time", t));
        BigDecimal amount = BigDecimal.ZERO;
        Set<Long> customers = new HashSet<>();
        for (Sale s : sales) {
            amount = amount.add(s.getPrice());
            if (s.getCustomerId() != null) customers.add(s.getCustomerId());
        }
        // 退货冲减
        BigDecimal refund = BigDecimal.ZERO;
        List<Return> rs = returnMapper.selectList(new QueryWrapper<Return>()
                .ge("return_time", f).lt("return_time", t));
        for (Return r : rs) refund = refund.add(r.getRefund());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("range", Map.of("from", from, "to", to));
        data.put("amount", amount);
        data.put("returnAmount", refund);
        data.put("netAmount", amount.subtract(refund));
        data.put("customerCount", customers.size());
        data.put("saleCount", sales.size());
        return Result.ok(data);
    }

    @GetMapping("/stats/turnover")
    @RequirePerm("UC-06")
    public Result<Map<String, Object>> turnover(@RequestParam String from, @RequestParam String to) {
        LocalDateTime f = LocalDate.parse(from).atStartOfDay();
        LocalDateTime t = LocalDate.parse(to).plusDays(1).atStartOfDay();
        List<Sale> sales = saleMapper.selectList(new QueryWrapper<Sale>()
                .ge("sale_time", f).lt("sale_time", t));
        BigDecimal wsum = BigDecimal.ZERO;
        BigDecimal w = BigDecimal.ZERO;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Sale s : sales) {
            Goods g = goodsMapper.selectById(s.getGoodsId());
            if (g == null || g.getInTime() == null || g.getMinPrice() == null) continue;
            long days = ChronoUnit.DAYS.between(g.getInTime().toLocalDate(), s.getSaleTime().toLocalDate());
            wsum = wsum.add(BigDecimal.valueOf(days).multiply(g.getMinPrice()));
            w = w.add(g.getMinPrice());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("goodsNo", g.getGoodsNo());
            r.put("name", g.getName());
            r.put("days", days);
            r.put("minPrice", g.getMinPrice());
            rows.add(r);
        }
        BigDecimal weighted = w.compareTo(BigDecimal.ZERO) > 0
                ? wsum.divide(w, 1, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        rows.sort((a, b) -> Long.compare((Long) b.get("days"), (Long) a.get("days")));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("range", Map.of("from", from, "to", to));
        data.put("weightedDays", weighted);
        data.put("sampleCount", rows.size());
        data.put("slowest", rows.size() > 10 ? rows.subList(0, 10) : rows);
        return Result.ok(data);
    }

    // ---------------- 工具 ----------------

    private String nextNo(String prefix) {
        return IdGen.next(prefix);
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
}
