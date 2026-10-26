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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 货品档案与基础数据。对应 specs/001-goods 与 000-base 的部分接口。
 */
@RestController
@RequestMapping("/api")
public class GoodsController {

    private final GoodsMapper goodsMapper;
    private final CategoryMapper categoryMapper;
    private final StockInMapper stockInMapper;
    private final StockLogMapper stockLogMapper;
    private final SourcePartyMapper partyMapper;
    private final CertOrgMapper certOrgMapper;

    public GoodsController(GoodsMapper goodsMapper, CategoryMapper categoryMapper,
                           StockInMapper stockInMapper, StockLogMapper stockLogMapper,
                           SourcePartyMapper partyMapper, CertOrgMapper certOrgMapper) {
        this.goodsMapper = goodsMapper;
        this.categoryMapper = categoryMapper;
        this.stockInMapper = stockInMapper;
        this.stockLogMapper = stockLogMapper;
        this.partyMapper = partyMapper;
        this.certOrgMapper = certOrgMapper;
    }

    // ---------------- 货品 ----------------

    @GetMapping("/goods")
    public Result<Map<String, Object>> list(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) Long categoryId,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        if (size > 100) size = 100;
        QueryWrapper<Goods> q = new QueryWrapper<>();
        if (status != null && !status.isBlank()) q.eq("status", status);
        if (categoryId != null) q.eq("category_id", categoryId);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like("goods_no", keyword).or().like("name", keyword));
        }
        q.orderByDesc("in_time");
        Page<Goods> p = goodsMapper.selectPage(new Page<>(page, size), q);
        Map<Long, String> catNames = categoryNames();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Goods g : p.getRecords()) {
            items.add(toVO(g, catNames));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", p.getTotal());
        data.put("page", page);
        data.put("size", size);
        data.put("items", items);
        return Result.ok(data);
    }

    @GetMapping("/goods/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Goods g = goodsMapper.selectById(id);
        if (g == null) throw BizException.notFound("货品不存在");
        return Result.ok(toVO(g, categoryNames()));
    }

    public static class GoodsReq {
        public String name;
        public Long categoryId;
        public String material;
        public String origin;
        public String shape;
        public String form;
        public BigDecimal weightG;
        public String size;
        public String certNo;
        public String image;
        public String srcType;
        public String srcParty;
        public String srcDoc;
        public BigDecimal cost;
        public BigDecimal minPrice;
        public BigDecimal transferPrice;
    }

    /** 单件建档 */
    @PostMapping("/goods")
    @RequirePerm("UC-02")
    @Transactional
    public Result<Map<String, Object>> create(@RequestBody GoodsReq req) {
        validate(req);
        StockIn si = new StockIn();
        si.setInTime(LocalDateTime.now());
        si.setReceiverId(CurrentUser.require().getUserId());
        si.setRemark("单件建档");
        stockInMapper.insert(si);

        Goods g = build(req, si.getId());
        goodsMapper.insert(g);
        writeLog(g.getId(), null, "在柜", "入库批次", String.valueOf(si.getId()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", g.getId());
        data.put("goodsNo", g.getGoodsNo());
        data.put("status", g.getStatus());
        data.put("inTime", g.getInTime());
        data.put("stockInId", si.getId());
        return Result.ok(data);
    }

    /** 批量入库：一次多件共用批次 */
    @PostMapping("/stock-ins")
    @RequirePerm("UC-02")
    @Transactional
    public Result<Map<String, Object>> stockIn(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) throw BizException.badRequest("货品清单不能为空");

        StockIn si = new StockIn();
        si.setInTime(LocalDateTime.now());
        si.setReceiverId(CurrentUser.require().getUserId());
        si.setRemark((String) body.getOrDefault("remark", null));
        stockInMapper.insert(si);

        List<Map<String, Object>> created = new ArrayList<>();
        for (Map<String, Object> it : items) {
            GoodsReq r = new GoodsReq();
            r.name = str(it.get("name"));
            r.categoryId = num(it.get("categoryId"));
            r.material = str(it.get("material"));
            r.origin = str(it.get("origin"));
            r.shape = str(it.get("shape"));
            r.form = str(it.get("form"));
            r.weightG = dec(it.get("weightG"));
            r.size = str(it.get("size"));
            r.certNo = str(it.get("certNo"));
            r.image = str(it.get("image"));
            r.srcType = str(it.get("srcType"));
            r.srcParty = str(it.get("srcParty"));
            r.srcDoc = str(it.get("srcDoc"));
            r.cost = dec(it.get("cost"));
            r.minPrice = dec(it.get("minPrice"));
            r.transferPrice = dec(it.get("transferPrice"));
            validate(r);
            Goods g = build(r, si.getId());
            goodsMapper.insert(g);
            writeLog(g.getId(), null, "在柜", "入库批次", String.valueOf(si.getId()));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", g.getId());
            m.put("goodsNo", g.getGoodsNo());
            created.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stockInId", si.getId());
        data.put("inTime", si.getInTime());
        data.put("goodsCount", created.size());
        data.put("goods", created);
        return Result.ok(data);
    }

    @PutMapping("/goods/{id}")
    @RequirePerm("UC-02")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Goods g = goodsMapper.selectById(id);
        if (g == null) throw BizException.notFound("货品不存在");
        if ("已售".equals(g.getStatus())) throw BizException.conflict("已售货品不可改档");
        // 不可改字段
        for (String bad : List.of("goodsNo", "srcType", "cost", "inTime", "stockInId", "status")) {
            if (body.containsKey(bad)) throw BizException.badRequest("字段 " + bad + " 不允许修改");
        }
        if (body.get("name") != null) g.setName(str(body.get("name")));
        if (body.get("categoryId") != null) g.setCategoryId(num(body.get("categoryId")));
        if (body.get("material") != null) g.setMaterial(str(body.get("material")));
        if (body.get("origin") != null) g.setOrigin(str(body.get("origin")));
        if (body.get("shape") != null) g.setShape(str(body.get("shape")));
        if (body.get("form") != null) g.setForm(str(body.get("form")));
        if (body.get("weightG") != null) g.setWeightG(dec(body.get("weightG")));
        if (body.get("size") != null) g.setSize(str(body.get("size")));
        if (body.get("certNo") != null) g.setCertNo(str(body.get("certNo")));
        if (body.get("image") != null) g.setImage(str(body.get("image")));
        if (body.get("minPrice") != null) {
            BigDecimal mp = dec(body.get("minPrice"));
            if (mp == null || mp.compareTo(BigDecimal.ZERO) <= 0) {
                throw BizException.badRequest("最低售价必须大于 0");
            }
            g.setMinPrice(mp);
        }
        if (body.get("transferPrice") != null) g.setTransferPrice(dec(body.get("transferPrice")));
        if (body.get("srcParty") != null) g.setSrcParty(str(body.get("srcParty")));
        if (body.get("srcDoc") != null) g.setSrcDoc(str(body.get("srcDoc")));
        goodsMapper.updateById(g);
        return Result.ok();
    }

    @GetMapping("/goods/{id}/logs")
    public Result<List<Map<String, Object>>> logs(@PathVariable Long id) {
        List<StockLog> ls = stockLogMapper.selectList(
                new QueryWrapper<StockLog>().eq("goods_id", id).orderByAsc("log_time"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (StockLog l : ls) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fromStatus", l.getFromStatus());
            m.put("toStatus", l.getToStatus());
            m.put("refType", l.getRefType());
            m.put("refNo", l.getRefNo());
            m.put("operatorId", l.getOperatorId());
            m.put("logTime", l.getLogTime());
            out.add(m);
        }
        return Result.ok(out);
    }

    // ---------------- 类别 ----------------

    @GetMapping("/categories")
    public Result<List<Category>> categories() {
        return Result.ok(categoryMapper.selectList(new QueryWrapper<Category>().orderByAsc("id")));
    }

    @PostMapping("/categories")
    @RequirePerm("UC-07")
    public Result<Category> addCategory(@RequestBody Category c) {
        if (c.getName() == null || c.getName().isBlank()) throw BizException.badRequest("名称不能为空");
        Long n = categoryMapper.selectCount(new QueryWrapper<Category>().eq("name", c.getName()));
        if (n != null && n > 0) throw BizException.conflict("类别名称已存在");
        c.setId(null);
        categoryMapper.insert(c);
        return Result.ok(c);
    }

    @PutMapping("/categories/{id}")
    @RequirePerm("UC-07")
    public Result<Void> updateCategory(@PathVariable Long id, @RequestBody Category c) {
        Category old = categoryMapper.selectById(id);
        if (old == null) throw BizException.notFound("类别不存在");
        old.setName(c.getName());
        old.setRemark(c.getRemark());
        categoryMapper.updateById(old);
        return Result.ok();
    }

    @DeleteMapping("/categories/{id}")
    @RequirePerm("UC-07")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        Long ref = goodsMapper.selectCount(new QueryWrapper<Goods>().eq("category_id", id));
        if (ref != null && ref > 0) {
            Map<String, Object> d = new HashMap<>();
            d.put("refCount", ref);
            throw BizException.conflict("该类别已被 " + ref + " 件货品引用，不能删除", d);
        }
        categoryMapper.deleteById(id);
        return Result.ok();
    }

    // ---------------- 来源方 ----------------

    @GetMapping("/source-parties")
    public Result<List<SourceParty>> parties(@RequestParam(required = false) String partyType) {
        QueryWrapper<SourceParty> q = new QueryWrapper<>();
        if (partyType != null && !partyType.isBlank()) q.eq("party_type", partyType);
        q.orderByAsc("party_type").orderByAsc("id");
        return Result.ok(partyMapper.selectList(q));
    }

    @PostMapping("/source-parties")
    @RequirePerm("UC-07")
    public Result<SourceParty> addParty(@RequestBody SourceParty p) {
        if (p.getName() == null || p.getName().isBlank()) throw BizException.badRequest("名称不能为空");
        Long n = partyMapper.selectCount(new QueryWrapper<SourceParty>()
                .eq("party_type", p.getPartyType()).eq("name", p.getName()));
        if (n != null && n > 0) throw BizException.conflict("同类别下名称已存在");
        p.setId(null);
        partyMapper.insert(p);
        return Result.ok(p);
    }

    @PutMapping("/source-parties/{id}")
    @RequirePerm("UC-07")
    public Result<Void> updateParty(@PathVariable Long id, @RequestBody SourceParty p) {
        SourceParty old = partyMapper.selectById(id);
        if (old == null) throw BizException.notFound("来源方不存在");
        old.setName(p.getName());
        old.setContactPerson(p.getContactPerson());
        old.setPhone(p.getPhone());
        old.setRemark(p.getRemark());
        partyMapper.updateById(old);
        return Result.ok();
    }

    @DeleteMapping("/source-parties/{id}")
    @RequirePerm("UC-07")
    public Result<Void> deleteParty(@PathVariable Long id) {
        SourceParty p = partyMapper.selectById(id);
        if (p == null) throw BizException.notFound("来源方不存在");
        Long ref = goodsMapper.selectCount(new QueryWrapper<Goods>().eq("src_party", p.getName()));
        if (ref != null && ref > 0) {
            Map<String, Object> d = new HashMap<>();
            d.put("refCount", ref);
            throw BizException.conflict("该来源方已被 " + ref + " 件货品引用，只能停用", d);
        }
        partyMapper.deleteById(id);
        return Result.ok();
    }

    // ---------------- 鉴定机构 ----------------

    @GetMapping("/cert-orgs")
    public Result<List<CertOrg>> certOrgs() {
        return Result.ok(certOrgMapper.selectList(new QueryWrapper<CertOrg>().orderByAsc("id")));
    }

    @PostMapping("/cert-orgs")
    @RequirePerm("UC-07")
    public Result<CertOrg> addCertOrg(@RequestBody CertOrg c) {
        c.setId(null);
        certOrgMapper.insert(c);
        return Result.ok(c);
    }

    @PutMapping("/cert-orgs/{id}")
    @RequirePerm("UC-07")
    public Result<Void> updateCertOrg(@PathVariable Long id, @RequestBody CertOrg c) {
        c.setId(id);
        certOrgMapper.updateById(c);
        return Result.ok();
    }

    // ---------------- 内部工具 ----------------

    private Map<Long, String> categoryNames() {
        Map<Long, String> m = new HashMap<>();
        for (Category c : categoryMapper.selectList(null)) m.put(c.getId(), c.getName());
        return m;
    }

    private Map<String, Object> toVO(Goods g, Map<Long, String> cats) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("goodsNo", g.getGoodsNo());
        m.put("name", g.getName());
        m.put("categoryId", g.getCategoryId());
        m.put("categoryName", cats.get(g.getCategoryId()));
        m.put("material", g.getMaterial());
        m.put("origin", g.getOrigin());
        m.put("shape", g.getShape());
        m.put("form", g.getForm());
        m.put("weightG", g.getWeightG());
        m.put("size", g.getSize());
        m.put("certNo", g.getCertNo());
        m.put("image", g.getImage());
        m.put("srcType", g.getSrcType());
        m.put("srcParty", g.getSrcParty());
        m.put("cost", g.getCost());
        m.put("minPrice", g.getMinPrice());
        m.put("transferPrice", g.getTransferPrice());
        m.put("inTime", g.getInTime());
        m.put("status", g.getStatus());
        m.put("isFeatured", g.getIsFeatured());
        return m;
    }

    private void validate(GoodsReq r) {
        if (r.name == null || r.name.isBlank()) throw BizException.badRequest("品名不能为空");
        if (r.categoryId == null) throw BizException.badRequest("类别不能为空");
        if (r.minPrice == null || r.minPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.badRequest("最低售价必须大于 0");
        }
        if (r.weightG == null || r.weightG.compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.badRequest("克重必须大于 0");
        }
        String t = r.srcType == null ? "" : r.srcType;
        if (!List.of("采购", "回收", "调货", "加工完工", "寄售").contains(t)) {
            throw BizException.badRequest("来源类型不合法");
        }
        if ("调货".equals(t) && r.cost != null) {
            throw BizException.unprocessable("调货调入不记成本");
        }
        if (!"调货".equals(t) && r.cost == null) {
            throw BizException.unprocessable("该来源类型必须填成本");
        }
    }

    private Goods build(GoodsReq r, Long stockInId) {
        Goods g = new Goods();
        g.setGoodsNo(nextNo());
        g.setName(r.name);
        g.setCategoryId(r.categoryId);
        g.setMaterial(r.material == null ? "未填" : r.material);
        g.setOrigin(r.origin == null ? "未填" : r.origin);
        g.setShape(r.shape == null ? "其他" : r.shape);
        g.setForm(r.form == null ? "山料" : r.form);
        g.setWeightG(r.weightG);
        g.setSize(r.size);
        g.setCertNo(r.certNo);
        g.setImage(r.image);
        g.setSrcType(r.srcType);
        g.setSrcParty(r.srcParty);
        g.setSrcDoc(r.srcDoc);
        g.setCost(r.cost);
        g.setMinPrice(r.minPrice);
        g.setTransferPrice(r.transferPrice);
        g.setInTime(LocalDateTime.now());
        g.setStockInId(stockInId);
        g.setStatus("在柜");
        g.setIsFeatured(0);
        return g;
    }

    private String nextNo() {
        return IdGen.next("JD");
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

    static String str(Object o) {
        return o == null ? null : o.toString();
    }

    static Long num(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        return Long.valueOf(o.toString());
    }

    static BigDecimal dec(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(o.toString());
    }
}
