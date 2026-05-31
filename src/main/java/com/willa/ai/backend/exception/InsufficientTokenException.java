package com.willa.ai.backend.exception;

import lombok.Getter;

@Getter
public class InsufficientTokenException extends RuntimeException {

    private final long requiredTokens;
    private final long availableTokens;

    public InsufficientTokenException(long requiredTokens, long availableTokens) {
        super("Ví của bạn không đủ token để phân tích. Cần mua thêm token.");
        this.requiredTokens = requiredTokens;
        this.availableTokens = availableTokens;
    }
}
