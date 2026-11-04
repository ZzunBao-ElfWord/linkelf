package com.linksprite.contract;

import com.linksprite.config.ContractConfig;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 合约实例工厂
 * 创建所有智能合约的 Java 封装实例并注入 Spring 容器
 * 仅在 fisco.enabled=true 时加载
 */
@Configuration
@ConditionalOnProperty(prefix = "fisco", name = "enabled", havingValue = "true")
public class ContractFactory {

    @Autowired
    private Client client;

    @Autowired
    private CryptoKeyPair cryptoKeyPair;

    @Autowired
    private ContractConfig contractConfig;

    @Bean
    public AuthorizationRegistry authorizationRegistry() {
        return new AuthorizationRegistry(client, cryptoKeyPair, contractConfig.getAuthorizationRegistry());
    }

    @Bean
    public LinkSpriteNFT linkSpriteNFT() {
        return new LinkSpriteNFT(client, cryptoKeyPair, contractConfig.getLinkspriteNft());
    }

    @Bean
    public TransferPolicy transferPolicy() {
        return new TransferPolicy(client, cryptoKeyPair, contractConfig.getTransferPolicy());
    }

    @Bean
    public KYCRegistry kycRegistry() {
        return new KYCRegistry(client, cryptoKeyPair, contractConfig.getKycRegistry());
    }

    @Bean
    public SpriteComputeToken spriteComputeToken() {
        return new SpriteComputeToken(client, cryptoKeyPair, contractConfig.getSpriteComputeToken());
    }

    @Bean
    public AIAccountFactory aiAccountFactory() {
        return new AIAccountFactory(client, cryptoKeyPair, contractConfig.getAiAccountFactory());
    }

    @Bean
    public Paymaster paymaster() {
        return new Paymaster(client, cryptoKeyPair, contractConfig.getPaymaster());
    }
}
