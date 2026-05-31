package com.willa.ai.backend.exception;

/**
 * User gọi refresh persona quá sớm (rate limit).
 */
public class PersonaRateLimitException extends RuntimeException {

    public PersonaRateLimitException(String message) {
        super(message);
    }
}
