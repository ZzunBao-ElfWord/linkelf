package com.linksprite.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 智能合约地址配置
 * 部署合约后填入各合约地址
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "contract")
public class ContractConfig {

    /** B端IP授权管理合约 */
    private String authorizationRegistry;

    /** 统一NFT铸造合约 */
    private String linkspriteNft;

    /** 合规流转策略合约 */
    private String transferPolicy;

    /** 实名认证合约 */
    private String kycRegistry;

    /** SCT算力词元合约 */
    private String spriteComputeToken;

    /** AI账户工厂合约 */
    private String aiAccountFactory;

    /** Gas代付合约 */
    private String paymaster;
}
