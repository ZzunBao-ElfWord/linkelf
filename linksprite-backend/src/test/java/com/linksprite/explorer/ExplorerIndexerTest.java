package com.linksprite.explorer;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ExplorerIndexer 事件解析单元测试：product_hint 推导、授权事件 ABI data 解码、topic 解码。
 * 不启动 Spring 上下文，不连接数据库。
 */
class ExplorerIndexerTest {

    @Test
    void parseProductHintTakesPrefixBeforeFirstDash() {
        assertEquals("openelf", ExplorerIndexer.parseProductHint("OPENELF-UGC-20260001"));
        assertEquals("linkelf", ExplorerIndexer.parseProductHint("LINKELF-SMOKE-001"));
        assertEquals("koko", ExplorerIndexer.parseProductHint("KOKO-IP-1"));
        assertEquals("openelf", ExplorerIndexer.parseProductHint("  openelf-x  "));
    }

    @Test
    void parseProductHintUnknownWhenNoDashOrEmpty() {
        assertEquals("unknown", ExplorerIndexer.parseProductHint("OPENELF"));
        assertEquals("unknown", ExplorerIndexer.parseProductHint(null));
        assertEquals("unknown", ExplorerIndexer.parseProductHint(""));
        assertEquals("unknown", ExplorerIndexer.parseProductHint("   "));
        assertEquals("unknown", ExplorerIndexer.parseProductHint("-UGC-1"));
    }

    @Test
    void decodeAuthContractNoFromAbiData() {
        String data = authData("OPENELF-UGC-20260001", BigInteger.valueOf(1000));
        assertEquals("OPENELF-UGC-20260001", ExplorerIndexer.decodeAuthContractNo(data));
        assertNull(ExplorerIndexer.decodeAuthContractNo(null));
        assertNull(ExplorerIndexer.decodeAuthContractNo("0x1234"));
    }

    @Test
    void decodeAuthContractNoSupportsChineseUtf8() {
        String contractNo = "LINKELF-授权-001";
        String data = authData(contractNo, BigInteger.ONE);
        assertEquals(contractNo, ExplorerIndexer.decodeAuthContractNo(data));
    }

    @Test
    void decodeUintAndAddressTopic() {
        assertEquals(BigInteger.valueOf(42),
                ExplorerIndexer.decodeUint("0x" + "0".repeat(62) + "2a"));
        assertNull(ExplorerIndexer.decodeUint(null));

        String topic = "0x" + "0".repeat(24) + "1234567890abcdef1234567890abcdef12345678";
        assertEquals("0x1234567890abcdef1234567890abcdef12345678",
                ExplorerIndexer.decodeAddressTopic(topic));
        assertNull(ExplorerIndexer.decodeAddressTopic("0x1234"));
    }

    @Test
    void numericConversions() {
        assertEquals("0", ExplorerIndexer.toDecimalString("0x0"));
        assertEquals("255", ExplorerIndexer.toDecimalString("0xff"));
        assertEquals("100", ExplorerIndexer.toDecimalString("100"));
        assertEquals("0", ExplorerIndexer.toDecimalString(null));

        assertEquals(255L, ExplorerIndexer.parseHexLong("0xff"));
        assertEquals(0L, ExplorerIndexer.parseHexLong(null));
        assertEquals(100L, ExplorerIndexer.parseHexLong("100"));
    }

    /** 构造 AuthorizationRegistered 的 data 部分：
     *  offset(string) | maxMint | startTime | endTime | string */
    private static String authData(String contractNo, BigInteger maxMint) {
        byte[] bytes = contractNo.getBytes(StandardCharsets.UTF_8);
        String tail = String.format("%064x", BigInteger.valueOf(bytes.length));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return String.format("%064x", BigInteger.valueOf(128))
                + String.format("%064x", maxMint)
                + String.format("%064x", BigInteger.ZERO)
                + String.format("%064x", BigInteger.ZERO)
                + tail
                + sb;
    }
}
