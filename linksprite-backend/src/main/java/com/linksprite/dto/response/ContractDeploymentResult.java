package com.linksprite.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 合约批量部署结果
 */
@Data
public class ContractDeploymentResult {

    /** 部署平台地址（SDK 账户） */
    private String platform;

    /** 部署群组 */
    private String groupId;

    /** 各合约部署信息（按部署顺序） */
    private List<DeployedContract> contracts = new ArrayList<>();

    /** 部署后的合约间初始化绑定调用 */
    private List<String> wiring = new ArrayList<>();

    /** 建议写入 application.yml 的 contract 配置段 */
    private String configSnippet;

    @Data
    public static class DeployedContract {
        private String name;
        private String address;
        private String txHash;

        public DeployedContract() {
        }

        public DeployedContract(String name, String address, String txHash) {
            this.name = name;
            this.address = address;
            this.txHash = txHash;
        }
    }
}
