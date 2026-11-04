package com.linksprite.contract;

import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.codec.datatypes.Type;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.v3.transaction.manager.AssembleTransactionProcessor;
import org.fisco.bcos.sdk.v3.transaction.manager.TransactionProcessorFactory;
import org.fisco.bcos.sdk.v3.transaction.model.dto.CallResponse;
import org.fisco.bcos.sdk.v3.transaction.model.dto.TransactionResponse;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用合约调用基类
 * 封装 FISCO BCOS SDK 3.x 的交易处理器，提供 sendTransaction 和 call 方法
 * <p>
 * 注意：生产环境建议使用 console genjava 命令从 ABI 自动生成合约 Java 类
 * 本类提供手动封装模板，便于理解调用流程
 */
@Slf4j
public abstract class BaseContract {

    protected final Client client;
    protected final CryptoKeyPair cryptoKeyPair;
    protected final String contractAddress;
    protected final String abi;
    protected final AssembleTransactionProcessor processor;

    public BaseContract(Client client, CryptoKeyPair cryptoKeyPair, String contractAddress, String abi) {
        this.client = client;
        this.cryptoKeyPair = cryptoKeyPair;
        this.contractAddress = contractAddress;
        this.abi = abi;
        try {
            // SDK 3.x 两参数工厂方法
            this.processor = TransactionProcessorFactory.createAssembleTransactionProcessor(
                    client, cryptoKeyPair);
        } catch (Exception e) {
            log.error("创建交易处理器失败", e);
            throw new RuntimeException("交易处理器初始化失败", e);
        }
    }

    /**
     * 合约名称（用于 call 标识，子类可覆盖）
     */
    protected String contractName() {
        return getClass().getSimpleName();
    }

    /**
     * 发送交易（写操作）
     * SDK 3.x 签名：sendTransactionAndGetResponse(address, abi, method, List params)
     */
    protected TransactionResponse sendTransaction(String method, List<Object> params) {
        try {
            log.debug("发送合约交易: 合约={}, 方法={}, 参数={}", contractAddress, method, params);
            TransactionResponse response = processor.sendTransactionAndGetResponse(
                    contractAddress, abi, method, params);

            log.info("交易完成: 方法={}, txHash={}, blockNumber={}, status={}",
                    method,
                    response.getTransactionReceipt().getTransactionHash(),
                    response.getTransactionReceipt().getBlockNumber(),
                    response.getTransactionReceipt().getStatus());

            if (response.getTransactionReceipt().getStatus() != 0) {
                log.error("交易执行失败: status={}, message={}",
                        response.getTransactionReceipt().getStatus(),
                        response.getTransactionReceipt().getMessage());
            }
            return response;
        } catch (Exception e) {
            log.error("合约交易异常: method={}", method, e);
            throw new RuntimeException("合约交易失败: " + method, e);
        }
    }

    /**
     * 调用只读方法（查操作）
     * SDK 3.x 签名：sendCall(contractName, address, abi, method, List params)
     */
    protected CallResponse call(String method, List<Object> params) {
        try {
            log.debug("调用合约查询: 合约={}, 方法={}, 参数={}", contractAddress, method, params);
            CallResponse response = processor.sendCall(
                    contractName(), contractAddress, abi, method, params);

            if (response.getReturnCode() != 0) {
                log.error("合约查询失败: method={}, code={}, message={}",
                        method, response.getReturnCode(), response.getReturnMessage());
            }
            return response;
        } catch (Exception e) {
            log.error("合约查询异常: method={}", method, e);
            throw new RuntimeException("合约查询失败: " + method, e);
        }
    }

    /**
     * 获取交易哈希
     */
    protected String getTxHash(TransactionResponse response) {
        return response.getTransactionReceipt().getTransactionHash();
    }

    // ========== 调用结果提取辅助方法（SDK 3.x getResults 返回 List<Type>） ==========

    /** 提取第 idx 个返回值并转为字符串 */
    protected String resultString(CallResponse resp, int idx) {
        Object value = resp.getResults().get(idx).getValue();
        return value == null ? null : value.toString();
    }

    /** 提取第 idx 个布尔返回值 */
    protected boolean resultBool(CallResponse resp, int idx) {
        return Boolean.TRUE.equals(resp.getResults().get(idx).getValue());
    }

    /** 提取第 idx 个无符号整数返回值 */
    protected BigInteger resultUint(CallResponse resp, int idx) {
        Object value = resp.getResults().get(idx).getValue();
        if (value instanceof BigInteger) {
            return (BigInteger) value;
        }
        return value == null ? null : new BigInteger(value.toString());
    }

    /** 提取全部返回值为字符串列表 */
    protected List<String> resultStrings(CallResponse resp) {
        List<String> list = new ArrayList<>();
        for (Type type : resp.getResults()) {
            Object value = type.getValue();
            list.add(value == null ? null : value.toString());
        }
        return list;
    }

    /**
     * 获取区块高度
     */
    protected BigInteger getBlockNumber(TransactionResponse response) {
        return response.getTransactionReceipt().getBlockNumber();
    }

    public String getContractAddress() {
        return contractAddress;
    }
}
