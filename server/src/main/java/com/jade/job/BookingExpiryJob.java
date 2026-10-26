package com.jade.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jade.entity.Booking;
import com.jade.mapper.BookingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预定到期回收。
 *
 * 柜台建单后货品被唯一索引锁住，散客看不见也买不了；没有人来点「爽约」时这条锁会一直挂着。
 * 这里按提交时间 +72 小时把仍在「待到店／已联系」的单子置为「已爽约」，货品随之释放。
 *
 * 为什么不加 expire_at 列：保留时长是全局口径（72 小时），多一列就要多维护一处写入，
 * 直接由 submit_time 推算更省；将来按单改工期再补列。
 *
 * 触发点两个：应用启动时跑一次（重启即回收，避免定时器错过），之后每小时第 7 分跑一次。
 */
@Component
public class BookingExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryJob.class);
    private static final int HOLD_HOURS = 72;

    private final BookingMapper bookingMapper;

    public BookingExpiryJob(BookingMapper bookingMapper) {
        this.bookingMapper = bookingMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onBoot() {
        int n = expire();
        if (n > 0) log.info("预定到期回收（启动时）：{} 条", n);
    }

    @Scheduled(cron = "0 7 * * * *")
    public void hourly() {
        int n = expire();
        if (n > 0) log.info("预定到期回收：{} 条", n);
    }

    /** 返回本次回收条数；条件是「仍在占用状态」且「提交时间早于 72 小时前」 */
    public int expire() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(HOLD_HOURS);
        List<Booking> stale = bookingMapper.selectList(new QueryWrapper<Booking>()
                .in("status", "待到店", "已联系")
                .lt("submit_time", deadline));
        for (Booking b : stale) {
            b.setStatus("已爽约");
            bookingMapper.updateById(b);
        }
        return stale.size();
    }
}
