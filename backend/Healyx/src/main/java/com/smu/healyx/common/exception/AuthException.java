package com.smu.healyx.common.exception;

import org.springframework.http.HttpStatus;

public class AuthException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    public AuthException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() { return errorCode; }
    public HttpStatus getStatus() { return status; }
}
