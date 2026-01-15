package com.d3.client.models;

import java.util.Map;

public class SupportedOperationOptions {
    private String ext;
    private String action;
    private Map<String, Object> parameters;

    public String getExt() {
        return ext;
    }

    public SupportedOperationOptions setExt(String ext) {
        this.ext = ext;
        return this;
    }

    public String getAction() {
        return action;
    }

    public SupportedOperationOptions setAction(String action) {
        this.action = action;
        return this;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public SupportedOperationOptions setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
        return this;
    }
}

