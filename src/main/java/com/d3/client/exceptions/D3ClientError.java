package com.d3.client.exceptions;

public class D3ClientError extends Exception {
    private Integer statusCode;
    private Integer code;
    private Object details;

    public D3ClientError(String message) {
        super(message);
    }

    public D3ClientError(String message, Integer statusCode, Integer code, Object details) {
        super(message);
        this.statusCode = statusCode;
        this.code = code;
        this.details = details;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public Integer getCode() {
        return code;
    }

    public Object getDetails() {
        return details;
    }
}

