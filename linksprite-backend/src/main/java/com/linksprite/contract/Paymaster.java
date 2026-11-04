package com.linksprite.contract;

import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.v3.transaction.model.dto.CallResponse;
import org.fisco.bcos.sdk.v3.transaction.model.dto.TransactionResponse;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Gas 代付合约封装
 * 对应 Solidity: Paymaster.sol
 */
public class Paymaster extends BaseContract {

    private static final String ABI = """
            [
              {"type":"constructor","stateMutability":"nonpayable","inputs":[{"name":"_platform","type":"address"},{"name":"_sctToken","type":"address"}]},
              {"type":"function","name":"executeForAI","stateMutability":"nonpayable","inputs":[{"name":"aiAccount","type":"address"},{"name":"target","type":"address"},{"name":"data","type":"bytes"}],"outputs":[{"name":"success","type":"bool"},{"name":"result","type":"bytes"}]},
              {"type":"function","name":"setOperationPrice","stateMutability":"nonpayable","inputs":[{"name":"selector","type":"bytes4"},{"name":"price","type":"uint256"}],"outputs":[]},
              {"type":"function","name":"setDefaultPrice","stateMutability":"nonpayable","inputs":[{"name":"price","type":"uint256"}],"outputs":[]},
              {"type":"function","name":"deposit","stateMutability":"payable","inputs":[],"outputs":[]},
              {"type":"function","name":"withdraw","stateMutability":"nonpayable","inputs":[{"name":"amount","type":"uint256"}],"outputs":[]},
              {"type":"receive","stateMutability":"payable"},
              {"type":"function","name":"platform","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"address"}]},
              {"type":"function","name":"sctToken","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"address"}]},
              {"type":"function","name":"defaultPrice","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"uint256"}]},
              {"type":"function","name":"operationPrices","stateMutability":"view","inputs":[{"name":"","type":"bytes4"}],"outputs":[{"name":"","type":"uint256"}]},
              {"type":"event","name":"GasPaid","anonymous":false,"inputs":[{"indexed":true,"name":"aiAccount","type":"address"},{"indexed":true,"name":"target","type":"address"},{"indexed":false,"name":"sctCost","type":"uint256"},{"indexed":false,"name":"gasUsed","type":"uint256"}]},
              {"type":"event","name":"PriceUpdated","anonymous":false,"inputs":[{"indexed":true,"name":"selector","type":"bytes4"},{"indexed":false,"name":"newPrice","type":"uint256"}]}
            ]
            """;

    public Paymaster(Client client, CryptoKeyPair cryptoKeyPair, String contractAddress) {
        super(client, cryptoKeyPair, contractAddress, ABI);
    }

    /**
     * AI 调用此函数执行操作并支付 SCT
     * @param aiAccount AI 智能账户地址
     * @param target 目标合约地址
     * @param data 调用数据（十六进制）
     */
    public TransactionResponse executeForAI(String aiAccount, String target, String data) {
        return sendTransaction("executeForAI", Arrays.asList(aiAccount, target, data));
    }

    /**
     * 设置操作定价
     * @param selector 函数选择器（4字节hex）
     * @param price SCT 价格
     */
    public TransactionResponse setOperationPrice(String selector, BigInteger price) {
        return sendTransaction("setOperationPrice", Arrays.asList(selector, price));
    }

    /**
     * 设置默认价格
     */
    public TransactionResponse setDefaultPrice(BigInteger price) {
        return sendTransaction("setDefaultPrice", Arrays.asList(price));
    }

    // ========== 查询 ==========

    public BigInteger operationPrices(String selector) {
        return resultUint(call("operationPrices", Arrays.asList(selector)), 0);
    }

    public BigInteger defaultPrice() {
        return resultUint(call("defaultPrice", Arrays.asList()), 0);
    }
}
