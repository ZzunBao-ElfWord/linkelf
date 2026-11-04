package com.linksprite.explorer;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ExplorerEventAbi 单元测试：真实 events topics/data 解析、topic0 映射、异常容错。
 * 不启动 Spring 上下文，不连接数据库与链节点。
 */
class ExplorerEventAbiTest {

    /** Keccak256("Transfer(address,address,uint256)") */
    private static final String TRANSFER_TOPIC =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";
    /** SM3("AuthorizationRegistered(uint256,string,address,uint256,uint256,uint256)") */
    private static final String AUTH_TOPIC =
            "0x9bb1f93611cdbe58b94c640003a2865e9036fea6495f24ade94860fb3db7691d";

    private final ExplorerEventAbi abi = new ExplorerEventAbi();

    @Test
    void decodeTransferFromRealTopics() {
        List<String> topics = Arrays.asList(
                TRANSFER_TOPIC,
                addressTopic("1111111111111111111111111111111111111111"),
                addressTopic("2222222222222222222222222222222222222222"),
                uintTopic(42));

        Map<String, Object> decoded = abi.decode(topics, "0x");
        assertEquals("Transfer", decoded.get("eventName"));
        assertEquals("Transfer(address,address,uint256)", decoded.get("signature"));

        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) decoded.get("args");
        assertEquals("0x1111111111111111111111111111111111111111", args.get("from"));
        assertEquals("0x2222222222222222222222222222222222222222", args.get("to"));
        assertEquals("42", args.get("tokenId"));
    }

    @Test
    void decodeAuthorizationRegisteredFromRealTopicsAndData() {
        String contractNo = "OPENELF-UGC-20260001";
        List<String> topics = new ArrayList<>();
        topics.add(AUTH_TOPIC);
        topics.add(uintTopic(7));
        topics.add(addressTopic("3333333333333333333333333333333333333333"));

        Map<String, Object> decoded = abi.decode(topics, authData(contractNo, BigInteger.valueOf(1000)));
        assertEquals("AuthorizationRegistered", decoded.get("eventName"));

        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) decoded.get("args");
        assertEquals("7", args.get("authId"));
        assertEquals("0x3333333333333333333333333333333333333333", args.get("ipOwner"));
        assertEquals(contractNo, args.get("contractNo"));
        assertEquals("1000", args.get("maxMint"));
        assertEquals("0", args.get("startTime"));
        assertEquals("0", args.get("endTime"));
    }

    @Test
    void topicMappingRegistersBothSm3AndKeccak() {
        List<String> transferTopics = abi.topicsOf("Transfer");
        assertTrue(transferTopics.contains(TRANSFER_TOPIC.substring(2)));
        assertEquals(2, transferTopics.size());

        List<String> authTopics = abi.topicsOf("AuthorizationRegistered");
        assertTrue(authTopics.contains(AUTH_TOPIC.substring(2)));
        assertTrue(abi.hasEvent("NFTMinted"));
        assertTrue(abi.eventNames().contains("Transfer"));
        assertFalse(abi.eventNames().contains("NotAnEvent"));
        assertTrue(abi.topicsOf("UnknownEvent").isEmpty());
    }

    @Test
    void unknownOrMalformedLogDoesNotThrow() {
        assertNull(abi.decode(List.of(), "0x"));
        assertNull(abi.decode(List.of("0x" + "0".repeat(64)), "0x"));

        List<String> topics = Arrays.asList(
                TRANSFER_TOPIC,
                addressTopic("1111111111111111111111111111111111111111"),
                addressTopic("2222222222222222222222222222222222222222"),
                "0xnot-a-hex-topic");
        Map<String, Object> decoded = abi.decode(topics, "0x");
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) decoded.get("args");
        // 无法解析的 tokenId 保留原值，不抛异常
        assertEquals("0xnot-a-hex-topic", args.get("tokenId"));
    }

    // ---------- 构造真实格式的 topic / data ----------

    private static String addressTopic(String address) {
        return "0x" + "0".repeat(24) + address;
    }

    private static String uintTopic(long value) {
        return "0x" + String.format("%064x", BigInteger.valueOf(value));
    }

    /** AuthorizationRegistered data：offset(string)=128 | maxMint | startTime | endTime | length | string */
    private static String authData(String contractNo, BigInteger maxMint) {
        byte[] bytes = contractNo.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return String.format("%064x", BigInteger.valueOf(128))
                + String.format("%064x", maxMint)
                + String.format("%064x", BigInteger.ZERO)
                + String.format("%064x", BigInteger.ZERO)
                + String.format("%064x", BigInteger.valueOf(bytes.length))
                + sb;
    }
}
