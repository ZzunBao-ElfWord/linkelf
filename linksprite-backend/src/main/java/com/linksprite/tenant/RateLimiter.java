package com.linksprite.tenant;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存限流器（按 productId 维度）。
 * <p>
 * - QPS：1 秒滑动窗口（保存最近 1 秒内的请求时间戳）。
 * - 日配额：按 yyyy-MM-dd 重置。
 * <p>
 * 说明：计数全部保存在进程内存中，重启后清零，属于可接受的实现（不做持久化）。
 */
@Component
public class RateLimiter {

    /** 未显式配置时的默认每秒上限，与 Tenant 默认值一致 */
    public static final int DEFAULT_QPS = 50;

    /** productId -> 最近 1 秒内的请求时间戳队列 */
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    /** productId -> qps 上限（由过滤器按租户配置写入） */
    private final Map<String, Integer> qpsLimits = new ConcurrentHashMap<>();

    /** productId -> 当日计数器 */
    private final Map<String, DailyCounter> dailyCounters = new ConcurrentHashMap<>();

    /** 设置某产品的 qps 上限（qps <= 0 视为不限流） */
    public void setQpsLimit(String productId, int qps) {
        if (productId == null) {
            return;
        }
        qpsLimits.put(productId, qps);
    }

    /**
     * 滑动窗口 QPS 校验：允许则计数并返回 true。
     */
    public boolean allowQps(String productId) {
        if (productId == null) {
            return false;
        }
        int limit = qpsLimits.getOrDefault(productId, DEFAULT_QPS);
        if (limit <= 0) {
            return true;
        }
        Deque<Long> window = windows.computeIfAbsent(productId, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        long cutoff = now - 1000L;
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst() <= cutoff) {
                window.pollFirst();
            }
            if (window.size() >= limit) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    /**
     * 每日配额校验：允许则计数并返回 true；跨天自动重置。
     */
    public boolean allowDaily(String productId, int quota) {
        if (productId == null) {
            return false;
        }
        if (quota <= 0) {
            return true;
        }
        String today = LocalDate.now().toString();
        DailyCounter counter = dailyCounters.computeIfAbsent(productId, k -> new DailyCounter(today));
        synchronized (counter) {
            if (!today.equals(counter.date)) {
                counter.date = today;
                counter.count = 0;
            }
            if (counter.count >= quota) {
                return false;
            }
            counter.count++;
            return true;
        }
    }

    /** 进程内日计数器 */
    private static final class DailyCounter {
        private String date;
        private int count;

        private DailyCounter(String date) {
            this.date = date;
        }
    }
}
