package com.jade.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jade.common.BizException;
import com.jade.common.CurrentUser;
import com.jade.common.RequirePerm;
import com.jade.common.IdGen;
import com.jade.common.Result;
import com.jade.entity.*;
import com.jade.mapper.*;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletRequest;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 客户档案、免登录浏览、看货预约、商品预定、到店跟进。
 * 对应 specs/003-customer。
 */
@RestController
@RequestMapping("/api")
public class CustomerController {

    private static final String[] CHANNELS =
            {"闲鱼", "微信朋友圈", "朋友介绍", "线下", "直播", "老客户复购", "其他"};
    private static final String[] TAGS = {"大客户", "懂行", "爽快", "要还价"};
    /** 额度占用口径：只有「已达成」释放，见 specs/003-customer/data-model.md */
    private static final List<String> QUOTA_STATUS =
            List.of("待到店", "已联系", "已爽约", "已取消");
    /** 预定保留时长：柜台建单后 72 小时不处理自动失效，释放货品 */
    private static final int BOOKING_HOLD_HOURS = 72;
    /** 预约可约窗口：最早当前时间后 2 小时，最晚当前时间后 3 天（与客户侧日期表单一致） */
    private static final long RESERVE_LEAD_HOURS = 2;
    private static final long RESERVE_SPAN_DAYS = 3;
    /** 门店地址：预约/改期成功反馈带回（用户 2026-10-25 拍板的虚构地址，真定铺面后与 H5/门户四处同步改） */
    private static final String STORE_ADDRESS = "乌鲁木齐市新市区鲤鱼山南路 66 号（鲤鱼山公园东门旁）";
    /** 到店时间后保留 60 分钟，过后预约自动失效、货品与时段释放 */
    private static final int RESERVE_HOLD_MINUTES = 60;
    /** 到店时间按半小时对齐 */
    private static final int SLOT_STEP_MINUTES = 30;
    /** 同一来源 IP 最多同时持有的有效预约数，防刷单把库表打爆 */
    private static final int MAX_ACTIVE_PER_IP = 3;
    /** 同一来源 IP 最多同时持有的有效预定数，与预约同口径（手机号可编造，必须按 IP 限） */
    private static final int MAX_ACTIVE_BOOKING_PER_IP = 3;
    /** 同一来源 IP 累计违约（到期未到店）上限，超过则需门店手动解除 */
    private static final int MAX_VIOLATIONS_PER_IP = 10;
    private static final DateTimeFormatter SLOT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** 客户侧参考价上界的换算系数：最低售价 × 1.25（与寄售价→最低售价的口径一致），取整到十位 */
    private static final BigDecimal PUBLIC_PRICE_FACTOR = new BigDecimal("1.25");
    private static final BigDecimal TEN = new BigDecimal("10");

    private final CustomerMapper customerMapper;
    private final CustomerTagMapper tagMapper;
    private final GoodsMapper goodsMapper;
    private final ReservationMapper reservationMapper;
    private final BookingMapper bookingMapper;
    private final VisitApptMapper visitApptMapper;
    private final SaleMapper saleMapper;
    private final CategoryMapper categoryMapper;

    public CustomerController(CustomerMapper customerMapper, CustomerTagMapper tagMapper,
                              GoodsMapper goodsMapper, ReservationMapper reservationMapper,
                              BookingMapper bookingMapper, VisitApptMapper visitApptMapper,
                              SaleMapper saleMapper, CategoryMapper categoryMapper) {
        this.customerMapper = customerMapper;
        this.tagMapper = tagMapper;
        this.goodsMapper = goodsMapper;
        this.reservationMapper = reservationMapper;
        this.bookingMapper = bookingMapper;
        this.visitApptMapper = visitApptMapper;
        this.saleMapper = saleMapper;
        this.categoryMapper = categoryMapper;
    }

    // ==================== 客户档案 ====================

    @GetMapping("/customers")
    @RequirePerm("UC-04")
    public Result<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String tag,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        if (size > 100) size = 100;
        QueryWrapper<Customer> q = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like("name", keyword).or().like("phone", keyword)
                    .or().like("customer_no", keyword).or().like("wechat", keyword));
        }
        if (tag != null && !tag.isBlank()) {
            List<Long> ids = tagMapper.selectList(new QueryWrapper<CustomerTag>().eq("tag", tag))
                    .stream().map(CustomerTag::getCustomerId).toList();
            if (ids.isEmpty()) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("total", 0);
                empty.put("items", List.of());
                return Result.ok(empty);
            }
            q.in("id", ids);
        }
        q.orderByDesc("id");
        Page<Customer> p = customerMapper.selectPage(new Page<>(page, size), q);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Customer c : p.getRecords()) items.add(toVO(c));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", p.getTotal());
        data.put("page", page);
        data.put("size", size);
        data.put("items", items);
        return Result.ok(data);
    }

    @GetMapping("/customers/{id}")
    @RequirePerm("UC-04")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Customer c = customerMapper.selectById(id);
        if (c == null) throw BizException.notFound("客户不存在");
        return Result.ok(toVO(c));
    }

    @GetMapping("/customers/{id}/purchases")
    @RequirePerm("UC-04")
    public Result<List<Map<String, Object>>> purchases(@PathVariable Long id) {
        List<Sale> sales = saleMapper.selectList(
                new QueryWrapper<Sale>().eq("customer_id", id).orderByDesc("sale_time"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Sale s : sales) {
            Goods g = goodsMapper.selectById(s.getGoodsId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("saleNo", s.getSaleNo());
            m.put("goodsName", g == null ? null : g.getName());
            m.put("price", s.getPrice());
            m.put("saleTime", s.getSaleTime());
            out.add(m);
        }
        return Result.ok(out);
    }

    public static class CustomerReq {
        public String name;
        public String phone;
        public String wechat;
        public String sourceChannel;
        public Long introducerId;
        public String prefCategory;
        public String prefSize;
        public BigDecimal prefBudget;
        public List<String> tags;
        public String remark;
    }

    @PostMapping("/customers")
    @RequirePerm("UC-04")
    @Transactional
    public Result<Map<String, Object>> create(@RequestBody CustomerReq req) {
        if (req.name == null || req.name.isBlank()) throw BizException.badRequest("姓名不能为空");
        if (req.phone == null || !req.phone.matches("\\d{11}")) {
            throw BizException.badRequest("手机号格式不正确");
        }
        if (!Arrays.asList(CHANNELS).contains(req.sourceChannel)) {
            throw BizException.badRequest("来源渠道不合法");
        }
        if ("朋友介绍".equals(req.sourceChannel) && req.introducerId == null) {
            throw BizException.badRequest("朋友介绍需选择介绍人");
        }
        Customer exists = customerMapper.selectOne(new QueryWrapper<Customer>().eq("phone", req.phone));
        if (exists != null) {
            Map<String, Object> d = toVO(exists);
            throw BizException.conflict("该手机号已建档", d);
        }
        Customer c = new Customer();
        c.setCustomerNo(nextNo("KH"));
        c.setName(req.name);
        c.setPhone(req.phone);
        c.setWechat(req.wechat);
        c.setSourceChannel(req.sourceChannel);
        c.setIntroducerId(req.introducerId);
        c.setPrefCategory(req.prefCategory);
        c.setPrefSize(req.prefSize);
        c.setPrefBudget(req.prefBudget);
        c.setRemark(req.remark);
        try {
            customerMapper.insert(c);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw BizException.conflict("该手机号已建档");
        }
        saveTags(c.getId(), req.tags);
        return Result.ok(toVO(c));
    }

    @PutMapping("/customers/{id}")
    @RequirePerm("UC-04")
    @Transactional
    public Result<Void> update(@PathVariable Long id, @RequestBody CustomerReq req) {
        Customer c = customerMapper.selectById(id);
        if (c == null) throw BizException.notFound("客户不存在");
        if (req.name != null) c.setName(req.name);
        if (req.wechat != null) c.setWechat(req.wechat);
        if (req.prefCategory != null) c.setPrefCategory(req.prefCategory);
        if (req.prefSize != null) c.setPrefSize(req.prefSize);
        if (req.prefBudget != null) c.setPrefBudget(req.prefBudget);
        if (req.remark != null) c.setRemark(req.remark);
        customerMapper.updateById(c);
        if (req.tags != null) {
            tagMapper.delete(new QueryWrapper<CustomerTag>().eq("customer_id", id));
            saveTags(id, req.tags);
        }
        return Result.ok();
    }

    @PutMapping("/customers/{id}/tags")
    @RequirePerm("UC-04")
    @Transactional
    public Result<Void> setTags(@PathVariable Long id, @RequestBody Map<String, List<String>> body) {
        tagMapper.delete(new QueryWrapper<CustomerTag>().eq("customer_id", id));
        saveTags(id, body.get("tags"));
        return Result.ok();
    }

    // ==================== 公开：浏览 ====================

    /**
     * 客户侧参考价上界。库里只有最低售价（底价），对外按 1.25 倍换算成报价再取整到十位；
     * 只下发这一个数，成本、最低售价、调货价都不出接口（FR-010）。
     */
    private BigDecimal publicPriceUpper(Goods g) {
        if (g == null || g.getMinPrice() == null) return null;
        return g.getMinPrice().multiply(PUBLIC_PRICE_FACTOR)
                .divide(TEN, 0, RoundingMode.HALF_UP).multiply(TEN);
    }

    /** 客户侧类别下拉：只暴露 id 与名称，不含任何经营字段 */
    @GetMapping("/public/categories")
    public Result<List<Map<String, Object>>> publicCategories() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Category c : categoryMapper.selectList(new QueryWrapper<Category>().orderByAsc("id"))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            out.add(m);
        }
        return Result.ok(out);
    }

    @GetMapping("/public/goods")
    public Result<Map<String, Object>> publicList(@RequestParam(required = false) Boolean featured,
                                                  @RequestParam(required = false) Long categoryId,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Boolean reservable,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        if (size > 100) size = 100;
        QueryWrapper<Goods> q = new QueryWrapper<>();
        q.eq("status", "在柜");
        if (Boolean.TRUE.equals(featured)) q.eq("is_featured", 1);
        if (categoryId != null) q.eq("category_id", categoryId);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like("name", keyword).or().like("goods_no", keyword));
        }
        // 预约入口要求只看可约：把有效预约占用的货品整批排除。过滤必须在分页前，total 才正确。
        // 口径与 occupiedGoodsIds/isActive 一致：待确认且在到店时间后 60 分钟保留期内
        if (Boolean.TRUE.equals(reservable)) {
            List<Reservation> act = reservationMapper.selectList(new QueryWrapper<Reservation>()
                    .eq("status", "待确认")
                    .gt("expect_slot", java.time.LocalDateTime.now().minusMinutes(RESERVE_HOLD_MINUTES)));
            java.util.Set<Long> occ = new java.util.HashSet<>();
            for (Reservation r : act) occ.add(r.getGoodsId());
            if (!occ.isEmpty()) q.notIn("id", occ);
        }
        q.orderByDesc("is_featured").orderByDesc("in_time");
        Page<Goods> p = goodsMapper.selectPage(new Page<>(page, size), q);
        Map<Long, String> cats = new HashMap<>();
        for (Category c : categoryMapper.selectList(null)) cats.put(c.getId(), c.getName());
        java.util.Set<Long> occupied = occupiedGoodsIds(p.getRecords(), java.time.LocalDateTime.now());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Goods g : p.getRecords()) {
            // 公开 VO：只给参考价上界，不含成本/最低售价/调货价（FR-010）
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("goodsNo", g.getGoodsNo());
            m.put("name", g.getName());
            m.put("categoryName", cats.get(g.getCategoryId()));
            m.put("material", g.getMaterial());
            m.put("origin", g.getOrigin());
            m.put("shape", g.getShape());
            m.put("weightG", g.getWeightG());
            m.put("size", g.getSize());
            m.put("image", g.getImage());
            m.put("featured", g.getIsFeatured() != null && g.getIsFeatured() == 1);
            m.put("priceUpper", publicPriceUpper(g));
            m.put("reservable", !occupied.contains(g.getId()));
            items.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", p.getTotal());
        data.put("page", page);
        data.put("size", size);
        data.put("items", items);
        return Result.ok(data);
    }

    @GetMapping("/public/goods/{goodsNo}")
    public Result<Map<String, Object>> publicDetail(@PathVariable String goodsNo) {
        Goods g = goodsMapper.selectOne(new QueryWrapper<Goods>().eq("goods_no", goodsNo));
        // 成交过的货品不再展示（FR-012）
        if (g == null || !"在柜".equals(g.getStatus())) {
            throw BizException.notFound("货品不存在或已下架");
        }
        Category c = categoryMapper.selectById(g.getCategoryId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("goodsNo", g.getGoodsNo());
        m.put("name", g.getName());
        m.put("categoryName", c == null ? null : c.getName());
        m.put("material", g.getMaterial());
        m.put("origin", g.getOrigin());
        m.put("shape", g.getShape());
        m.put("form", g.getForm());
        m.put("weightG", g.getWeightG());
        m.put("size", g.getSize());
        m.put("image", g.getImage());
        m.put("certNo", g.getCertNo());
        m.put("featured", g.getIsFeatured() != null && g.getIsFeatured() == 1);
        m.put("priceUpper", publicPriceUpper(g));
        LocalDateTime now2 = LocalDateTime.now();
        boolean taken = false;
        List<Reservation> rs = reservationMapper.selectList(new QueryWrapper<Reservation>()
                .eq("goods_id", g.getId()).eq("status", "待确认"));
        for (Reservation r : rs) {
            if (isActive(r.getExpectSlot(), now2)) {
                taken = true;
                break;
            }
        }
        m.put("reservable", !taken);
        return Result.ok(m);
    }

    // ==================== 公开：预约 ====================

    public static class ReservationReq {
        public String goodsNo;
        public String name;
        public String phone;
        public String expectSlot;
    }

    @PostMapping("/public/reservations")
    @Transactional
    public Result<Map<String, Object>> reserve(@RequestBody ReservationReq req, HttpServletRequest httpReq) {
        if (req.phone == null || !req.phone.matches("\\d{11}")) {
            throw BizException.badRequest("手机号格式不正确");
        }
        if (req.name == null || req.name.isBlank()) throw BizException.badRequest("姓名不能为空");
        // 到店时间：yyyy-MM-dd HH:mm、半小时对齐、落在 [现在+2 小时, 现在+3 天]
        if (req.expectSlot == null || req.expectSlot.isBlank()) {
            throw BizException.badRequest("请选择到店日期与时间");
        }
        LocalDateTime slot;
        try {
            slot = LocalDateTime.parse(req.expectSlot.trim(), SLOT_FMT);
        } catch (DateTimeParseException e) {
            throw BizException.badRequest("到店时间格式应为 yyyy-MM-dd HH:mm");
        }
        if (slot.getMinute() % SLOT_STEP_MINUTES != 0 || slot.getSecond() != 0) {
            throw BizException.badRequest("到店时间请选择整点或半点");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime earliest = now.plusHours(RESERVE_LEAD_HOURS);
        LocalDateTime latest = now.plusDays(RESERVE_SPAN_DAYS);
        if (slot.isBefore(earliest)) {
            throw BizException.badRequest("最早可约 " + earliest.format(SLOT_FMT) + "，请选择更晚的时间");
        }
        if (slot.isAfter(latest)) {
            throw BizException.badRequest("最晚可约 " + latest.format(SLOT_FMT) + "，请选择更早的时间");
        }
        Goods g = goodsMapper.selectOne(new QueryWrapper<Goods>().eq("goods_no", req.goodsNo));
        if (g == null || !"在柜".equals(g.getStatus())) throw BizException.notFound("货品不存在或已下架");

        String slotText = slot.format(SLOT_FMT);
        expireOutdated();

        // 同一手机号、同一货品已有未失效预约：按改期处理，只能提前
        Reservation own = activeReservation(g.getId(), req.phone, now);
        if (own != null) {
            if (own.getExpectSlot() != null && !slot.isBefore(own.getExpectSlot())) {
                throw BizException.conflict("只能提前，如需推迟请联系门店");
            }
            ensureSlotFree(slot, own.getId(), now);
            own.setName(req.name);
            own.setExpectSlot(slot);
            reservationMapper.updateById(own);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("resvNo", own.getResvNo());
            data.put("expectSlot", slotText);
            data.put("address", STORE_ADDRESS);
            data.put("tip", "已为您改到更早的时间，预约编号不变");
            return Result.ok(data);
        }

        // 整件货独占：他人有效预约期内不可再约
        List<Reservation> others = reservationMapper.selectList(new QueryWrapper<Reservation>()
                .eq("goods_id", g.getId()).eq("status", "待确认"));
        for (Reservation r : others) {
            if (isActive(r.getExpectSlot(), now)) {
                throw BizException.conflict("该货品已被预约，可联系门店或选择其他货品");
            }
        }
        // 同一半小时时段只安排一次接待，到店与上门共用时段
        ensureSlotFree(slot, null, now);

        // 限流落库：历史违约与当前占用分开算
        String ip = clientIp(httpReq);
        int violations = reservationMapper.selectCount(new QueryWrapper<Reservation>()
                .eq("ip", ip).eq("status", "已失效").eq("exempt", 0)).intValue();
        if (violations >= MAX_VIOLATIONS_PER_IP) {
            throw new BizException(429, "检测到多次预约未到店，请联系门店解除限制");
        }
        int held = reservationMapper.selectCount(new QueryWrapper<Reservation>()
                .eq("ip", ip).eq("status", "待确认")
                .gt("expect_slot", now.minusMinutes(RESERVE_HOLD_MINUTES))).intValue();
        if (held >= MAX_ACTIVE_PER_IP) {
            throw new BizException(429, "同一网络最多同时保留 3 条预约，请到店或等原预约失效后再约");
        }
        Reservation r = new Reservation();
        r.setResvNo(nextNo("YY"));
        r.setGoodsId(g.getId());
        r.setName(req.name);
        r.setPhone(req.phone);
        r.setIp(ip);
        r.setExpectSlot(slot);
        r.setStatus("待确认");
        r.setExempt(0);
        try {
            reservationMapper.insert(r);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 唯一索引兜底：并发下时段或货品刚被他人抢走
            throw BizException.conflict("该时段或货品刚被他人约走，请刷新后重试");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resvNo", r.getResvNo());
        data.put("expectSlot", r.getExpectSlot() == null ? null : r.getExpectSlot().format(SLOT_FMT));
        data.put("address", STORE_ADDRESS);
        data.put("tip", "请截图保存预约编号");
        return Result.ok(data);
    }

    // ---------- 预约限制（老板手动解除） ----------

    /** 被限制的 IP 列表：累计违约 ≥ 阈值且未豁免 */
    @GetMapping("/reservations/limits")
    @RequirePerm("UC-12")
    public Result<List<Map<String, Object>>> limits() {
        expireOutdated();
        List<Map<String, Object>> rows = reservationMapper.selectMaps(new QueryWrapper<Reservation>()
                .select("ip", "COUNT(*) AS violations", "MAX(expect_slot) AS last_slot")
                .eq("status", "已失效").eq("exempt", 0)
                .isNotNull("ip").ne("ip", "")
                .groupBy("ip").having("COUNT(*) >= {0}", MAX_VIOLATIONS_PER_IP));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ip", r.get("ip"));
            m.put("violations", r.get("violations"));
            Object last = r.get("last_slot");
            String lastStr = null;
            if (last instanceof java.sql.Timestamp ts) {
                lastStr = ts.toLocalDateTime().format(SLOT_FMT);
            } else if (last instanceof LocalDateTime ldt) {
                lastStr = ldt.format(SLOT_FMT);
            } else if (last != null) {
                lastStr = last.toString();
            }
            m.put("lastSlot", lastStr);
            m.put("blocked", true);
            out.add(m);
        }
        return Result.ok(out);
    }

    /** 解除某 IP 的预约限制：历史违约置为已豁免，保留留痕 */
    @PostMapping("/reservations/limits/{ip}/release")
    @RequirePerm("UC-12")
    public Result<Map<String, Object>> release(@PathVariable String ip) {
        CurrentUser cu = CurrentUser.require();
        UpdateWrapper<Reservation> uw = new UpdateWrapper<>();
        uw.eq("ip", ip).eq("status", "已失效").eq("exempt", 0)
          .set("exempt", 1)
          .set("exempt_by", cu.getUserId())
          .set("exempt_at", LocalDateTime.now());
        int n = reservationMapper.update(null, uw);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ip", ip);
        data.put("released", n);
        return Result.ok(data);
    }

    // ---------- 预约占用判定 ----------

    /** 预约是否仍在占用期：到店时间后保留 60 分钟，过后自动失效 */
    private boolean isActive(LocalDateTime slot, LocalDateTime now) {
        return slot != null && slot.plusMinutes(RESERVE_HOLD_MINUTES).isAfter(now);
    }

    /** 把过期未到店的预约落库为「已失效」，释放货品与时段（唯一索引随之失效） */
    private void expireOutdated() {
        UpdateWrapper<Reservation> uw = new UpdateWrapper<>();
        uw.eq("status", "待确认")
          .lt("expect_slot", LocalDateTime.now().minusMinutes(RESERVE_HOLD_MINUTES))
          .set("status", "已失效");
        reservationMapper.update(null, uw);
    }

    /** 某手机号对某货品的有效预约（用于改期） */
    private Reservation activeReservation(Long goodsId, String phone, LocalDateTime now) {
        List<Reservation> rs = reservationMapper.selectList(new QueryWrapper<Reservation>()
                .eq("goods_id", goodsId).eq("phone", phone).eq("status", "待确认"));
        for (Reservation r : rs) {
            if (isActive(r.getExpectSlot(), now)) return r;
        }
        return null;
    }

    /** 被有效预约占用的货品 id 集合，批量算，避免列表逐条查库 */
    private java.util.Set<Long> occupiedGoodsIds(List<Goods> goods, LocalDateTime now) {
        java.util.Set<Long> out = new java.util.HashSet<>();
        if (goods.isEmpty()) return out;
        List<Long> ids = new ArrayList<>();
        for (Goods g : goods) ids.add(g.getId());
        List<Reservation> rs = reservationMapper.selectList(new QueryWrapper<Reservation>()
                .eq("status", "待确认").in("goods_id", ids));
        for (Reservation r : rs) {
            if (isActive(r.getExpectSlot(), now)) out.add(r.getGoodsId());
        }
        return out;
    }

    /**
     * 取来源 IP。
     *
     * <p>本机回环说明请求来自隧道（公网明文连接器只绑 127.0.0.1），此时真实客户端在
     * X-Forwarded-For 首段；公网直连（TLS 端口）不读该头，避免伪造。
     */
    private String clientIp(HttpServletRequest req) {
        if (req == null) return "unknown";
        String ip = req.getRemoteAddr();
        if (ip != null && (ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1"))) {
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String first = xff.split(",")[0].trim();
                if (!first.isEmpty()) return first;
            }
        }
        return (ip == null || ip.isBlank()) ? "unknown" : ip;
    }

    /** 时段占用校验：到店预约与上门预约共用同一张时段表 */
    private void ensureSlotFree(LocalDateTime slot, Long excludeResvId, LocalDateTime now) {
        List<Reservation> rs = reservationMapper.selectList(new QueryWrapper<Reservation>()
                .eq("status", "待确认").eq("expect_slot", slot));
        for (Reservation r : rs) {
            if (excludeResvId != null && excludeResvId.equals(r.getId())) continue;
            if (isActive(r.getExpectSlot(), now)) {
                throw BizException.conflict("该时段已约满，请选择其他时间");
            }
        }
        String slotText = slot.format(SLOT_FMT);
        // 上门预约已改成 日期 + 起止时间三列，不能再按文本相等比对；
        // 到店预约占 [slot, slot+1h)，与上门区间有交集就算冲突
        LocalDate visitDay = slot.toLocalDate();
        LocalTime from = slot.toLocalTime();
        LocalTime to = from.plusHours(1);
        List<VisitAppt> vs = visitApptMapper.selectList(new QueryWrapper<VisitAppt>()
                .eq("status", "待上门").eq("expect_date", visitDay));
        for (VisitAppt v : vs) {
            if (v.getStartTime() != null && v.getEndTime() != null
                    && from.isBefore(v.getEndTime()) && v.getStartTime().isBefore(to)) {
                throw BizException.conflict("该时段已安排上门交接（" + v.getStartTime() + "--"
                        + v.getEndTime() + "），请选择其他时间");
            }
        }
    }

    @GetMapping("/public/reservations")
    public Result<List<Map<String, Object>>> queryReservation(@RequestParam String phoneTail) {
        if (phoneTail == null || !phoneTail.matches("\\d{4}")) {
            throw BizException.badRequest("请输入手机号后四位");
        }
        List<Reservation> rs = reservationMapper.selectList(new QueryWrapper<Reservation>()
                .likeLeft("phone", phoneTail).eq("status", "待确认").orderByDesc("created_at"));
        List<Map<String, Object>> out = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Reservation r : rs) {
            // 已过保留期的预约不再展示（FR-019）
            if (!isActive(r.getExpectSlot(), now)) continue;
            Goods g = goodsMapper.selectById(r.getGoodsId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("resvNo", r.getResvNo());
            m.put("expectSlot", r.getExpectSlot() == null ? null : r.getExpectSlot().format(SLOT_FMT));
            m.put("goodsName", g == null ? null : g.getName());
            out.add(m);
            if (out.size() >= 20) break;
        }
        return Result.ok(out);
    }

    // ==================== 内部：预定（由店员在柜台端操作） ====================
    // 客户侧不再开放预定：货品被锁 72 小时的代价由门店判断，散客自行预定容易把货占死。

    public static class BookingReq {
        public String goodsNo;
        public String name;
        public String phone;
    }

    @PostMapping("/bookings")
    @RequirePerm("UC-28")
    @Transactional
    public Result<Map<String, Object>> book(@RequestBody BookingReq req) {
        if (req.phone == null || !req.phone.matches("\\d{11}")) {
            throw BizException.badRequest("手机号格式不正确");
        }
        if (req.name == null || req.name.isBlank()) throw BizException.badRequest("请填客户姓名");
        if (countQuota(req.phone) >= 3) {
            throw BizException.unprocessable("该手机号已有 3 条有效预定，先处理旧的再建");
        }
        Goods g = goodsMapper.selectOne(new QueryWrapper<Goods>().eq("goods_no", req.goodsNo));
        if (g == null || !"在柜".equals(g.getStatus())) throw BizException.notFound("货品不存在或不在柜");
        Long active = bookingMapper.selectCount(new QueryWrapper<Booking>()
                .eq("goods_id", g.getId()).in("status", "待到店", "已联系"));
        if (active != null && active > 0) throw BizException.conflict("该货品已被预定");

        Customer c = customerMapper.selectOne(new QueryWrapper<Customer>().eq("phone", req.phone));
        Booking b = new Booking();
        b.setBookingNo(nextNo("YD"));
        b.setGoodsId(g.getId());
        b.setName(req.name);
        b.setPhone(req.phone);
        // 柜台代订，没有来源 IP；ip 列留空
        b.setIp(null);
        b.setCustomerId(c == null ? null : c.getId());
        b.setSubmitTime(LocalDateTime.now());
        b.setStatus("待到店");
        try {
            bookingMapper.insert(b);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // uk_booking_goods_active 函数唯一索引兜底，并发时库层只放行一条
            throw BizException.conflict("该货品已被预定");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", b.getId());
        data.put("bookingNo", b.getBookingNo());
        data.put("expireAt", b.getSubmitTime().plusHours(BOOKING_HOLD_HOURS));
        return Result.ok(data);
    }

    /** 有效预定清单（待到店/已联系），带剩余保留时间，供柜台端列表与倒计时 */
    @GetMapping("/bookings")
    @RequirePerm("UC-28")
    public Result<List<Map<String, Object>>> activeBookings() {
        List<Booking> bs = bookingMapper.selectList(new QueryWrapper<Booking>()
                .in("status", "待到店", "已联系")
                .orderByAsc("submit_time"));
        List<Map<String, Object>> out = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Booking b : bs) {
            Goods g = goodsMapper.selectById(b.getGoodsId());
            LocalDateTime expireAt = b.getSubmitTime().plusHours(BOOKING_HOLD_HOURS);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("bookingNo", b.getBookingNo());
            m.put("goodsNo", g == null ? null : g.getGoodsNo());
            m.put("goodsName", g == null ? null : g.getName());
            m.put("name", b.getName());
            m.put("phone", b.getPhone());
            m.put("status", b.getStatus());
            m.put("submitTime", b.getSubmitTime());
            m.put("expireAt", expireAt);
            m.put("hoursLeft", Math.max(0, java.time.Duration.between(now, expireAt).toHours()));
            out.add(m);
        }
        return Result.ok(out);
    }

    // ==================== 内部：到店跟进 ====================

    @GetMapping("/bookings/today")
    @RequirePerm("UC-28")
    public Result<List<Map<String, Object>>> today() {
        List<Booking> bs = bookingMapper.selectList(new QueryWrapper<Booking>()
                .in("status", "待到店", "已联系")
                .ge("submit_time", LocalDate.now().atStartOfDay())
                .lt("submit_time", LocalDate.now().plusDays(1).atStartOfDay())
                .orderByAsc("submit_time"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Booking b : bs) {
            Goods g = goodsMapper.selectById(b.getGoodsId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("bookingNo", b.getBookingNo());
            m.put("name", b.getName());
            m.put("phone", b.getPhone());
            m.put("goodsName", g == null ? null : g.getName());
            m.put("status", b.getStatus());
            m.put("submitTime", b.getSubmitTime());
            m.put("contactTime", b.getContactTime());
            out.add(m);
        }
        return Result.ok(out);
    }

    @PostMapping("/bookings/{id}/contact")
    @RequirePerm("UC-28")
    public Result<Void> contact(@PathVariable Long id) {
        Booking b = bookingMapper.selectById(id);
        if (b == null) throw BizException.notFound("预定记录不存在");
        if (!"待到店".equals(b.getStatus())) {
            throw BizException.conflict("该预定状态为「" + b.getStatus() + "」，不能标记已联系");
        }
        b.setStatus("已联系");
        b.setContactTime(LocalDateTime.now());
        bookingMapper.updateById(b);
        return Result.ok();
    }

    public static class ResultReq {
        public Boolean arrived;
    }

    @PostMapping("/bookings/{id}/result")
    @RequirePerm("UC-28")
    public Result<Map<String, Object>> result(@PathVariable Long id, @RequestBody ResultReq req) {
        Booking b = bookingMapper.selectById(id);
        if (b == null) throw BizException.notFound("预定记录不存在");
        if (!List.of("待到店", "已联系").contains(b.getStatus())) {
            throw BizException.conflict("该预定已结束");
        }
        b.setStatus(Boolean.TRUE.equals(req.arrived) ? "已达成" : "已爽约");
        bookingMapper.updateById(b);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bookingNo", b.getBookingNo());
        data.put("status", b.getStatus());
        data.put("remaining", Math.max(0, 3 - countQuota(b.getPhone())));
        return Result.ok(data);
    }

    // ==================== 工具 ====================

    private long countQuota(String phone) {
        Long n = bookingMapper.selectCount(new QueryWrapper<Booking>()
                .eq("phone", phone).in("status", QUOTA_STATUS));
        return n == null ? 0 : n;
    }

    private void saveTags(Long customerId, List<String> tags) {
        if (tags == null) return;
        for (String t : tags) {
            if (!Arrays.asList(TAGS).contains(t)) {
                throw BizException.badRequest("标签不合法：" + t);
            }
            CustomerTag ct = new CustomerTag();
            ct.setCustomerId(customerId);
            ct.setTag(t);
            tagMapper.insert(ct);
        }
    }

    private Map<String, Object> toVO(Customer c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("customerNo", c.getCustomerNo());
        m.put("name", c.getName());
        m.put("phone", c.getPhone());
        m.put("wechat", c.getWechat());
        m.put("sourceChannel", c.getSourceChannel());
        m.put("prefCategory", c.getPrefCategory());
        m.put("prefSize", c.getPrefSize());
        m.put("prefBudget", c.getPrefBudget());
        m.put("remark", c.getRemark());
        List<String> tags = tagMapper.selectList(
                new QueryWrapper<CustomerTag>().eq("customer_id", c.getId()))
                .stream().map(CustomerTag::getTag).toList();
        m.put("tags", tags);
        return m;
    }

    private String nextNo(String prefix) {
        return IdGen.next(prefix);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 11) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
