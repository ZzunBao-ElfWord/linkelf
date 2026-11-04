package com.linksprite.dto.response;

import lombok.Data;

import java.math.BigInteger;

/**
 * 链上交易结果
 * 所有写操作返回此对象，必须透出 txHash 和 blockNumber
 */
@Data
public class ChainTxResult {

    /** 交易哈希 */
    private String txHash;

    /** 区块高度 */
    private BigInteger blockNumber;

    /** 交易状态码：0 成功，非 0 失败 */
    private Integer status;

    /** 交易消息 */
    private String message;

    /** 合约返回值（如有） */
    private String output;

    public boolean isSuccess() {
        return status != null && status == 0;
    }
}
