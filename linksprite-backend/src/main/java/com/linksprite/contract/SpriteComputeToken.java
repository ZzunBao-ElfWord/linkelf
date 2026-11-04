package com.linksprite.contract;

import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.v3.transaction.model.dto.CallResponse;
import org.fisco.bcos.sdk.v3.transaction.model.dto.TransactionResponse;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

/**
 * SCT 算力词元合约封装
 * 对应 Solidity: SpriteComputeToken.sol
 */
public class SpriteComputeToken extends BaseContract {

    private static final String ABI = """
            [
              {"type":"constructor","stateMutability":"nonpayable","inputs":[{"name":"_platform","type":"address"}]},
              {"type":"function","name":"registerAIContract","stateMutability":"nonpayable","inputs":[{"name":"aiAccount","type":"address"}],"outputs":[]},
              {"type":"function","name":"removeAIContract","stateMutability":"nonpayable","inputs":[{"name":"aiAccount","type":"address"}],"outputs":[]},
              {"type":"function","name":"setAIFactory","stateMutability":"nonpayable","inputs":[{"name":"factory","type":"address"}],"outputs":[]},
              {"type":"function","name":"balanceOf","stateMutability":"view","inputs":[{"name":"account","type":"address"}],"outputs":[{"name":"","type":"uint256"}]},
              {"type":"function","name":"allowance","stateMutability":"view","inputs":[{"name":"owner","type":"address"},{"name":"spender","type":"address"}],"outputs":[{"name":"","type":"uint256"}]},
              {"type":"function","name":"transfer","stateMutability":"nonpayable","inputs":[{"name":"to","type":"address"},{"name":"amount","type":"uint256"}],"outputs":[{"name":"","type":"bool"}]},
              {"type":"function","name":"approve","stateMutability":"nonpayable","inputs":[{"name":"spender","type":"address"},{"name":"amount","type":"uint256"}],"outputs":[{"name":"","type":"bool"}]},
              {"type":"function","name":"transferFrom","stateMutability":"nonpayable","inputs":[{"name":"from","type":"address"},{"name":"to","type":"address"},{"name":"amount","type":"uint256"}],"outputs":[{"name":"","type":"bool"}]},
              {"type":"function","name":"mintToAI","stateMutability":"nonpayable","inputs":[{"name":"to","type":"address"},{"name":"amount","type":"uint256"}],"outputs":[]},
              {"type":"function","name":"burnFromAI","stateMutability":"nonpayable","inputs":[{"name":"from","type":"address"},{"name":"amount","type":"uint256"}],"outputs":[]},
              {"type":"function","name":"payServiceFee","stateMutability":"nonpayable","inputs":[{"name":"aiAccount","type":"address"},{"name":"amount","type":"uint256"}],"outputs":[]},
              {"type":"function","name":"name","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"string"}]},
              {"type":"function","name":"symbol","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"string"}]},
              {"type":"function","name":"decimals","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"uint8"}]},
              {"type":"function","name":"totalSupply","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"uint256"}]},
              {"type":"function","name":"platform","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"address"}]},
              {"type":"function","name":"aiFactory","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"address"}]},
              {"type":"function","name":"isAIContract","stateMutability":"view","inputs":[{"name":"","type":"address"}],"outputs":[{"name":"","type":"bool"}]},
              {"type":"event","name":"Transfer","anonymous":false,"inputs":[{"indexed":true,"name":"from","type":"address"},{"indexed":true,"name":"to","type":"address"},{"indexed":false,"name":"value","type":"uint256"}]},
              {"type":"event","name":"Approval","anonymous":false,"inputs":[{"indexed":true,"name":"owner","type":"address"},{"indexed":true,"name":"spender","type":"address"},{"indexed":false,"name":"value","type":"uint256"}]},
              {"type":"event","name":"AIContractRegistered","anonymous":false,"inputs":[{"indexed":true,"name":"aiAccount","type":"address"}]},
              {"type":"event","name":"AIContractRemoved","anonymous":false,"inputs":[{"indexed":true,"name":"aiAccount","type":"address"}]},
              {"type":"event","name":"Minted","anonymous":false,"inputs":[{"indexed":true,"name":"to","type":"address"},{"indexed":false,"name":"amount","type":"uint256"}]},
              {"type":"event","name":"Burned","anonymous":false,"inputs":[{"indexed":true,"name":"from","type":"address"},{"indexed":false,"name":"amount","type":"uint256"}]}
            ]
            """;

    public SpriteComputeToken(Client client, CryptoKeyPair cryptoKeyPair, String contractAddress) {
        super(client, cryptoKeyPair, contractAddress, ABI);
    }

    /**
     * 平台铸造 SCT 给 AI 账户
     */
    public TransactionResponse mintToAI(String to, BigInteger amount) {
        return sendTransaction("mintToAI", Arrays.asList(to, amount));
    }

    /**
     * 平台销毁 AI 账户的 SCT
     */
    public TransactionResponse burnFromAI(String from, BigInteger amount) {
        return sendTransaction("burnFromAI", Arrays.asList(from, amount));
    }

    /**
     * AI 向平台支付服务费
     */
    public TransactionResponse payServiceFee(String aiAccount, BigInteger amount) {
        return sendTransaction("payServiceFee", Arrays.asList(aiAccount, amount));
    }

    /**
     * 转账（仅 AI 账户间）
     */
    public TransactionResponse transfer(String to, BigInteger amount) {
        return sendTransaction("transfer", Arrays.asList(to, amount));
    }

    /**
     * 授权
     */
    public TransactionResponse approve(String spender, BigInteger amount) {
        return sendTransaction("approve", Arrays.asList(spender, amount));
    }

    /**
     * 注册 AI 账户到白名单
     */
    public TransactionResponse registerAIContract(String aiAccount) {
        return sendTransaction("registerAIContract", Collections.singletonList(aiAccount));
    }

    /**
     * 移除 AI 账户
     */
    public TransactionResponse removeAIContract(String aiAccount) {
        return sendTransaction("removeAIContract", Collections.singletonList(aiAccount));
    }

    // ========== 查询 ==========

    public BigInteger balanceOf(String account) {
        return resultUint(call("balanceOf", Collections.singletonList(account)), 0);
    }

    public boolean isAIContract(String account) {
        return resultBool(call("isAIContract", Collections.singletonList(account)), 0);
    }

    public BigInteger totalSupply() {
        return resultUint(call("totalSupply", Collections.emptyList()), 0);
    }
}
