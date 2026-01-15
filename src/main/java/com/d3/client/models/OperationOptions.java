package com.d3.client.models;

import java.util.List;
import java.util.Map;

public class OperationOptions {
    private String action;
    private List<String> fileKeys;
    private Map<String, Object> parameters;
    private Map<String, String> notes;

    public OperationOptions(String action, List<String> fileKeys, Map<String, Object> parameters, Map<String, String> notes) {
        this.action = action;
        this.fileKeys = fileKeys;
        this.parameters = parameters;
        this.notes = notes;
    }

    public String getAction() {
        return action;
    }

    public List<String> getFileKeys() {
        return fileKeys;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public Map<String, String> getNotes() {
        return notes;
    }
}

