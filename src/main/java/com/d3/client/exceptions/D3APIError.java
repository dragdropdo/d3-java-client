package com.d3.client.exceptions;

public class D3APIError extends D3ClientError {
    public D3APIError(String message, int statusCode, Integer code) {
        super(message, statusCode, code, null);
    }
}

