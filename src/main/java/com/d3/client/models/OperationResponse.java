package com.d3.client.models;

public class OperationResponse {
    private String mainTaskId;

    public OperationResponse(String mainTaskId) {
        this.mainTaskId = mainTaskId;
    }

    public String getMainTaskId() {
        return mainTaskId;
    }

    // CamelCase alias
    public String getMainTaskIdAlias() {
        return mainTaskId;
    }
}

