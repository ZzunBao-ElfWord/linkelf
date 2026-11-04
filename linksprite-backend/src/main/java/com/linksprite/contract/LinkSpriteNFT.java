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
 * 统一NFT铸造合约封装
 * 对应 Solidity: LinkSpriteNFT.sol
 */
public class LinkSpriteNFT extends BaseContract {

    private static final String ABI = """
            [
              {"type":"constructor","stateMutability":"nonpayable","inputs":[{"name":"_platform","type":"address"},{"name":"_authRegistry","type":"address"},{"name":"_transferPolicy","type":"address"},{"name":"_kycRegistry","type":"address"}]},
              {"type":"function","name":"mintB2B","stateMutability":"nonpayable","inputs":[{"name":"to","type":"address"},{"name":"tokenId","type":"uint256"},{"name":"tokenURI","type":"string"},{"name":"authId","type":"uint256"},{"name":"ipfsHash","type":"string"},{"name":"workHash","type":"string"}],"outputs":[]},
              {"type":"function","name":"mintUGC","stateMutability":"nonpayable","inputs":[{"name":"to","type":"address"},{"name":"tokenId","type":"uint256"},{"name":"tokenURI","type":"string"},{"name":"dciId","type":"string"},{"name":"antTxHash","type":"string"},{"name":"ipfsHash","type":"string"},{"name":"workHash","type":"string"}],"outputs":[]},
              {"type":"function","name":"balanceOf","stateMutability":"view","inputs":[{"name":"owner","type":"address"}],"outputs":[{"name":"","type":"uint256"}]},
              {"type":"function","name":"ownerOf","stateMutability":"view","inputs":[{"name":"tokenId","type":"uint256"}],"outputs":[{"name":"","type":"address"}]},
              {"type":"function","name":"tokenURI","stateMutability":"view","inputs":[{"name":"tokenId","type":"uint256"}],"outputs":[{"name":"","type":"string"}]},
              {"type":"function","name":"approve","stateMutability":"nonpayable","inputs":[{"name":"to","type":"address"},{"name":"tokenId","type":"uint256"}],"outputs":[]},
              {"type":"function","name":"getApproved","stateMutability":"view","inputs":[{"name":"tokenId","type":"uint256"}],"outputs":[{"name":"","type":"address"}]},
              {"type":"function","name":"setApprovalForAll","stateMutability":"nonpayable","inputs":[{"name":"operator","type":"address"},{"name":"approved","type":"bool"}],"outputs":[]},
              {"type":"function","name":"isApprovedForAll","stateMutability":"view","inputs":[{"name":"owner","type":"address"},{"name":"operator","type":"address"}],"outputs":[{"name":"","type":"bool"}]},
              {"type":"function","name":"safeTransferFrom","stateMutability":"nonpayable","inputs":[{"name":"from","type":"address"},{"name":"to","type":"address"},{"name":"tokenId","type":"uint256"}],"outputs":[]},
              {"type":"function","name":"safeTransferFrom","stateMutability":"nonpayable","inputs":[{"name":"from","type":"address"},{"name":"to","type":"address"},{"name":"tokenId","type":"uint256"},{"name":"data","type":"bytes"}],"outputs":[]},
              {"type":"function","name":"transferFrom","stateMutability":"nonpayable","inputs":[{"name":"from","type":"address"},{"name":"to","type":"address"},{"name":"tokenId","type":"uint256"}],"outputs":[]},
              {"type":"function","name":"setPaused","stateMutability":"nonpayable","inputs":[{"name":"tokenId","type":"uint256"},{"name":"paused","type":"bool"}],"outputs":[]},
              {"type":"function","name":"getProofInfo","stateMutability":"view","inputs":[{"name":"tokenId","type":"uint256"}],"outputs":[{"name":"source","type":"uint8"},{"name":"proofType","type":"string"},{"name":"proofId","type":"string"}]},
              {"type":"function","name":"platform","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"address"}]},
              {"type":"function","name":"authRegistry","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"address"}]},
              {"type":"function","name":"transferPolicy","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"address"}]},
              {"type":"function","name":"kycRegistry","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"address"}]},
              {"type":"function","name":"name","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"string"}]},
              {"type":"function","name":"symbol","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"string"}]},
              {"type":"function","name":"totalSupply","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"uint256"}]},
              {"type":"function","name":"extras","stateMutability":"view","inputs":[{"name":"","type":"uint256"}],"outputs":[{"name":"source","type":"uint8"},{"name":"authId","type":"uint256"},{"name":"dciId","type":"string"},{"name":"antTxHash","type":"string"},{"name":"ipfsHash","type":"string"},{"name":"workHash","type":"string"},{"name":"mintTime","type":"uint256"},{"name":"paused","type":"bool"}]},
              {"type":"event","name":"Transfer","anonymous":false,"inputs":[{"indexed":true,"name":"from","type":"address"},{"indexed":true,"name":"to","type":"address"},{"indexed":true,"name":"tokenId","type":"uint256"}]},
              {"type":"event","name":"Approval","anonymous":false,"inputs":[{"indexed":true,"name":"owner","type":"address"},{"indexed":true,"name":"approved","type":"address"},{"indexed":true,"name":"tokenId","type":"uint256"}]},
              {"type":"event","name":"ApprovalForAll","anonymous":false,"inputs":[{"indexed":true,"name":"owner","type":"address"},{"indexed":true,"name":"operator","type":"address"},{"indexed":false,"name":"approved","type":"bool"}]},
              {"type":"event","name":"NFTMinted","anonymous":false,"inputs":[{"indexed":true,"name":"tokenId","type":"uint256"},{"indexed":true,"name":"source","type":"uint8"},{"indexed":true,"name":"to","type":"address"},{"indexed":false,"name":"authId","type":"uint256"},{"indexed":false,"name":"dciId","type":"string"}]},
              {"type":"event","name":"NFTPaused","anonymous":false,"inputs":[{"indexed":true,"name":"tokenId","type":"uint256"},{"indexed":false,"name":"paused","type":"bool"}]}
            ]
            """;

    public LinkSpriteNFT(Client client, CryptoKeyPair cryptoKeyPair, String contractAddress) {
        super(client, cryptoKeyPair, contractAddress, ABI);
    }

    /**
     * B端铸造：绑定授权
     * @param to 接收地址
     * @param tokenId NFT ID
     * @param tokenURI 元数据URI
     * @param authId 授权ID
     * @param ipfsHash 作品IPFS哈希
     * @param workHash 作品内容哈希
     */
    public TransactionResponse mintB2B(
            String to, BigInteger tokenId, String tokenURI,
            BigInteger authId, String ipfsHash, String workHash) {
        return sendTransaction("mintB2B", Arrays.asList(
                to, tokenId, tokenURI, authId, ipfsHash, workHash));
    }

    /**
     * UGC铸造：绑定蚂蚁确权证明
     * @param to 接收地址（创作者）
     * @param tokenId NFT ID
     * @param tokenURI 元数据URI
     * @param dciId DCI标识
     * @param antTxHash 蚂蚁链存证哈希
     * @param ipfsHash 作品IPFS哈希
     * @param workHash 作品内容哈希
     */
    public TransactionResponse mintUGC(
            String to, BigInteger tokenId, String tokenURI,
            String dciId, String antTxHash, String ipfsHash, String workHash) {
        return sendTransaction("mintUGC", Arrays.asList(
                to, tokenId, tokenURI, dciId, antTxHash, ipfsHash, workHash));
    }

    /**
     * 安全转账（受限转赠）
     */
    public TransactionResponse safeTransferFrom(String from, String to, BigInteger tokenId) {
        return sendTransaction("safeTransferFrom", Arrays.asList(from, to, tokenId));
    }

    /**
     * 授权转账
     */
    public TransactionResponse approve(String to, BigInteger tokenId) {
        return sendTransaction("approve", Arrays.asList(to, tokenId));
    }

    /**
     * 设置全局授权
     */
    public TransactionResponse setApprovalForAll(String operator, boolean approved) {
        return sendTransaction("setApprovalForAll", Arrays.asList(operator, approved));
    }

    /**
     * 暂停/恢复NFT流转
     */
    public TransactionResponse setPaused(BigInteger tokenId, boolean paused) {
        return sendTransaction("setPaused", Arrays.asList(tokenId, paused));
    }

    // ========== 查询方法 ==========

    public String ownerOf(BigInteger tokenId) {
        return resultString(call("ownerOf", Collections.singletonList(tokenId)), 0);
    }

    public BigInteger balanceOf(String owner) {
        return resultUint(call("balanceOf", Collections.singletonList(owner)), 0);
    }

    public String tokenURI(BigInteger tokenId) {
        return resultString(call("tokenURI", Collections.singletonList(tokenId)), 0);
    }

    public String getApproved(BigInteger tokenId) {
        return resultString(call("getApproved", Collections.singletonList(tokenId)), 0);
    }

    public boolean isApprovedForAll(String owner, String operator) {
        return resultBool(call("isApprovedForAll", Arrays.asList(owner, operator)), 0);
    }

    public List<String> getProofInfo(BigInteger tokenId) {
        return resultStrings(call("getProofInfo", Collections.singletonList(tokenId)));
    }

    public List<String> extras(BigInteger tokenId) {
        return resultStrings(call("extras", Collections.singletonList(tokenId)));
    }

    public BigInteger totalSupply() {
        return resultUint(call("totalSupply", Collections.emptyList()), 0);
    }
}
