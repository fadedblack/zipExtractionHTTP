# Zip Extraction Application

Efficiently extract specific files from a remote ZIP archive using HTTP range requests—without downloading or loading the entire ZIP into memory.

## What It Does
- Locates the End Of Central Directory (EOCD) by fetching only the last megabyte of the ZIP file.
- Parses the Central Directory to discover file metadata (names, offsets, sizes, compression method).
- Issues precise HTTP Range requests to fetch only the bytes needed for a given file.
- Decompresses the file payload (supports stored and deflate methods) and streams the result directly, without buffering the entire archive.

## Key Features
- HTTP Range-based selective ZIP entry extraction.
- Supports STORED (no compression) and DEFLATE compression methods.
- No full-archive buffering; only targeted byte windows are retrieved and streamed.
- Robust parsing of central and local file headers.
- Simple, modern API for extracting files by name or streaming output.

## Public API
### ZipExtraction
| Method | Description |
|--------|-------------|
| `ZipExtraction(String zipUrl)` | Creates an extractor bound to a remote ZIP URL (must support HTTP Range). |
| `List<CentralDirectoryInfo> getCentralDirectoryContents()` | Fetches last MB, locates EOCD, parses central directory, returns metadata entries. |
| `byte[] getFileFromFileName(String filename)` | Fetches and decompresses a single file by exact name match; returns raw bytes. |
| `void streamFrom(String filename, StreamProcessorInterface processor)` | Streams decompressed file bytes to a custom processor, ideal for large files. |

### CentralDirectoryInfo (Model)
Represents an individual ZIP entry. Key fields:
- `fileName` – Name of the entry.
- `compressedSize`, `uncompressedSize` – Size metrics.
- `localHeaderOffset` – Byte offset to the local file header.

## Usage Example
```java
String zipUrl = "https://example.com/path/to/archive.zip";
ZipExtraction extraction = new ZipExtraction(zipUrl);

// Discover contents (metadata only)
List<CentralDirectoryInfo> entries = extraction.getCentralDirectoryContents();
entries.forEach(e -> System.out.println(e.getFileName() + " (" + e.getUncompressedSize() + " bytes)"));

// Fetch a single file by name
byte[] fileBytes = extraction.getFileFromFileName("document.txt");
System.out.println("Fetched document.txt length=" + fileBytes.length);

// Stream a file directly to a processor
extraction.streamFrom("largefile.bin", (bytes, name) -> {
    // Handle streamed bytes (e.g., write to disk)
});
```

## Requirements
- Java 21
- Gradle (wrapper included)
- Network access to remote ZIP supporting HTTP Range (S3, most CDN/file servers)

## Build & Test
```bash
./gradlew clean build
./gradlew test
```

## Error Handling
- Throws `ZipHttpException` for HTTP request failures.
- Throws `ZipExtractionException` for ZIP structure issues (EOCD missing, invalid directory size).
- Throws `ZipException` for unsupported compression or decompression errors.

## Security Considerations
- No secrets stored; when using S3, prefer time-bound pre-signed URLs.
- Ensure remote ZIP source is trusted (avoid decompression bombs).

## License
MIT License © 2025 Inkeet
