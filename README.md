# DragDropDo Java SDK

Official Java client library for the D3 Business API. This library provides a simple and elegant interface for developers to interact with D3's file processing services.

## Features

- ✅ **File Upload** - Upload files with automatic multipart handling
- ✅ **Operation Support** - Check which operations are available for file types
- ✅ **File Operations** - Convert, compress, merge, zip, and more
- ✅ **Status Polling** - Built-in polling for operation status
- ✅ **Error Handling** - Comprehensive error types and messages
- ✅ **Progress Tracking** - Upload progress callbacks

## Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.d3</groupId>
    <artifactId>dragdropdo-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

Or for Gradle:

```gradle
implementation 'com.d3:dragdropdo-sdk:1.0.0'
```

## Quick Start

```java
import com.d3.client.Dragdropdo;
import com.d3.client.D3ClientConfig;
import com.d3.client.models.*;
import com.d3.client.exceptions.*;

// Initialize the client
Dragdropdo client = new Dragdropdo(
    new D3ClientConfig("your-api-key-here")
        .setBaseUrl("https://api.d3.com")  // Optional, defaults to https://api.d3.com
        .setTimeout(30000)                 // Optional, defaults to 30000ms
);

// Upload a file
UploadResponse uploadResult = client.uploadFile(
    new UploadFileOptions()
        .setFile("/path/to/document.pdf")
        .setFileName("document.pdf")
        .setMimeType("application/pdf")
);

System.out.println("File key: " + uploadResult.getFileKey());

// Check if convert to PNG is supported
SupportedOperationResponse supported = client.checkSupportedOperation(
    new SupportedOperationOptions()
        .setExt("pdf")
        .setAction("convert")
        .setParameters(Map.of("convert_to", "png"))
);

if (supported.isSupported()) {
    // Convert PDF to PNG
    OperationResponse operation = client.convert(
        List.of(uploadResult.getFileKey()),
        "png",
        null
    );

    // Poll for completion
    StatusResponse status = client.pollStatus(
        new PollStatusOptions()
            .setMainTaskId(operation.getMainTaskId())
            .setInterval(2000)  // Check every 2 seconds
            .setOnUpdate(s -> System.out.println("Status: " + s.getOperationStatus()))
    );

    if ("completed".equals(status.getOperationStatus())) {
        System.out.println("Download links:");
        status.getFilesData().forEach(file ->
            System.out.println("  " + file.getDownloadLink())
        );
    }
}
```

## API Reference

### Initialization

#### `new Dragdropdo(D3ClientConfig config)`

Create a new D3 client instance.

**Parameters:**

- `apiKey` (required) - Your D3 API key
- `baseUrl` (optional) - Base URL of the D3 API (default: `"https://api.d3.com"`)
- `timeout` (optional) - Request timeout in milliseconds (default: `30000`)
- `headers` (optional) - Custom headers to include in all requests

**Example:**

```java
Dragdropdo client = new Dragdropdo(
    new D3ClientConfig("your-api-key")
        .setBaseUrl("https://api.d3.com")
        .setTimeout(30000)
);
```

---

### File Upload

#### `uploadFile(UploadFileOptions options)`

Upload a file to D3 storage. This method handles the complete upload flow including multipart uploads.

**Parameters:**

- `file` (required) - File path (string)
- `fileName` (required) - Original file name
- `mimeType` (optional) - MIME type (auto-detected if not provided)
- `parts` (optional) - Number of parts for multipart upload (auto-calculated if not provided)
- `onProgress` (optional) - Progress callback (Consumer<UploadProgress>)

**Returns:** `UploadResponse` with `fileKey` and `presignedUrls`

**Example:**

```java
UploadResponse result = client.uploadFile(
    new UploadFileOptions()
        .setFile("/path/to/file.pdf")
        .setFileName("document.pdf")
        .setMimeType("application/pdf")
        .setOnProgress(progress -> 
            System.out.println("Upload: " + progress.getPercentage() + "%")
        )
);
```

---

### Check Supported Operations

#### `checkSupportedOperation(SupportedOperationOptions options)`

Check which operations are supported for a file extension.

**Parameters:**

- `ext` (required) - File extension (e.g., `"pdf"`, `"jpg"`)
- `action` (optional) - Specific action to check (e.g., `"convert"`, `"compress"`)
- `parameters` (optional) - Parameters for validation (e.g., `Map.of("convert_to", "png")`)

**Returns:** `SupportedOperationResponse` with support information

**Example:**

```java
// Get all available actions for PDF
SupportedOperationResponse result = client.checkSupportedOperation(
    new SupportedOperationOptions().setExt("pdf")
);
System.out.println("Available actions: " + result.getAvailableActions());

// Check if convert to PNG is supported
SupportedOperationResponse result = client.checkSupportedOperation(
    new SupportedOperationOptions()
        .setExt("pdf")
        .setAction("convert")
        .setParameters(Map.of("convert_to", "png"))
);
System.out.println("Supported: " + result.isSupported());
```

---

### Create Operations

#### `createOperation(OperationOptions options)`

Create a file operation (convert, compress, merge, zip, etc.).

**Parameters:**

- `action` (required) - Action to perform: `"convert"`, `"compress"`, `"merge"`, `"zip"`, `"share"`, `"lock"`, `"unlock"`, `"reset_password"`
- `fileKeys` (required) - List of file keys from upload
- `parameters` (optional) - Action-specific parameters
- `notes` (optional) - User metadata

**Returns:** `OperationResponse` with `mainTaskId`

**Example:**

```java
// Convert PDF to PNG
OperationResponse result = client.createOperation(
    new OperationOptions(
        "convert",
        List.of("file-key-123"),
        Map.of("convert_to", "png"),
        Map.of("userId", "user-123")
    )
);
```

#### Convenience Methods

The client also provides convenience methods for common operations:

**Convert:**

```java
client.convert(fileKeys, convertTo, notes);
// Example: client.convert(List.of("file-key-123"), "png", null);
```

**Compress:**

```java
client.compress(fileKeys, compressionValue, notes);
// Example: client.compress(List.of("file-key-123"), "recommended", null);
```

**Merge:**

```java
client.merge(fileKeys, notes);
// Example: client.merge(List.of("file-key-1", "file-key-2"), null);
```

**Zip:**

```java
client.zip(fileKeys, notes);
// Example: client.zip(List.of("file-key-1", "file-key-2"), null);
```

**Share:**

```java
client.share(fileKeys, notes);
// Example: client.share(List.of("file-key-123"), null);
```

**Lock PDF:**

```java
client.lockPdf(fileKeys, password, notes);
// Example: client.lockPdf(List.of("file-key-123"), "secure-password", null);
```

**Unlock PDF:**

```java
client.unlockPdf(fileKeys, password, notes);
// Example: client.unlockPdf(List.of("file-key-123"), "password", null);
```

**Reset PDF Password:**

```java
client.resetPdfPassword(fileKeys, oldPassword, newPassword, notes);
// Example: client.resetPdfPassword(List.of("file-key-123"), "old", "new", null);
```

---

### Get Status

#### `getStatus(StatusOptions options)`

Get the current status of an operation.

**Parameters:**

- `mainTaskId` (required) - Main task ID from operation creation
- `fileTaskId` (optional) - Specific file task ID

**Returns:** `StatusResponse` with operation and file statuses

**Example:**

```java
// Get main task status
StatusResponse status = client.getStatus(
    new StatusOptions().setMainTaskId("task-123")
);

// Get specific file task status
StatusResponse status = client.getStatus(
    new StatusOptions()
        .setMainTaskId("task-123")
        .setFileTaskId("file-task-456")
);

System.out.println("Operation status: " + status.getOperationStatus());
// Possible values: "queued", "running", "completed", "failed"
```

#### `pollStatus(PollStatusOptions options)`

Poll operation status until completion or failure.

**Parameters:**

- `mainTaskId` (required) - Main task ID
- `fileTaskId` (optional) - Specific file task ID
- `interval` (optional) - Polling interval in milliseconds (default: `2000`)
- `timeout` (optional) - Maximum polling duration in milliseconds (default: `300000` = 5 minutes)
- `onUpdate` (optional) - Callback for each status update

**Returns:** `StatusResponse` with final status

**Example:**

```java
StatusResponse status = client.pollStatus(
    new PollStatusOptions()
        .setMainTaskId("task-123")
        .setInterval(2000)  // Check every 2 seconds
        .setTimeout(300000)  // 5 minutes max
        .setOnUpdate(s -> System.out.println("Status: " + s.getOperationStatus()))
);

if ("completed".equals(status.getOperationStatus())) {
    System.out.println("All files processed successfully!");
    status.getFilesData().forEach(file ->
        System.out.println("Download: " + file.getDownloadLink())
    );
}
```

---

## Complete Workflow Example

Here's a complete example showing the typical workflow:

```java
import com.d3.client.Dragdropdo;
import com.d3.client.D3ClientConfig;
import com.d3.client.models.*;
import com.d3.client.exceptions.*;

public class Example {
    public static void main(String[] args) {
        // Initialize client
        Dragdropdo client = new Dragdropdo(
            new D3ClientConfig(System.getenv("D3_API_KEY"))
                .setBaseUrl("https://api.d3.com")
        );

        try {
            // Step 1: Upload file
            System.out.println("Uploading file...");
            UploadResponse uploadResult = client.uploadFile(
                new UploadFileOptions()
                    .setFile("./document.pdf")
                    .setFileName("document.pdf")
                    .setOnProgress(progress ->
                        System.out.println("Upload progress: " + progress.getPercentage() + "%")
                    )
            );
            System.out.println("Upload complete. File key: " + uploadResult.getFileKey());

            // Step 2: Check if operation is supported
            System.out.println("Checking supported operations...");
            SupportedOperationResponse supported = client.checkSupportedOperation(
                new SupportedOperationOptions()
                    .setExt("pdf")
                    .setAction("convert")
                    .setParameters(Map.of("convert_to", "png"))
            );

            if (!supported.isSupported()) {
                throw new Exception("Convert to PNG is not supported for PDF");
            }

            // Step 3: Create operation
            System.out.println("Creating convert operation...");
            OperationResponse operation = client.convert(
                List.of(uploadResult.getFileKey()),
                "png",
                Map.of("userId", "user-123", "source", "api")
            );
            System.out.println("Operation created. Task ID: " + operation.getMainTaskId());

            // Step 4: Poll for completion
            System.out.println("Waiting for operation to complete...");
            StatusResponse status = client.pollStatus(
                new PollStatusOptions()
                    .setMainTaskId(operation.getMainTaskId())
                    .setInterval(2000)
                    .setOnUpdate(s -> System.out.println("Status: " + s.getOperationStatus()))
            );

            // Step 5: Handle result
            if ("completed".equals(status.getOperationStatus())) {
                System.out.println("Operation completed successfully!");
                for (int i = 0; i < status.getFilesData().size(); i++) {
                    FileTaskStatus file = status.getFilesData().get(i);
                    System.out.println("File " + (i + 1) + ":");
                    System.out.println("  Status: " + file.getStatus());
                    System.out.println("  Download: " + file.getDownloadLink());
                }
            } else {
                System.out.println("Operation failed");
                status.getFilesData().forEach(file -> {
                    if (file.getErrorMessage() != null) {
                        System.out.println("Error: " + file.getErrorMessage());
                    }
                });
            }
        } catch (D3APIError e) {
            System.out.println("API Error (" + e.getStatusCode() + "): " + e.getMessage());
        } catch (D3ValidationError e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
```

---

## Error Handling

The client provides several error types for better error handling:

```java
import com.d3.client.exceptions.*;

try {
    client.uploadFile(...);
} catch (D3APIError e) {
    // API returned an error
    System.out.println("API Error (" + e.getStatusCode() + "): " + e.getMessage());
    if (e.getCode() != null) {
        System.out.println("Error code: " + e.getCode());
    }
} catch (D3ValidationError e) {
    // Validation error (missing required fields, etc.)
    System.out.println("Validation Error: " + e.getMessage());
} catch (D3UploadError e) {
    // Upload-specific error
    System.out.println("Upload Error: " + e.getMessage());
} catch (D3TimeoutError e) {
    // Timeout error (from polling)
    System.out.println("Timeout: " + e.getMessage());
} catch (Exception e) {
    // Other errors
    System.out.println("Error: " + e.getMessage());
}
```

---

## Requirements

- Java 11 or higher
- Maven or Gradle

---

## License

ISC

---

## Support

For API documentation and support, visit [D3 Developer Portal](https://developer.d3.com).

