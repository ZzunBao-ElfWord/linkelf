package com.linksprite.service;

import com.linksprite.contract.AuthorizationRegistry;
import com.linksprite.dto.response.ChainTxResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;

/**
 * B端IP授权管理服务
 */
@Slf4j
@Service
public class AuthorizationService extends BaseService {

    @Autowired
    private ChainStatusService chainStatus;

    /** 链未启用时该 Bean 不存在，可选注入 */
    @Autowired(required = false)
    private AuthorizationRegistry authorizationRegistry;

    /**
     * 登记B端IP授权
     */
    public ChainTxResult register(
            String ipOwner, String contractNo, String workHash,
            BigInteger startTime, BigInteger endTime,
            Integer scope, BigInteger maxMint) {
        requireChain(chainStatus);
        log.info("登记IP授权: contractNo={}, ipOwner={}, maxMint={}", contractNo, ipOwner, maxMint);
        return checkTxResult(authorizationRegistry.registerAuthorization(
                ipOwner, contractNo, workHash, startTime, endTime,
                BigInteger.valueOf(scope), maxMint));
    }

    /**
     * 撤销授权
     */
    public ChainTxResult revoke(BigInteger authId) {
        requireChain(chainStatus);
        log.info("撤销授权: authId={}", authId);
        return checkTxResult(authorizationRegistry.revokeAuthorization(authId));
    }

    /**
     * 校验授权是否有效
     */
    public boolean isValid(BigInteger authId) {
        requireChain(chainStatus);
        return authorizationRegistry.isValid(authId);
    }

    /**
     * 查询授权详情
     */
    public String[] getAuthorization(BigInteger authId) {
        requireChain(chainStatus);
        return authorizationRegistry.getAuthorization(authId).toArray(new String[0]);
    }

    /**
     * 增加已铸造计数（NFT铸造成功后内部调用）
     */
    public void incrementMintedCount(BigInteger authId) {
        requireChain(chainStatus);
        authorizationRegistry.incrementMintedCount(authId);
    }

    /**
     * 全量授权列表：按 nextAuthId 顺序遍历链上登记。
     * B端授权量级小（合同数），全量遍历即可；若未来量级大需改造为合约事件索引。
     */
    public java.util.List<java.util.Map<String, Object>> listAll() {
        requireChain(chainStatus);
        BigInteger upper = authorizationRegistry.nextAuthId();
        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        for (BigInteger i = BigInteger.ONE; i.compareTo(upper) < 0; i = i.add(BigInteger.ONE)) {
            try {
                java.util.List<String> a = authorizationRegistry.getAuthorization(i);
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("authId", i.toString());
                m.put("ipOwner", a.get(0));
                m.put("contractNo", a.get(1));
                m.put("workHash", a.get(2));
                m.put("startTime", a.get(3));
                m.put("endTime", a.get(4));
                m.put("scope", a.get(5));
                m.put("maxMint", a.get(6));
                m.put("mintedCount", a.get(7));
                m.put("revoked", Boolean.parseBoolean(a.get(8)));
                m.put("valid", isValid(i));
                out.add(m);
            } catch (Exception e) {
                log.warn("读取授权 #{} 失败: {}", i, e.getMessage());
            }
        }
        return out;
    }
}
