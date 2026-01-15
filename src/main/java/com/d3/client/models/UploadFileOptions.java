package com.d3.client.models;

import java.util.function.Consumer;

public class UploadFileOptions {
    private String file;
    private String fileName;
    private String mimeType;
    private int parts;
    private Consumer<UploadProgress> onProgress;

    public String getFile() {
        return file;
    }

    public UploadFileOptions setFile(String file) {
        this.file = file;
        return this;
    }

    public String getFileName() {
        return fileName;
    }

    public UploadFileOptions setFileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    public String getMimeType() {
        return mimeType;
    }

    public UploadFileOptions setMimeType(String mimeType) {
        this.mimeType = mimeType;
        return this;
    }

    public int getParts() {
        return parts;
    }

    public UploadFileOptions setParts(int parts) {
        this.parts = parts;
        return this;
    }

    public Consumer<UploadProgress> getOnProgress() {
        return onProgress;
    }

    public UploadFileOptions setOnProgress(Consumer<UploadProgress> onProgress) {
        this.onProgress = onProgress;
        return this;
    }
}

