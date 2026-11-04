package com.linksprite.contract;

import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.v3.transaction.model.dto.CallResponse;
import org.fisco.bcos.sdk.v3.transaction.model.dto.TransactionResponse;

import java.util.Collections;

/**
 * 实名认证合约封装
 * 对应 Solidity: KYCRegistry.sol
 */
public class KYCRegistry extends BaseContract {

    private static final String ABI = """
            [
              {"type":"constructor","stateMutability":"nonpayable","inputs":[{"name":"_platform","type":"address"}]},
              {"type":"function","name":"verify","stateMutability":"nonpayable","inputs":[{"name":"user","type":"address"},{"name":"_proofHash","type":"string"}],"outputs":[]},
              {"type":"function","name":"revoke","stateMutability":"nonpayable","inputs":[{"name":"user","type":"address"}],"outputs":[]},
              {"type":"function","name":"isVerified","stateMutability":"view","inputs":[{"name":"user","type":"address"}],"outputs":[{"name":"","type":"bool"}]},
              {"type":"function","name":"platform","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"address"}]},
              {"type":"function","name":"verified","stateMutability":"view","inputs":[{"name":"","type":"address"}],"outputs":[{"name":"","type":"bool"}]},
              {"type":"function","name":"verifiedAt","stateMutability":"view","inputs":[{"name":"","type":"address"}],"outputs":[{"name":"","type":"uint256"}]},
              {"type":"function","name":"proofHash","stateMutability":"view","inputs":[{"name":"","type":"address"}],"outputs":[{"name":"","type":"string"}]},
              {"type":"event","name":"Verified","anonymous":false,"inputs":[{"indexed":true,"name":"user","type":"address"},{"indexed":false,"name":"timestamp","type":"uint256"}]},
              {"type":"event","name":"Revoked","anonymous":false,"inputs":[{"indexed":true,"name":"user","type":"address"},{"indexed":false,"name":"timestamp","type":"uint256"}]}
            ]
            """;

    public KYCRegistry(Client client, CryptoKeyPair cryptoKeyPair, String contractAddress) {
        super(client, cryptoKeyPair, contractAddress, ABI);
    }

    /**
     * 标记用户已实名
     * @param user 用户地址
     * @param proofHash 实名凭证哈希（脱敏）
     */
    public TransactionResponse verify(String user, String proofHash) {
        return sendTransaction("verify", java.util.Arrays.asList(user, proofHash));
    }

    /**
     * 撤销实名
     */
    public TransactionResponse revoke(String user) {
        return sendTransaction("revoke", Collections.singletonList(user));
    }

    /**
     * 校验用户是否已实名
     */
    public boolean isVerified(String user) {
        CallResponse response = call("isVerified", Collections.singletonList(user));
        return resultBool(response, 0);
    }
}
