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
 * 授权管理合约封装
 * 对应 Solidity: AuthorizationRegistry.sol
 *
 * ABI 获取方式:
 *   1. 编译合约后从编译输出复制 ABI
 *   2. 或使用 FISCO BCOS console: sol2java -a AuthorizationRegistry.abi -p com.linksprite.contract
 */
public class AuthorizationRegistry extends BaseContract {

    // 合约 ABI（依据 AuthorizationRegistry.sol 手工生成，与 solc 编译产物等价；枚举 AuthScope 对应 uint8）
    private static final String ABI = """
            [
              {"type":"constructor","stateMutability":"nonpayable","inputs":[{"name":"_platform","type":"address"}]},
              {"type":"function","name":"registerAuthorization","stateMutability":"nonpayable","inputs":[{"name":"ipOwner","type":"address"},{"name":"contractNo","type":"string"},{"name":"workHash","type":"string"},{"name":"startTime","type":"uint256"},{"name":"endTime","type":"uint256"},{"name":"scope","type":"uint8"},{"name":"maxMint","type":"uint256"}],"outputs":[{"name":"authId","type":"uint256"}]},
              {"type":"function","name":"revokeAuthorization","stateMutability":"nonpayable","inputs":[{"name":"authId","type":"uint256"}],"outputs":[]},
              {"type":"function","name":"isValid","stateMutability":"view","inputs":[{"name":"authId","type":"uint256"}],"outputs":[{"name":"","type":"bool"}]},
              {"type":"function","name":"incrementMintedCount","stateMutability":"nonpayable","inputs":[{"name":"authId","type":"uint256"}],"outputs":[]},
              {"type":"function","name":"getAuthorization","stateMutability":"view","inputs":[{"name":"authId","type":"uint256"}],"outputs":[{"name":"ipOwner","type":"address"},{"name":"contractNo","type":"string"},{"name":"workHash","type":"string"},{"name":"startTime","type":"uint256"},{"name":"endTime","type":"uint256"},{"name":"scope","type":"uint8"},{"name":"maxMint","type":"uint256"},{"name":"mintedCount","type":"uint256"},{"name":"revoked","type":"bool"}]},
              {"type":"function","name":"transferPlatform","stateMutability":"nonpayable","inputs":[{"name":"newPlatform","type":"address"}],"outputs":[]},
              {"type":"function","name":"platform","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"address"}]},
              {"type":"function","name":"nextAuthId","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"uint256"}]},
              {"type":"function","name":"contractToAuthId","stateMutability":"view","inputs":[{"name":"","type":"string"}],"outputs":[{"name":"","type":"uint256"}]},
              {"type":"function","name":"authorizations","stateMutability":"view","inputs":[{"name":"","type":"uint256"}],"outputs":[{"name":"ipOwner","type":"address"},{"name":"contractNo","type":"string"},{"name":"workHash","type":"string"},{"name":"startTime","type":"uint256"},{"name":"endTime","type":"uint256"},{"name":"scope","type":"uint8"},{"name":"maxMint","type":"uint256"},{"name":"mintedCount","type":"uint256"},{"name":"revoked","type":"bool"},{"name":"registeredBy","type":"address"},{"name":"registerTime","type":"uint256"}]},
              {"type":"function","name":"setNftContract","stateMutability":"nonpayable","inputs":[{"name":"_nft","type":"address"}],"outputs":[]},
              {"type":"function","name":"nftContract","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"address"}]},
              {"type":"event","name":"AuthorizationRegistered","anonymous":false,"inputs":[{"indexed":true,"name":"authId","type":"uint256"},{"indexed":false,"name":"contractNo","type":"string"},{"indexed":true,"name":"ipOwner","type":"address"},{"indexed":false,"name":"maxMint","type":"uint256"},{"indexed":false,"name":"startTime","type":"uint256"},{"indexed":false,"name":"endTime","type":"uint256"}]},
              {"type":"event","name":"AuthorizationRevoked","anonymous":false,"inputs":[{"indexed":true,"name":"authId","type":"uint256"},{"indexed":true,"name":"revoker","type":"address"}]},
              {"type":"event","name":"MintedCountIncremented","anonymous":false,"inputs":[{"indexed":true,"name":"authId","type":"uint256"},{"indexed":false,"name":"newCount","type":"uint256"}]},
              {"type":"event","name":"NftContractBound","anonymous":false,"inputs":[{"indexed":true,"name":"nftContract","type":"address"}]}
            ]
            """;

    public AuthorizationRegistry(Client client, CryptoKeyPair cryptoKeyPair, String contractAddress) {
        super(client, cryptoKeyPair, contractAddress, ABI);
    }

    /**
     * 登记B端IP授权
     * @param ipOwner IP方账户地址
     * @param contractNo 授权合同编号
     * @param workHash 授权作品内容哈希
     * @param startTime 授权生效时间（秒）
     * @param endTime 授权到期时间（秒）
     * @param scope 授权范围：0=独家, 1=非独家, 2=可转授权
     * @param maxMint 最大铸造数量
     * @return TransactionResponse（含 txHash, blockNumber）
     */
    public TransactionResponse registerAuthorization(
            String ipOwner, String contractNo, String workHash,
            BigInteger startTime, BigInteger endTime,
            BigInteger scope, BigInteger maxMint) {
        return sendTransaction("registerAuthorization", Arrays.asList(
                ipOwner, contractNo, workHash, startTime, endTime, scope, maxMint));
    }

    /**
     * 撤销授权
     */
    public TransactionResponse revokeAuthorization(BigInteger authId) {
        return sendTransaction("revokeAuthorization", Collections.singletonList(authId));
    }

    /**
     * 增加已铸造计数（NFT铸造成功后调用）
     */
    public TransactionResponse incrementMintedCount(BigInteger authId) {
        return sendTransaction("incrementMintedCount", Collections.singletonList(authId));
    }

    /**
     * 校验授权是否有效
     */
    public boolean isValid(BigInteger authId) {
        CallResponse response = call("isValid", Collections.singletonList(authId));
        return resultBool(response, 0);
    }

    /**
     * 查询授权详情
     */
    public List<String> getAuthorization(BigInteger authId) {
        return resultStrings(call("getAuthorization", Collections.singletonList(authId)));
    }

    /**
     * 根据合同号查询授权ID
     */
    public BigInteger contractToAuthId(String contractNo) {
        return resultUint(call("contractToAuthId", Collections.singletonList(contractNo)), 0);
    }

    /** 授权ID计数（下一可用 authId；列表遍历上界） */
    public BigInteger nextAuthId() {
        return resultUint(call("nextAuthId", Collections.emptyList()), 0);
    }

    /**
     * 绑定 NFT 合约地址（部署后由平台调用一次）
     */
    public TransactionResponse setNftContract(String nft) {
        return sendTransaction("setNftContract", Collections.singletonList(nft));
    }

    /**
     * 查询已绑定的 NFT 合约地址
     */
    public String nftContract() {
        return resultString(call("nftContract", Collections.emptyList()), 0);
    }
}
