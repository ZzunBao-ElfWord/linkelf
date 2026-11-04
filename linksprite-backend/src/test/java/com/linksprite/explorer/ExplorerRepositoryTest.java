package com.linksprite.explorer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ExplorerRepository 纯函数单元测试：分页参数钳制、OFFSET、搜索输入类型、方法选择器。
 * 不启动 Spring 上下文，不连接数据库。
 */
class ExplorerRepositoryTest {

    @Test
    void clampPageKeepsValidAndNormalizesInvalid() {
        assertEquals(1, ExplorerRepository.clampPage(0));
        assertEquals(1, ExplorerRepository.clampPage(-5));
        assertEquals(3, ExplorerRepository.clampPage(3));
    }

    @Test
    void clampSizeDefaultsAndCaps() {
        assertEquals(20, ExplorerRepository.clampSize(0));
        assertEquals(20, ExplorerRepository.clampSize(-1));
        assertEquals(25, ExplorerRepository.clampSize(25));
        assertEquals(100, ExplorerRepository.clampSize(100));
        assertEquals(100, ExplorerRepository.clampSize(500));
    }

    @Test
    void offsetOfUsesClampedValues() {
        assertEquals(0L, ExplorerRepository.offsetOf(1, 20));
        assertEquals(40L, ExplorerRepository.offsetOf(3, 20));
        assertEquals(0L, ExplorerRepository.offsetOf(0, 20));
        assertEquals(100L, ExplorerRepository.offsetOf(2, 500));
    }

    @Test
    void detectSearchType() {
        assertEquals("block", ExplorerRepository.detectSearchType("123"));
        assertEquals("block", ExplorerRepository.detectSearchType(" 100 "));
        assertEquals("tx", ExplorerRepository.detectSearchType(
                "0x" + "a".repeat(64)));
        assertEquals("address", ExplorerRepository.detectSearchType(
                "0x" + "b".repeat(40)));
        assertEquals("unknown", ExplorerRepository.detectSearchType("OPENELF-UGC-1"));
        assertEquals("unknown", ExplorerRepository.detectSearchType("0x123"));
        assertEquals("unknown", ExplorerRepository.detectSearchType(null));
        assertEquals("unknown", ExplorerRepository.detectSearchType("   "));
    }

    @Test
    void validatePageRejectsIllegalValues() {
        assertNull(ExplorerRepository.validatePage(1, 20));
        assertNull(ExplorerRepository.validatePage(3, 100));
        assertEquals("page 参数必须大于等于 1", ExplorerRepository.validatePage(0, 20));
        assertEquals("size 参数必须大于等于 1", ExplorerRepository.validatePage(1, 0));
        assertEquals("size 参数不得超过 100", ExplorerRepository.validatePage(1, 500));
    }

    @Test
    void tokenIdOfSupportsHashPrefixAndPlainNumber() {
        assertEquals(Long.valueOf(12), ExplorerRepository.tokenIdOf("12"));
        assertEquals(Long.valueOf(12), ExplorerRepository.tokenIdOf("#12"));
        assertEquals(Long.valueOf(0), ExplorerRepository.tokenIdOf("#0"));
        assertNull(ExplorerRepository.tokenIdOf("#abc"));
        assertNull(ExplorerRepository.tokenIdOf("0xabc"));
        assertNull(ExplorerRepository.tokenIdOf(""));
        assertNull(ExplorerRepository.tokenIdOf(null));
    }

    @Test
    void methodOfTakesFirst10Chars() {
        assertEquals("0x12345678",
                ExplorerRepository.methodOf("0x12345678abcdef"));
        assertEquals(null, ExplorerRepository.methodOf("0x1234"));
        assertEquals(null, ExplorerRepository.methodOf(""));
        assertEquals(null, ExplorerRepository.methodOf(null));
    }
}
