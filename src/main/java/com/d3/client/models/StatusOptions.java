package com.d3.client.models;

public class StatusOptions {
    private String mainTaskId;
    private String fileTaskId;

    public String getMainTaskId() {
        return mainTaskId;
    }

    public StatusOptions setMainTaskId(String mainTaskId) {
        this.mainTaskId = mainTaskId;
        return this;
    }

    public String getFileTaskId() {
        return fileTaskId;
    }

    public StatusOptions setFileTaskId(String fileTaskId) {
        this.fileTaskId = fileTaskId;
        return this;
    }
}

