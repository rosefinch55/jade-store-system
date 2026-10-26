package com.jade.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 业务编号生成。
 * 不用「查当日最大序号 +1」——并发下两个请求会拿到同一个最大值，
 * 插入时撞唯一索引直接 500。改用「日期时分秒 + 两位随机」，重复概率可忽略，
 * 代价是编号不连续，业务上不要求连续。
 */
public final class IdGen {

    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private IdGen() {
    }

    public static String next(String prefix) {
        return prefix + LocalDateTime.now().format(F)
                + String.format("%02d", ThreadLocalRandom.current().nextInt(100));
    }
}
