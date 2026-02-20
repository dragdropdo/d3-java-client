package com.d3.client;

import com.d3.client.models.*;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Live API tests for D3 Java Client
 *
 * These tests make real API calls and require:
 * - RUN_LIVE_TESTS=1 environment variable
 * - D3_API_KEY environment variable with a valid API key
 */
public class D3ClientLiveTest {
    private static final String API_BASE = "https://api-dev.dragdropdo.com";
    private static String apiKey;
    private static boolean runLive;
    private Dragdropdo client;
    private File tmpFile;

    @BeforeClass
    public static void setUpClass() {
        apiKey = System.getenv("D3_API_KEY");
        String runLiveEnv = System.getenv("RUN_LIVE_TESTS");
        runLive = "1".equals(runLiveEnv) && apiKey != null && !apiKey.isEmpty();
    }

    @Before
    public void setUp() throws Exception {
        if (!runLive || apiKey == null || apiKey.isEmpty()) {
            org.junit.Assume.assumeTrue(
                "Skipping live API tests. Set RUN_LIVE_TESTS=1 and D3_API_KEY to run.",
                false
            );
        }

        String apiBase = System.getenv("D3_BASE_URL");
        if (apiBase == null || apiBase.isEmpty()) {
            apiBase = API_BASE;
        }

        D3ClientConfig config = new D3ClientConfig(apiKey);
        config.setBaseUrl(apiBase);
        config.setTimeout(120000L); // 120 seconds

        client = new Dragdropdo(config);

        // Create temporary test file
        tmpFile = File.createTempFile("d3-live-", ".txt");
        tmpFile.deleteOnExit();
        try (FileWriter writer = new FileWriter(tmpFile)) {
            writer.write("hello world");
        }
    }

    @After
    public void tearDown() {
        if (tmpFile != null && tmpFile.exists()) {
            tmpFile.delete();
        }
    }

    @Test
    public void testUploadConvertPollDownload() throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            fail("D3_API_KEY is required for live tests");
        }

        System.out.println("[live-test] Uploading file...");
        UploadFileOptions uploadOptions = new UploadFileOptions();
        uploadOptions.setFile(tmpFile.getAbsolutePath());
        uploadOptions.setFileName("hello.txt");
        uploadOptions.setMimeType("text/plain");
        uploadOptions.setParts(1);

        UploadResponse upload = client.uploadFile(uploadOptions);
        System.out.println("[live-test] Upload result: file_key=" + upload.getFileKey() +
                ", upload_id=" + upload.getUploadId());

        System.out.println("[live-test] Starting convert...");
        List<String> fileKeys = new ArrayList<>();
        fileKeys.add(upload.getFileKey());
        OperationResponse operation = client.convert(fileKeys, "png", null);
        System.out.println("[live-test] Operation: main_task_id=" + operation.getMainTaskId());

        System.out.println("[live-test] Polling status...");
        PollStatusOptions pollOptions = new PollStatusOptions();
        pollOptions.setMainTaskId(operation.getMainTaskId());
        pollOptions.setInterval(3000L); // 3 seconds
        pollOptions.setTimeout(60000L); // 60 seconds

        StatusResponse status = client.pollStatus(pollOptions);
        System.out.println("[live-test] Final status: operation_status=" + status.getOperationStatus());

        assertEquals("completed", status.getOperationStatus());
        assertNotNull(status.getFilesData());
        assertTrue(status.getFilesData().size() > 0);

        String link = status.getFilesData().get(0).getDownloadLink();
        assertNotNull(link);

        if (link != null && !link.isEmpty()) {
            System.out.println("[live-test] Downloading output...");
            URL url = new URL(link);
            URLConnection connection = url.openConnection();
            byte[] data = connection.getInputStream().readAllBytes();
            System.out.println("[live-test] Downloaded bytes: " + data.length);
            assertTrue(data.length > 0);
        }
    }
}

