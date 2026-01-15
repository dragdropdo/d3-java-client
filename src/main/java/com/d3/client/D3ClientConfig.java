package com.d3.client;

import java.util.Map;

public class D3ClientConfig {
    private String apiKey;
    private String baseUrl;
    private long timeout;
    private Map<String, String> headers;

    public D3ClientConfig(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    public D3ClientConfig setApiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public D3ClientConfig setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public long getTimeout() {
        return timeout;
    }

    public D3ClientConfig setTimeout(long timeout) {
        this.timeout = timeout;
        return this;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public D3ClientConfig setHeaders(Map<String, String> headers) {
        this.headers = headers;
        return this;
    }
}

