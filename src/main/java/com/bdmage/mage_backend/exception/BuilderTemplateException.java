package com.bdmage.mage_backend.exception;

import org.springframework.http.HttpStatus;

public class BuilderTemplateException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public BuilderTemplateException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return this.status; }
    public String getCode() { return this.code; }
}
