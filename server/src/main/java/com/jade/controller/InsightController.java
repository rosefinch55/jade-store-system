package com.jade.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jade.common.BizException;
import com.jade.common.RequirePerm;
import com.jade.common.Result;
import com.jade.entity.*;
import com.jade.mapper.*;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 经营看板数据：报表指标与日历事件。
 * 只做聚合查询，不写业务规则，权限沿用登录态（拦截器要求已登录）。
 */
@RestController
@RequestMapping("/api")
public class InsightController {

    private static final DateTimeFormatter SLOT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final PaymentMapper paymentMapper;
    private final InstallmentMapper installmentMapper;
    private final ReservationMapper reservationMapper;
    private final VisitApptMapper visitApptMapper;
    private final ProcessTaskMapper processTaskMapper;
    private final GoodsMapper goodsMapper;

    public InsightController(PaymentMapper paymentMapper, InstallmentMapper installmentMapper,
                             ReservationMapper reservationMapper, VisitApptMapper visitApptMapper,
                             ProcessTaskMapper processTaskMapper, GoodsMapper goodsMapper) {
        this.paymentMapper = paymentMapper;
        this.installmentMapper = installmentMapper;
        this.reservationMapper = reservationMapper;
        this.visitApptMapper = visitApptMapper;
        this.processTaskMapper = processTaskMapper;
        this.goodsMapper = goodsMapper;
    }

    // ---------------- 报表指标 ----------------

    @RequirePerm(value = {}, roles = {"BOSS", "SALE"})
    @GetMapping("/stats/overview")
    public Result<Map<String, Object>> overview(@RequestParam String from, @RequestParam String to) {
        LocalDateTime f;
        LocalDateTime t;
        long days;
        try {
            f = LocalDate.parse(from).atStartOfDay();
            t = LocalDate.parse(to).plusDays(1).atStartOfDay();
            days = Math.max(1, LocalDate.parse(to).toEpochDay() - LocalDate.parse(from).toEpochDay() + 1);
        } catch (DateTimeParseException e) {
            throw BizException.badRequest("日期格式应为 yyyy-MM-dd");
        }

        Map<String, Object> data = new LinkedHashMap<>();

        // 收款总额与笔数（只统计「收取」方向，采购等支出不计入营业额）
        List<Map<String, Object>> sum = paymentMapper.selectMaps(new QueryWrapper<Payment>()
                .select("IFNULL(SUM(amount),0) AS total", "COUNT(*) AS cnt")
                .eq("direction", "收取").ge("pay_time", f).lt("pay_time", t));
        BigDecimal total = toDecimal(sum.isEmpty() ? null : sum.get(0).get("total"));
        long cnt = sum.isEmpty() || sum.get(0).get("cnt") == null ? 0
                : Long.parseLong(sum.get(0).get("cnt").toString());
        data.put("collected", total);
        data.put("paymentCount", cnt);
        data.put("dailyAvg", total.divide(BigDecimal.valueOf(days), 2, java.math.RoundingMode.HALF_UP));
        data.put("avgTicket", cnt == 0 ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(cnt), 2, java.math.RoundingMode.HALF_UP));

        // 分期逾期：「逾期」是展示层派生态（未还且过下一期到期日），库里不存在该值，
        // 按 next_due_date 派生口径查，否则永远查不到、看板恒为 0
        List<Installment> overdue = installmentMapper.selectList(new QueryWrapper<Installment>()
                .eq("status", "未还").lt("next_due_date", LocalDate.now()));
        BigDecimal overdueAmt = BigDecimal.ZERO;
        for (Installment i : overdue) {
            if (i.getPeriodAmount() != null) overdueAmt = overdueAmt.add(i.getPeriodAmount());
        }
        data.put("overdueCount", overdue.size());
        data.put("overdueAmount", overdueAmt);

        // 业务类型构成
        List<Map<String, Object>> byType = paymentMapper.selectMaps(new QueryWrapper<Payment>()
                .select("biz_type AS type", "IFNULL(SUM(amount),0) AS amt")
                .eq("direction", "收取").ge("pay_time", f).lt("pay_time", t)
                .groupBy("biz_type").orderByDesc("amt"));
        List<Map<String, Object>> types = new ArrayList<>();
        for (Map<String, Object> r : byType) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", r.get("type"));
            m.put("amount", toDecimal(r.get("amt")));
            types.add(m);
        }
        data.put("byType", types);

        // 每日收款趋势
        List<Map<String, Object>> trendRows = paymentMapper.selectMaps(new QueryWrapper<Payment>()
                .select("DATE(pay_time) AS d", "IFNULL(SUM(amount),0) AS amt")
                .eq("direction", "收取").ge("pay_time", f).lt("pay_time", t)
                .groupBy("DATE(pay_time)").orderByAsc("d"));
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map<String, Object> r : trendRows) {
            Map<String, Object> m = new LinkedHashMap<>();
            Object d = r.get("d");
            m.put("date", d == null ? null : d.toString());
            m.put("amount", toDecimal(r.get("amt")));
            trend.add(m);
        }
        data.put("trend", trend);
        return Result.ok(data);
    }

    // ---------------- 日历事件 ----------------

    /**
     * 三类事件叠加：到店预约、上门预约、加工取件（按期限）。
     * 参数为日期区间，闭区间。
     */
    @RequirePerm(value = {}, roles = {"BOSS", "SALE"})
    @GetMapping("/calendar")
    public Result<List<Map<String, Object>>> calendar(@RequestParam String from, @RequestParam String to) {
        LocalDateTime f;
        LocalDateTime t;
        LocalDate d1;
        LocalDate d2;
        try {
            d1 = LocalDate.parse(from);
            d2 = LocalDate.parse(to);
        } catch (DateTimeParseException e) {
            throw BizException.badRequest("日期格式应为 yyyy-MM-dd");
        }
        f = d1.atStartOfDay();
        t = d2.plusDays(1).atStartOfDay();

        List<Map<String, Object>> out = new ArrayList<>();

        // 到店预约（与上门预约同口径：只列仍有效的，已取消/已失效不上日历）
        for (Reservation r : reservationMapper.selectList(new QueryWrapper<Reservation>()
                .eq("status", "待确认").ge("expect_slot", f).lt("expect_slot", t))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "reserve");
            m.put("at", r.getExpectSlot() == null ? null : r.getExpectSlot().format(SLOT_FMT));
            m.put("title", r.getName() + " 到店看货");
            m.put("sub", r.getResvNo() + " · " + r.getStatus());
            m.put("ref", r.getResvNo());
            out.add(m);
        }

        // 上门预约（2026-10-26 起日期与时段是结构化字段，直接按日期取，
        // 不像以前那样存自由文本再猜）
        for (VisitAppt v : visitApptMapper.selectList(new QueryWrapper<VisitAppt>()
                .eq("status", "待上门").ge("expect_date", d1).le("expect_date", d2))) {
            if (v.getExpectDate() == null || v.getStartTime() == null) continue;
            LocalDateTime at = LocalDateTime.of(v.getExpectDate(), v.getStartTime());
            if (at.isBefore(f) || !at.isBefore(t)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "visit");
            m.put("at", at.format(SLOT_FMT));
            m.put("title", v.getApptParty() + " 上门");
            m.put("sub", (v.getMatter() == null ? "" : v.getMatter())
                    + " · " + v.getStartTime() + "–" + v.getEndTime() + " · " + v.getStatus());
            m.put("ref", v.getApptNo());
            out.add(m);
        }

        // 加工取件（按期限，未完成的才排）
        for (ProcessTask p : processTaskMapper.selectList(new QueryWrapper<ProcessTask>()
                .ge("deadline", d1).le("deadline", d2))) {
            if ("已交付".equals(p.getStatus())) continue; // 任务终态是「已交付」，此前误用上门预约的状态词
            Goods g = p.getGoodsId() == null ? null : goodsMapper.selectById(p.getGoodsId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "task");
            m.put("at", p.getDeadline() + " 09:00");
            m.put("title", "加工件到期" + (g == null ? "" : "：" + g.getName()));
            m.put("sub", p.getTaskNo() + " · " + p.getStatus() + " · 工费 " + p.getLaborFee());
            m.put("ref", p.getTaskNo());
            out.add(m);
        }

        out.sort(Comparator.comparing(m -> String.valueOf(m.get("at"))));
        return Result.ok(out);
    }

    private BigDecimal toDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        try {
            return new BigDecimal(o.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
