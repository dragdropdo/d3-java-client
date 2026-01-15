package com.d3.client.models;

import java.util.List;

public class StatusResponse {
    private String operationStatus;
    private List<FileTaskStatus> filesData;

    public String getOperationStatus() {
        return operationStatus;
    }

    public void setOperationStatus(String operationStatus) {
        this.operationStatus = operationStatus;
    }

    public List<FileTaskStatus> getFilesData() {
        return filesData;
    }

    public void setFilesData(List<FileTaskStatus> filesData) {
        this.filesData = filesData;
    }

    // CamelCase aliases
    public String getOperationStatusAlias() {
        return operationStatus;
    }

    public List<FileTaskStatus> getFilesDataAlias() {
        return filesData;
    }
}

