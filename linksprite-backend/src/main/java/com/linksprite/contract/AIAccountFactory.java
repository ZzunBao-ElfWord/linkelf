package com.linksprite.contract;

import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.v3.transaction.model.dto.CallResponse;
import org.fisco.bcos.sdk.v3.transaction.model.dto.TransactionResponse;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * AI 账户工厂合约封装
 * 对应 Solidity: AIAccountFactory.sol
 */
public class AIAccountFactory extends BaseContract {

    private static final String ABI = """
            [
              {"type":"constructor","stateMutability":"nonpayable","inputs":[{"name":"_platform","type":"address"},{"name":"_sctToken","type":"address"}]},
              {"type":"function","name":"createAIAccount","stateMutability":"nonpayable","inputs":[{"name":"controller","type":"address"}],"outputs":[{"name":"aiAccount","type":"address"}]},
              {"type":"function","name":"createAIContracts","stateMutability":"nonpayable","inputs":[{"name":"controllers","type":"address[]"}],"outputs":[{"name":"","type":"address[]"}]},
              {"type":"function","name":"getAIContractCount","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"uint256"}]},
              {"type":"function","name":"getAllAIContracts","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"address[]"}]},
              {"type":"function","name":"platform","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"address"}]},
              {"type":"function","name":"sctToken","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"address"}]},
              {"type":"function","name":"allAIContracts","stateMutability":"view","inputs":[{"name":"","type":"uint256"}],"outputs":[{"name":"","type":"address"}]},
              {"type":"function","name":"controllerToAccount","stateMutability":"view","inputs":[{"name":"","type":"address"}],"outputs":[{"name":"","type":"address"}]},
              {"type":"event","name":"AIContractCreated","anonymous":false,"inputs":[{"indexed":true,"name":"aiAccount","type":"address"},{"indexed":true,"name":"controller","type":"address"}]}
            ]
            """;

    public AIAccountFactory(Client client, CryptoKeyPair cryptoKeyPair, String contractAddress) {
        super(client, cryptoKeyPair, contractAddress, ABI);
    }

    /**
     * 创建 AI 智能账户
     * @param controller AI 控制器地址
     */
    public TransactionResponse createAIAccount(String controller) {
        return sendTransaction("createAIAccount", Collections.singletonList(controller));
    }

    /**
     * 批量创建 AI 账户
     * @param controllers 控制器地址数组
     */
    public TransactionResponse createAIContracts(String[] controllers) {
        // Solidity address[] 对应 Java List<String>
        return sendTransaction("createAIContracts",
                Collections.singletonList(Arrays.asList(controllers)));
    }

    // ========== 查询 ==========

    public String controllerToAccount(String controller) {
        return resultString(call("controllerToAccount", Collections.singletonList(controller)), 0);
    }

    public BigInteger getAIContractCount() {
        return resultUint(call("getAIContractCount", Collections.emptyList()), 0);
    }

    public List<String> getAllAIContracts() {
        return resultStrings(call("getAllAIContracts", Collections.emptyList()));
    }
}
