package com.linksprite.tenant;

import java.util.List;

/**
 * 路径 → 授权范围（scope）解析器。
 * <p>
 * 该映射只依赖请求路径，便于单元测试；匹配按最长前缀优先。
 */
public final class ScopeResolver {

    public static final String DEPLOY = "deploy";
    public static final String MINT = "mint";
    public static final String NFT = "nft";
    public static final String AUTH = "auth";
    public static final String KYC = "kyc";
    public static final String WALLET = "wallet";
    public static final String TOKEN = "token";
    public static final String AI_ACCOUNT = "aiaccount";
    public static final String COPYRIGHT = "copyright";
    public static final String READ = "read";

    /** 系统支持的全部 scope（旧版全局 key 视为拥有全部权限） */
    public static final List<String> ALL_SCOPES = List.of(
            DEPLOY, MINT, NFT, AUTH, KYC, WALLET, TOKEN, AI_ACCOUNT, COPYRIGHT, READ);

    private ScopeResolver() {
    }

    /**
     * 解析请求路径对应的 scope；不匹配任何业务前缀时返回 read。
     */
    public static String resolve(String path) {
        String p = normalize(path);
        if (under(p, "/api/deploy")) {
            return DEPLOY;
        }
        if (under(p, "/api/nft/mint")) {
            return MINT;
        }
        if (under(p, "/api/nft")) {
            return NFT;
        }
        if (under(p, "/api/auth")) {
            return AUTH;
        }
        if (under(p, "/api/kyc")) {
            return KYC;
        }
        if (under(p, "/api/wallet")) {
            return WALLET;
        }
        if (under(p, "/api/sct")) {
            return TOKEN;
        }
        if (under(p, "/api/ai-account")) {
            return AI_ACCOUNT;
        }
        if (under(p, "/api/copyright")) {
            return COPYRIGHT;
        }
        return READ;
    }

    /** 去掉查询串、末尾斜杠并转小写，统一为 /api/... 形式 */
    private static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String p = path.trim();
        int q = p.indexOf('?');
        if (q >= 0) {
            p = p.substring(0, q);
        }
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p.toLowerCase();
    }

    /** 路径是否为 base 本身或其子路径（避免 /api/nft 误匹配 /api/nftxxx） */
    private static boolean under(String path, String base) {
        return path.equals(base) || path.startsWith(base + "/");
    }
}
