package com.d3.client.exceptions;

public class D3ValidationError extends D3ClientError {
    public D3ValidationError(String message) {
        super(message, 400, null, null);
    }
}

