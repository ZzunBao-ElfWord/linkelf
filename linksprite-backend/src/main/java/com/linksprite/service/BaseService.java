package com.linksprite.service;

import com.linksprite.common.ChainException;
import com.linksprite.dto.response.ChainTxResult;
import org.fisco.bcos.sdk.v3.transaction.model.dto.TransactionResponse;

/**
 * 服务基类，提供公共方法
 */
public abstract class BaseService {

    /**
     * 链状态检查由子类注入 ChainStatusService 后调用
     */
    protected void requireChain(ChainStatusService chainStatus) {
        chainStatus.requireConnected();
    }

    /**
     * 将合约交易响应转换为统一结果
     */
    protected ChainTxResult toTxResult(TransactionResponse response) {
        ChainTxResult result = new ChainTxResult();
        if (response != null && response.getTransactionReceipt() != null) {
            result.setTxHash(response.getTransactionReceipt().getTransactionHash());
            result.setBlockNumber(response.getTransactionReceipt().getBlockNumber());
            result.setStatus(response.getTransactionReceipt().getStatus());
            result.setMessage(response.getTransactionReceipt().getMessage());
            if (response.getReturnObject() != null) {
                result.setOutput(response.getReturnObject().toString());
            }
        }
        return result;
    }

    /**
     * 校验交易结果，失败则抛异常
     */
    protected ChainTxResult checkTxResult(TransactionResponse response) {
        ChainTxResult result = toTxResult(response);
        if (!result.isSuccess()) {
            throw new ChainException(
                    "链上交易失败: " + result.getMessage(),
                    result.getTxHash(),
                    result.getStatus());
        }
        return result;
    }
}
