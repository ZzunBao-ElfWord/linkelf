package com.linksprite.tenant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RateLimiter 单元测试：qps 超限、跨秒恢复、每日配额超限。
 */
class RateLimiterTest {

    @Test
    void qpsExceededAndRecoversAfterOneSecond() throws InterruptedException {
        RateLimiter limiter = new RateLimiter();
        limiter.setQpsLimit("openelf", 3);

        assertTrue(limiter.allowQps("openelf"));
        assertTrue(limiter.allowQps("openelf"));
        assertTrue(limiter.allowQps("openelf"));
        assertFalse(limiter.allowQps("openelf"));

        // 跨越 1 秒窗口后恢复
        Thread.sleep(1100L);
        assertTrue(limiter.allowQps("openelf"));
    }

    @Test
    void qpsIsIndependentPerProduct() {
        RateLimiter limiter = new RateLimiter();
        limiter.setQpsLimit("openelf", 1);

        assertTrue(limiter.allowQps("openelf"));
        assertFalse(limiter.allowQps("openelf"));
        // 其它产品不受影响（使用默认上限 50）
        assertTrue(limiter.allowQps("koko"));
    }

    @Test
    void dailyQuotaExceeded() {
        RateLimiter limiter = new RateLimiter();

        assertTrue(limiter.allowDaily("openelf", 2));
        assertTrue(limiter.allowDaily("openelf", 2));
        assertFalse(limiter.allowDaily("openelf", 2));

        // 另一产品独立计数
        assertTrue(limiter.allowDaily("koko", 2));
    }
}
