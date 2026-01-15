package com.d3.client;

import com.d3.client.exceptions.*;
import com.d3.client.models.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.*;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * D3 Business API Client
 *
 * A Java client library for interacting with the D3 Business API.
 * Provides methods for file uploads, operations, and status checking.
 */
public class D3Client {
    private final String apiKey;
    private final String baseUrl;
    private final long timeout;
    private final Map<String, String> headers;
    private final OkHttpClient httpClient;
    private final Gson gson;

    /**
     * Create a new D3 Client instance
     *
     * @param config Client configuration
     * @throws D3ValidationError if API key is missing
     */
    public D3Client(D3ClientConfig config) throws D3ValidationError {
        if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
            throw new D3ValidationError("API key is required");
        }

        this.apiKey = config.getApiKey();
        this.baseUrl = (config.getBaseUrl() != null && !config.getBaseUrl().isEmpty())
                ? config.getBaseUrl().replaceAll("/$", "")
                : "https://api-dev.dragdropdo.com";
        this.timeout = config.getTimeout() > 0 ? config.getTimeout() : 30000;

        this.headers = new HashMap<>();
        this.headers.put("Content-Type", "application/json");
        this.headers.put("Authorization", "Bearer " + this.apiKey);
        if (config.getHeaders() != null) {
            this.headers.putAll(config.getHeaders());
        }

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(this.timeout, TimeUnit.MILLISECONDS)
                .readTimeout(this.timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(this.timeout, TimeUnit.MILLISECONDS)
                .build();

        this.gson = new Gson();
    }

    /**
     * Upload a file to D3 storage
     *
     * @param options Upload options
     * @return Upload response with file key
     * @throws D3ValidationError|D3UploadError|D3APIError
     */
    public UploadResponse uploadFile(UploadFileOptions options)
            throws D3ValidationError, D3UploadError, D3ClientError {
        if (options.getFileName() == null || options.getFileName().isEmpty()) {
            throw new D3ValidationError("file_name is required");
        }

        if (options.getFile() == null || options.getFile().isEmpty()) {
            throw new D3ValidationError("file must be a file path string");
        }

        File file = new File(options.getFile());
        if (!file.exists()) {
            throw new D3ValidationError("File not found: " + options.getFile());
        }

        long fileSize = file.length();

        // Calculate parts if not provided
        long chunkSize = 5 * 1024 * 1024; // 5MB per part
        int calculatedParts = options.getParts() > 0
                ? options.getParts()
                : (int) Math.ceil((double) fileSize / chunkSize);
        int actualParts = Math.max(1, Math.min(calculatedParts, 100)); // Limit to 100 parts

        // Detect MIME type if not provided
        String detectedMimeType = options.getMimeType();
        if (detectedMimeType == null || detectedMimeType.isEmpty()) {
            String ext = getFileExtension(options.getFileName());
            detectedMimeType = getMimeType(ext);
            if (detectedMimeType == null || detectedMimeType.isEmpty()) {
                try {
                    detectedMimeType = Files.probeContentType(Paths.get(options.getFile()));
                } catch (IOException e) {
                    // Ignore
                }
                if (detectedMimeType == null || detectedMimeType.isEmpty()) {
                    detectedMimeType = "application/octet-stream";
                }
            }
        }

        try {
            // Step 1: Request presigned URLs
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("file_name", options.getFileName());
            requestBody.addProperty("size", fileSize);
            requestBody.addProperty("mime_type", detectedMimeType);
            requestBody.addProperty("parts", actualParts);

            Response response = makeRequest("POST", "/v1/biz/initiate-upload", requestBody.toString());
            JsonObject responseJson = gson.fromJson(response.body().string(), JsonObject.class);
            response.close();

            JsonObject uploadData = responseJson.getAsJsonObject("data");
            String fileKey = uploadData.get("file_key").getAsString();
            String uploadId = uploadData.get("upload_id").getAsString();
            String objectName = uploadData.has("object_name") ? uploadData.get("object_name").getAsString() : null;
            List<String> presignedUrls = new ArrayList<>();
            uploadData.getAsJsonArray("presigned_urls").forEach(
                    url -> presignedUrls.add(url.getAsString())
            );

            if (presignedUrls.size() != actualParts) {
                throw new D3UploadError("Mismatch: requested " + actualParts
                        + " parts but received " + presignedUrls.size() + " presigned URLs");
            }

            if (uploadId == null || uploadId.isEmpty()) {
                throw new D3UploadError("Upload ID not received from server");
            }

            // Step 2: Upload file parts and capture ETags
            long chunkSizePerPart = (fileSize + actualParts - 1) / actualParts;
            long bytesUploaded = 0;
            List<Map<String, Object>> uploadParts = new ArrayList<>();

            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                for (int i = 0; i < actualParts; i++) {
                    long start = i * chunkSizePerPart;
                    long end = Math.min(start + chunkSizePerPart, fileSize);
                    int partSize = (int) (end - start);

                    // Read chunk
                    byte[] chunk = new byte[partSize];
                    raf.seek(start);
                    raf.readFully(chunk);

                    // Upload chunk
                    Request putRequest = new Request.Builder()
                            .url(presignedUrls.get(i))
                            .put(RequestBody.create(chunk, MediaType.parse(detectedMimeType)))
                            .build();

                    Response putResponse = httpClient.newCall(putRequest).execute();
                    if (!putResponse.isSuccessful()) {
                        putResponse.close();
                        throw new D3UploadError("Failed to upload part " + (i + 1));
                    }

                    // Extract ETag from response
                    String etag = putResponse.header("ETag");
                    if (etag == null) {
                        etag = putResponse.header("etag");
                    }
                    if (etag == null || etag.isEmpty()) {
                        putResponse.close();
                        throw new D3UploadError("Failed to get ETag for part " + (i + 1));
                    }
                    etag = etag.replaceAll("^\"|\"$", ""); // Remove quotes if present
                    putResponse.close();

                    Map<String, Object> part = new HashMap<>();
                    part.put("etag", etag);
                    part.put("part_number", i + 1);
                    uploadParts.add(part);

                    bytesUploaded += partSize;

                    // Report progress
                    if (options.getOnProgress() != null) {
                        UploadProgress progress = new UploadProgress(
                                i + 1,
                                actualParts,
                                bytesUploaded,
                                fileSize,
                                (int) ((bytesUploaded * 100) / fileSize)
                        );
                        options.getOnProgress().accept(progress);
                    }
                }
            }

            // Step 3: Complete the multipart upload
            JsonObject completeBody = new JsonObject();
            completeBody.addProperty("file_key", fileKey);
            completeBody.addProperty("upload_id", uploadId);
            if (objectName != null && !objectName.isEmpty()) {
                completeBody.addProperty("object_name", objectName);
            }
            completeBody.add("parts", gson.toJsonTree(uploadParts));

            try {
                makeRequest("POST", "/v1/biz/complete-upload", completeBody.toString()).close();
            } catch (D3ClientError e) {
                throw new D3UploadError("Failed to complete upload: " + e.getMessage());
            }

            return new UploadResponse(fileKey, uploadId, presignedUrls, objectName);
        } catch (D3ClientError e) {
            throw e;
        } catch (Exception e) {
            throw new D3UploadError("Upload failed: " + e.getMessage());
        }
    }

    /**
     * Check if an operation is supported for a file extension
     */
    public SupportedOperationResponse checkSupportedOperation(SupportedOperationOptions options)
            throws D3ValidationError, D3ClientError {
        if (options.getExt() == null || options.getExt().isEmpty()) {
            throw new D3ValidationError("Extension (ext) is required");
        }

        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("ext", options.getExt());
            if (options.getAction() != null) {
                requestBody.addProperty("action", options.getAction());
            }
            if (options.getParameters() != null) {
                requestBody.add("parameters", gson.toJsonTree(options.getParameters()));
            }

            Response response = makeRequest("POST", "/v1/biz/supported-operation",
                    requestBody.toString());
            JsonObject responseJson = gson.fromJson(response.body().string(), JsonObject.class);
            response.close();

            return gson.fromJson(responseJson.get("data"), SupportedOperationResponse.class);
        } catch (D3ClientError e) {
            throw e;
        } catch (Exception e) {
            throw new D3ClientError("Failed to check supported operation: " + e.getMessage());
        }
    }

    /**
     * Create a file operation
     */
    public OperationResponse createOperation(OperationOptions options)
            throws D3ValidationError, D3ClientError {
        if (options.getAction() == null || options.getAction().isEmpty()) {
            throw new D3ValidationError("Action is required");
        }
        if (options.getFileKeys() == null || options.getFileKeys().isEmpty()) {
            throw new D3ValidationError("At least one file key is required");
        }

        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("action", options.getAction());
            requestBody.add("file_keys", gson.toJsonTree(options.getFileKeys()));
            if (options.getParameters() != null) {
                requestBody.add("parameters", gson.toJsonTree(options.getParameters()));
            }
            if (options.getNotes() != null) {
                requestBody.add("notes", gson.toJsonTree(options.getNotes()));
            }

            Response response = makeRequest("POST", "/v1/biz/do", requestBody.toString());
            JsonObject responseJson = gson.fromJson(response.body().string(), JsonObject.class);
            response.close();

            JsonObject data = responseJson.getAsJsonObject("data");
            // Map snake_case to camelCase
            String mainTaskId = data.has("main_task_id") ? 
                data.get("main_task_id").getAsString() : 
                data.get("mainTaskId").getAsString();
            return new OperationResponse(mainTaskId);
        } catch (D3ClientError e) {
            throw e;
        } catch (Exception e) {
            throw new D3ClientError("Failed to create operation: " + e.getMessage());
        }
    }

    // Convenience methods

    public OperationResponse convert(List<String> fileKeys, String convertTo, Map<String, String> notes)
            throws D3ValidationError, D3ClientError {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("convert_to", convertTo);
        return createOperation(new OperationOptions("convert", fileKeys, parameters, notes));
    }

    public OperationResponse compress(List<String> fileKeys, String compressionValue, Map<String, String> notes)
            throws D3ValidationError, D3ClientError {
        if (compressionValue == null || compressionValue.isEmpty()) {
            compressionValue = "recommended";
        }
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("compression_value", compressionValue);
        return createOperation(new OperationOptions("compress", fileKeys, parameters, notes));
    }

    public OperationResponse merge(List<String> fileKeys, Map<String, String> notes)
            throws D3ValidationError, D3ClientError {
        return createOperation(new OperationOptions("merge", fileKeys, null, notes));
    }

    public OperationResponse zip(List<String> fileKeys, Map<String, String> notes)
            throws D3ValidationError, D3ClientError {
        return createOperation(new OperationOptions("zip", fileKeys, null, notes));
    }

    public OperationResponse share(List<String> fileKeys, Map<String, String> notes)
            throws D3ValidationError, D3ClientError {
        return createOperation(new OperationOptions("share", fileKeys, null, notes));
    }

    public OperationResponse lockPdf(List<String> fileKeys, String password, Map<String, String> notes)
            throws D3ValidationError, D3ClientError {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("password", password);
        return createOperation(new OperationOptions("lock", fileKeys, parameters, notes));
    }

    public OperationResponse unlockPdf(List<String> fileKeys, String password, Map<String, String> notes)
            throws D3ValidationError, D3ClientError {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("password", password);
        return createOperation(new OperationOptions("unlock", fileKeys, parameters, notes));
    }

    public OperationResponse resetPdfPassword(List<String> fileKeys, String oldPassword, String newPassword,
                                               Map<String, String> notes)
            throws D3ValidationError, D3ClientError {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("old_password", oldPassword);
        parameters.put("new_password", newPassword);
        return createOperation(new OperationOptions("reset_password", fileKeys, parameters, notes));
    }

    /**
     * Get operation status
     */
    public StatusResponse getStatus(StatusOptions options) throws D3ValidationError, D3ClientError {
        if (options.getMainTaskId() == null || options.getMainTaskId().isEmpty()) {
            throw new D3ValidationError("main_task_id is required");
        }

        try {
            String url = "/v1/biz/status/" + options.getMainTaskId();
            if (options.getFileTaskId() != null && !options.getFileTaskId().isEmpty()) {
                url += "/" + options.getFileTaskId();
            }

            Response response = makeRequest("GET", url, null);
            JsonObject responseJson = gson.fromJson(response.body().string(), JsonObject.class);
            response.close();

            JsonObject data = responseJson.getAsJsonObject("data");
            
            // Map snake_case to camelCase
            StatusResponse statusResponse = new StatusResponse();
            statusResponse.setOperationStatus(
                data.has("operation_status") ? data.get("operation_status").getAsString() :
                data.has("operationStatus") ? data.get("operationStatus").getAsString() : "queued"
            );
            
            JsonArray filesDataArray = data.getAsJsonArray("files_data");
            if (filesDataArray == null) {
                filesDataArray = data.getAsJsonArray("filesData");
            }
            
            List<FileTaskStatus> filesData = new ArrayList<>();
            if (filesDataArray != null) {
                for (int i = 0; i < filesDataArray.size(); i++) {
                    JsonObject fileObj = filesDataArray.get(i).getAsJsonObject();
                    FileTaskStatus fileStatus = new FileTaskStatus();
                    fileStatus.setFileKey(
                        fileObj.has("file_key") ? fileObj.get("file_key").getAsString() :
                        fileObj.has("fileKey") ? fileObj.get("fileKey").getAsString() : ""
                    );
                    fileStatus.setStatus(fileObj.get("status").getAsString());
                    if (fileObj.has("download_link") || fileObj.has("downloadLink")) {
                        fileStatus.setDownloadLink(
                            fileObj.has("download_link") ? fileObj.get("download_link").getAsString() :
                            fileObj.get("downloadLink").getAsString()
                        );
                    }
                    if (fileObj.has("error_code") || fileObj.has("errorCode")) {
                        fileStatus.setErrorCode(
                            fileObj.has("error_code") ? fileObj.get("error_code").getAsString() :
                            fileObj.get("errorCode").getAsString()
                        );
                    }
                    if (fileObj.has("error_message") || fileObj.has("errorMessage")) {
                        fileStatus.setErrorMessage(
                            fileObj.has("error_message") ? fileObj.get("error_message").getAsString() :
                            fileObj.get("errorMessage").getAsString()
                        );
                    }
                    filesData.add(fileStatus);
                }
            }
            statusResponse.setFilesData(filesData);
            
            return statusResponse;
        } catch (D3ClientError e) {
            throw e;
        } catch (Exception e) {
            throw new D3ClientError("Failed to get status: " + e.getMessage());
        }
    }

    /**
     * Poll operation status until completion or failure
     */
    public StatusResponse pollStatus(PollStatusOptions options)
            throws D3ValidationError, D3ClientError, D3TimeoutError {
        long interval = options.getInterval() > 0 ? options.getInterval() : 2000;
        long timeout = options.getTimeout() > 0 ? options.getTimeout() : 300000;
        long startTime = System.currentTimeMillis();

        while (true) {
            // Check timeout
            if (System.currentTimeMillis() - startTime > timeout) {
                throw new D3TimeoutError("Polling timed out after " + timeout + "ms");
            }

            // Get status
            StatusResponse status = getStatus(options);

            // Call update callback
            if (options.getOnUpdate() != null) {
                options.getOnUpdate().accept(status);
            }

            // Check if completed or failed
            if ("completed".equals(status.getOperationStatus()) || "failed".equals(status.getOperationStatus())) {
                return status;
            }

            // Wait before next poll
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new D3ClientError("Polling interrupted");
            }
        }
    }

    private Response makeRequest(String method, String endpoint, String body)
            throws D3APIError, D3ClientError {
        String url = baseUrl + endpoint;
        Request.Builder requestBuilder = new Request.Builder().url(url);

        if (body != null) {
            requestBuilder.method(method, RequestBody.create(body, MediaType.parse("application/json")));
        } else {
            requestBuilder.method(method, null);
        }

        // Add headers
        for (Map.Entry<String, String> header : headers.entrySet()) {
            requestBuilder.addHeader(header.getKey(), header.getValue());
        }

        try {
            Response response = httpClient.newCall(requestBuilder.build()).execute();
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                response.close();
                JsonObject errorJson = gson.fromJson(errorBody, JsonObject.class);
                String message = errorJson.has("message") ? errorJson.get("message").getAsString()
                        : (errorJson.has("error") ? errorJson.get("error").getAsString()
                        : "API request failed");
                Integer code = errorJson.has("code") ? errorJson.get("code").getAsInt() : null;
                throw new D3APIError(message, response.code(), code);
            }
            return response;
        } catch (D3ClientError e) {
            throw e;
        } catch (Exception e) {
            throw new D3ClientError("Network error: " + e.getMessage());
        }
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot >= 0 ? fileName.substring(lastDot).toLowerCase() : "";
    }

    private String getMimeType(String ext) {
        Map<String, String> mimeTypes = new HashMap<>();
        mimeTypes.put(".pdf", "application/pdf");
        mimeTypes.put(".jpg", "image/jpeg");
        mimeTypes.put(".jpeg", "image/jpeg");
        mimeTypes.put(".png", "image/png");
        mimeTypes.put(".gif", "image/gif");
        mimeTypes.put(".webp", "image/webp");
        mimeTypes.put(".doc", "application/msword");
        mimeTypes.put(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        mimeTypes.put(".xls", "application/vnd.ms-excel");
        mimeTypes.put(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        mimeTypes.put(".zip", "application/zip");
        mimeTypes.put(".txt", "text/plain");
        mimeTypes.put(".mp4", "video/mp4");
        mimeTypes.put(".mp3", "audio/mpeg");

        return mimeTypes.get(ext.toLowerCase());
    }
}

