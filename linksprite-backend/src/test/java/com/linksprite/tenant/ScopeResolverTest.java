package com.linksprite.tenant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ScopeResolver 单元测试：验证路径映射规则。
 */
class ScopeResolverTest {

    @Test
    void deployPaths() {
        assertEquals("deploy", ScopeResolver.resolve("/api/deploy"));
        assertEquals("deploy", ScopeResolver.resolve("/api/deploy/precheck"));
        assertEquals("deploy", ScopeResolver.resolve("/api/deploy/contracts"));
    }

    @Test
    void mintAndNftPaths() {
        assertEquals("mint", ScopeResolver.resolve("/api/nft/mint/b2b"));
        assertEquals("mint", ScopeResolver.resolve("/api/nft/mint/ugc"));
        assertEquals("nft", ScopeResolver.resolve("/api/nft"));
        assertEquals("nft", ScopeResolver.resolve("/api/nft/owner/1"));
        assertEquals("nft", ScopeResolver.resolve("/api/nft/transfer"));
    }

    @Test
    void otherBusinessPaths() {
        assertEquals("auth", ScopeResolver.resolve("/api/auth/register"));
        assertEquals("auth", ScopeResolver.resolve("/api/auth/list"));
        assertEquals("kyc", ScopeResolver.resolve("/api/kyc/verify"));
        assertEquals("wallet", ScopeResolver.resolve("/api/wallet/create"));
        assertEquals("token", ScopeResolver.resolve("/api/sct/mint"));
        assertEquals("token", ScopeResolver.resolve("/api/sct/balance/0xabc"));
        assertEquals("aiaccount", ScopeResolver.resolve("/api/ai-account/create"));
        assertEquals("copyright", ScopeResolver.resolve("/api/copyright/deposit"));
    }

    @Test
    void fallbackToRead() {
        assertEquals("read", ScopeResolver.resolve("/api/health"));
        assertEquals("read", ScopeResolver.resolve("/api/work-submissions"));
        assertEquals("read", ScopeResolver.resolve("/api/ai/execute"));
        assertEquals("read", ScopeResolver.resolve("/api"));
        assertEquals("read", ScopeResolver.resolve(null));
        assertEquals("read", ScopeResolver.resolve(""));
        // 前缀必须按路径段匹配，避免 /api/nftxxx 误判为 nft
        assertEquals("read", ScopeResolver.resolve("/api/nftxxx"));
        assertEquals("read", ScopeResolver.resolve("/api/deployx"));
    }

    @Test
    void handlesTrailingSlashAndQuery() {
        assertEquals("nft", ScopeResolver.resolve("/api/nft/owner/1/"));
        assertEquals("mint", ScopeResolver.resolve("/api/nft/mint/b2b?debug=1"));
        assertEquals("read", ScopeResolver.resolve("/api/health/"));
    }
}
