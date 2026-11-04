package com.linksprite.service;

import com.linksprite.common.ChainException;
import com.linksprite.config.FiscoBcosConfig;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.v3.client.Client;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;

/**
 * 链连接状态服务
 * 统一判断区块链是否可用，链未连接时调用链上操作会明确失败（不模拟成功）
 */
@Slf4j
@Service
public class ChainStatusService {

    @Autowired
    private FiscoBcosConfig fiscoBcosConfig;

    /** 链启用且 SDK 初始化成功时存在 */
    @Autowired(required = false)
    private Client client;

    /**
     * 链功能是否启用
     */
    public boolean isEnabled() {
        return fiscoBcosConfig.isEnabled();
    }

    /**
     * 链是否已连接（SDK Client 已初始化）
     */
    public boolean isConnected() {
        return client != null;
    }

    /**
     * 要求链已连接，否则抛出异常
     * 所有链上写操作和查询操作入口必须调用此方法
     */
    public void requireConnected() {
        if (!fiscoBcosConfig.isEnabled()) {
            throw new ChainException("区块链功能未启用（fisco.enabled=false），当前为开发模式，无法执行链上操作");
        }
        if (client == null) {
            throw new ChainException("区块链客户端未初始化，请检查节点连接与证书配置");
        }
    }

    /**
     * 查询当前区块高度（链未连接时返回 -1）
     */
    public long getBlockNumber() {
        if (!isConnected()) {
            return -1L;
        }
        try {
            BigInteger blockNumber = client.getBlockNumber().getBlockNumber();
            return blockNumber.longValue();
        } catch (Exception e) {
            log.warn("查询区块高度失败: {}", e.getMessage());
            return -1L;
        }
    }

    /**
     * 获取群组 ID
     */
    public String getGroupId() {
        return fiscoBcosConfig.getGroupId();
    }

    /**
     * 获取链 ID
     */
    public String getChainId() {
        return fiscoBcosConfig.getChainId();
    }
}
