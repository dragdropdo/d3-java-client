package com.d3.client.models;

import java.util.List;

public class UploadResponse {
    private String fileKey;
    private String uploadId;
    private List<String> presignedUrls;
    private String objectName;

    public UploadResponse(String fileKey, String uploadId, List<String> presignedUrls, String objectName) {
        this.fileKey = fileKey;
        this.uploadId = uploadId;
        this.presignedUrls = presignedUrls;
        this.objectName = objectName;
    }

    public String getFileKey() {
        return fileKey;
    }

    public String getUploadId() {
        return uploadId;
    }

    public List<String> getPresignedUrls() {
        return presignedUrls;
    }

    public String getObjectName() {
        return objectName;
    }

    // CamelCase aliases for compatibility
    public String getFileKeyAlias() {
        return fileKey;
    }

    public String getUploadIdAlias() {
        return uploadId;
    }

    public List<String> getPresignedUrlsAlias() {
        return presignedUrls;
    }

    public String getObjectNameAlias() {
        return objectName;
    }
}

