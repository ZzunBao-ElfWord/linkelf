package com.linksprite.contract;

import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.v3.transaction.model.dto.CallResponse;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 合规流转策略合约封装
 * 对应 Solidity: TransferPolicy.sol
 */
public class TransferPolicy extends BaseContract {

    private static final String ABI = """
            [
              {"type":"constructor","stateMutability":"nonpayable","inputs":[{"name":"_platform","type":"address"}]},
              {"type":"function","name":"canTransfer","stateMutability":"view","inputs":[{"name":"from","type":"address"},{"name":"","type":"address"},{"name":"","type":"uint256"}],"outputs":[{"name":"","type":"bool"}]},
              {"type":"function","name":"recordTransfer","stateMutability":"nonpayable","inputs":[{"name":"from","type":"address"}],"outputs":[]},
              {"type":"function","name":"setNftContract","stateMutability":"nonpayable","inputs":[{"name":"_nft","type":"address"}],"outputs":[]},
              {"type":"function","name":"getTransferStatus","stateMutability":"view","inputs":[{"name":"addr","type":"address"}],"outputs":[{"name":"monthlyCount","type":"uint256"},{"name":"monthlyLimit","type":"uint256"}]},
              {"type":"function","name":"platform","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"address"}]},
              {"type":"function","name":"nftContract","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"address"}]},
              {"type":"function","name":"MAX_TRANSFERS_PER_MONTH","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"uint256"}]},
              {"type":"function","name":"monthlyTransferCount","stateMutability":"view","inputs":[{"name":"","type":"address"},{"name":"","type":"uint256"}],"outputs":[{"name":"","type":"uint256"}]},
              {"type":"event","name":"NftContractBound","anonymous":false,"inputs":[{"indexed":true,"name":"nftContract","type":"address"}]}
            ]
            """;

    public TransferPolicy(Client client, CryptoKeyPair cryptoKeyPair, String contractAddress) {
        super(client, cryptoKeyPair, contractAddress, ABI);
    }

    /**
     * 校验是否可以转赠
     */
    public boolean canTransfer(String from, String to, BigInteger tokenId) {
        CallResponse response = call("canTransfer", Arrays.asList(from, to, tokenId));
        return resultBool(response, 0);
    }

    /**
     * 记录转赠行为
     */
    public void recordTransfer(String from) {
        sendTransaction("recordTransfer", Collections.singletonList(from));
    }

    /**
     * 绑定 NFT 合约地址（部署后由平台调用一次）
     */
    public void setNftContract(String nft) {
        sendTransaction("setNftContract", Collections.singletonList(nft));
    }

    /**
     * 查询转赠状态（[当月已转次数, 每月上限]）
     */
    public List<String> getTransferStatus(String addr) {
        return resultStrings(call("getTransferStatus", Collections.singletonList(addr)));
    }
}
