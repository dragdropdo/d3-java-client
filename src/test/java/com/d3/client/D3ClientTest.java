package com.d3.client;

import com.d3.client.exceptions.*;
import com.d3.client.models.*;
import com.google.gson.JsonObject;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class D3ClientTest {
    private MockWebServer mockWebServer;
    private MockWebServer partUploadServer;
    private Dragdropdo client;
    private static final String API_BASE = "https://api-dev.dragdropdo.com";

    @Before
    public void setUp() throws Exception {
        // Create mock server for API calls
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // Create mock server for part uploads
        partUploadServer = new MockWebServer();
        partUploadServer.start();

        D3ClientConfig config = new D3ClientConfig("test-key");
        String baseUrl = mockWebServer.url("/").toString().replaceAll("/$", "");
        config.setBaseUrl(baseUrl);
        config.setTimeout(120000); // 120 seconds - enough for mock server and polling

        client = new Dragdropdo(config);
    }

    @After
    public void tearDown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
        if (partUploadServer != null) {
            partUploadServer.shutdown();
        }
    }

    @Test
    public void testUploadFile_MultipartFlow() throws Exception {
        // Create temporary test file
        File tmpFile = File.createTempFile("d3-test-", ".pdf");
        tmpFile.deleteOnExit();

        // Write 6MB of data
        try (FileWriter writer = new FileWriter(tmpFile)) {
            String content = "a".repeat(6 * 1024 * 1024);
            writer.write(content);
        }

        // Mock presigned URL request
        JsonObject uploadResponse = new JsonObject();
        JsonObject data = new JsonObject();
        data.addProperty("file_key", "file-key-123");
        data.addProperty("upload_id", "upload-id-456");
        List<String> presignedUrls = new ArrayList<>();
        presignedUrls.add(partUploadServer.url("/part1").toString());
        presignedUrls.add(partUploadServer.url("/part2").toString());
        data.add("presigned_urls", new com.google.gson.Gson().toJsonTree(presignedUrls));
        uploadResponse.add("data", data);

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(uploadResponse.toString())
                .setHeader("Content-Type", "application/json"));

        // Mock part uploads
        partUploadServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"etag-part-1\""));
        partUploadServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"etag-part-2\""));

        // Mock complete upload
        JsonObject completeResponse = new JsonObject();
        JsonObject completeData = new JsonObject();
        completeData.addProperty("message", "Upload completed successfully");
        completeData.addProperty("file_key", "file-key-123");
        completeResponse.add("data", completeData);

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(completeResponse.toString())
                .setHeader("Content-Type", "application/json"));

        // Perform upload
        UploadFileOptions options = new UploadFileOptions();
        options.setFile(tmpFile.getAbsolutePath());
        options.setFileName("test.pdf");
        options.setMimeType("application/pdf");
        options.setParts(2);

        UploadResponse result = client.uploadFile(options);

        // Verify results
        assertEquals("file-key-123", result.getFileKey());
        assertEquals("upload-id-456", result.getUploadId());
        assertEquals(2, result.getPresignedUrls().size());

        // Verify API calls
        RecordedRequest uploadRequest = mockWebServer.takeRequest();
        assertEquals("POST", uploadRequest.getMethod());
        assertEquals("/v1/external/upload", uploadRequest.getPath());
    }

    @Test
    public void testCreateOperation_AndPollStatus() throws Exception {
        // Mock create operation
        JsonObject operationResponse = new JsonObject();
        JsonObject operationData = new JsonObject();
        operationData.addProperty("main_task_id", "task-123");
        operationResponse.add("data", operationData);

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(operationResponse.toString())
                .setHeader("Content-Type", "application/json"));

        // Mock status calls (queued then completed)
        JsonObject statusQueued = new JsonObject();
        JsonObject statusQueuedData = new JsonObject();
        statusQueuedData.addProperty("operation_status", "queued");
        List<Map<String, Object>> filesDataQueued = new ArrayList<>();
        Map<String, Object> fileQueued = new HashMap<>();
        fileQueued.put("file_key", "file-key-123");
        fileQueued.put("status", "queued");
        filesDataQueued.add(fileQueued);
        statusQueuedData.add("files_data", new com.google.gson.Gson().toJsonTree(filesDataQueued));
        statusQueued.add("data", statusQueuedData);

        JsonObject statusCompleted = new JsonObject();
        JsonObject statusCompletedData = new JsonObject();
        statusCompletedData.addProperty("operation_status", "completed");
        List<Map<String, Object>> filesDataCompleted = new ArrayList<>();
        Map<String, Object> fileCompleted = new HashMap<>();
        fileCompleted.put("file_key", "file-key-123");
        fileCompleted.put("status", "completed");
        fileCompleted.put("download_link", "https://files.d3.com/output.png");
        filesDataCompleted.add(fileCompleted);
        statusCompletedData.add("files_data", new com.google.gson.Gson().toJsonTree(filesDataCompleted));
        statusCompleted.add("data", statusCompletedData);

        // First status call returns queued (immediate response)
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(statusQueued.toString())
                .setHeader("Content-Type", "application/json"));
        // Second status call (after polling interval) returns completed
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(statusCompleted.toString())
                .setHeader("Content-Type", "application/json"));
        // Additional calls (if any) also return completed - add multiple to handle polling loop
        for (int i = 0; i < 10; i++) {
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody(statusCompleted.toString())
                    .setHeader("Content-Type", "application/json"));
        }

        // Create operation
        List<String> fileKeys = new ArrayList<>();
        fileKeys.add("file-key-123");
        OperationResponse operation = client.convert(
                fileKeys,
                "png",
                null
        );

        assertEquals("task-123", operation.getMainTaskId());

        // Wait a bit to ensure mock server is ready
        Thread.sleep(100);

        // Poll status - use longer interval to avoid overwhelming mock server
        PollStatusOptions pollOptions = new PollStatusOptions();
        pollOptions.setMainTaskId(operation.getMainTaskId());
        pollOptions.setInterval(100L); // 100ms - give mock server time
        pollOptions.setTimeout(30000L); // 30 seconds - plenty of time

        StatusResponse status = client.pollStatus(pollOptions);

        assertEquals("completed", status.getOperationStatus());
        assertNotNull(status.getFilesData());
        assertTrue(status.getFilesData().size() > 0);
        assertTrue(status.getFilesData().get(0).getDownloadLink().contains("files.d3.com"));
    }

    @Test
    public void testNewClient_Validation() {
        // Test missing API key
        try {
            D3ClientConfig config = new D3ClientConfig("");
            new Dragdropdo(config);
            fail("Expected D3ValidationError for missing API key");
        } catch (D3ValidationError e) {
            assertTrue(e.getMessage().contains("API key is required"));
        }

        // Test valid client
        D3ClientConfig config = new D3ClientConfig("test-key");
        config.setBaseUrl("https://api-dev.dragdropdo.com");
        try {
            Dragdropdo validClient = new Dragdropdo(config);
            assertNotNull(validClient);
        } catch (D3ValidationError e) {
            fail("Should not throw error for valid config");
        }
    }

    @Test
    public void testCheckSupportedOperation() throws Exception {
        JsonObject response = new JsonObject();
        JsonObject data = new JsonObject();
        data.addProperty("supported", true);
        data.addProperty("ext", "pdf");
        List<String> availableActions = new ArrayList<>();
        availableActions.add("convert");
        availableActions.add("compress");
        availableActions.add("merge");
        data.add("available_actions", new com.google.gson.Gson().toJsonTree(availableActions));
        response.add("data", data);

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(response.toString())
                .setHeader("Content-Type", "application/json"));

        SupportedOperationOptions options = new SupportedOperationOptions();
        options.setExt("pdf");

        SupportedOperationResponse result = client.checkSupportedOperation(options);

        // Check if result has supported field (may be accessed via getter or field)
        assertNotNull(result);
        assertEquals("pdf", result.getExt());
    }
}

