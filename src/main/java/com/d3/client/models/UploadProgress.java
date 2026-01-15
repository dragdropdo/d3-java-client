package com.d3.client.models;

public class UploadProgress {
    private int currentPart;
    private int totalParts;
    private long bytesUploaded;
    private long totalBytes;
    private int percentage;

    public UploadProgress(int currentPart, int totalParts, long bytesUploaded, long totalBytes, int percentage) {
        this.currentPart = currentPart;
        this.totalParts = totalParts;
        this.bytesUploaded = bytesUploaded;
        this.totalBytes = totalBytes;
        this.percentage = percentage;
    }

    public int getCurrentPart() {
        return currentPart;
    }

    public int getTotalParts() {
        return totalParts;
    }

    public long getBytesUploaded() {
        return bytesUploaded;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public int getPercentage() {
        return percentage;
    }
}

