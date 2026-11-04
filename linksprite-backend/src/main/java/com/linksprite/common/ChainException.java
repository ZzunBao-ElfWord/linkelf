package com.linksprite.common;

/**
 * 链上操作异常
 */
public class ChainException extends RuntimeException {

    private final String txHash;
    private final Integer status;

    public ChainException(String message) {
        super(message);
        this.txHash = null;
        this.status = null;
    }

    public ChainException(String message, String txHash, Integer status) {
        super(message);
        this.txHash = txHash;
        this.status = status;
    }

    public ChainException(String message, Throwable cause) {
        super(message, cause);
        this.txHash = null;
        this.status = null;
    }

    public String getTxHash() {
        return txHash;
    }

    public Integer getStatus() {
        return status;
    }
}
