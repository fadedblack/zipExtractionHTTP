# Zip Extraction Application

Efficient, on-demand extraction of individual files from a remote ZIP archive using HTTP range requests—without downloading or loading the entire ZIP into memory.

## Overview
Traditional ZIP processing requires downloading the full archive or streaming it entirely before accessing specific entries. This application avoids that by:

1. Locating the End Of Central Directory (EOCD) by fetching only the last megabyte of the ZIP.
2. Parsing the Central Directory to discover file metadata (names, offsets, sizes, compression method).
3. Issuing precise HTTP Range requests to fetch only the bytes needed for a given file.
4. Decompressing the file payload (supporting stored and deflate methods) and returning its bytes.

This design dramatically reduces bandwidth and memory usage for large remote ZIP files, especially when only a subset of files is required.

## Key Features
- HTTP Range-based selective ZIP entry extraction.
- Supports STORED (no compression) and DEFLATE compression methods.
- Zero full-archive buffering; only targeted byte windows are retrieved.
- Robust parsing of central and local file headers.
- Pluggable HTTP client with retry logic and configurable timeouts.
- Java 21 + Spring Boot dependency (currently used only for dependency management, no REST layer yet).
- Lombok-based model (`CentralDirectoryInfo`).

## Public API
### ZipExtraction
| Method | Description |
|--------|-------------|
| `ZipExtraction(String zipUrl)` | Creates an extractor bound to a remote ZIP URL (must support HTTP Range). |
| `ZipExtraction(String zipUrl, ZipHttpClient zipHttpClient)` | Same as above with custom HTTP client (retry/backoff customization). |
| `List<CentralDirectoryInfo> getCentralDirectoryContents()` | Fetches last MB, locates EOCD, parses central directory, returns metadata entries. Returns empty list on failure. |
| `List<List<Byte>> getAllFiles(List<CentralDirectoryInfo> entries)` | Iterates all provided entries, range-fetches and decompresses each file, returns list of byte lists (empty lists for failures). |
| `List<Byte> getFile(List<CentralDirectoryInfo> entries, String filename)` | Fetches and decodes a single file by exact name match; throws `IllegalArgumentException` if not found; returns empty list on extraction errors. |

### ZipHttpClient
| Method | Description |
|--------|-------------|
| `byte[] fetchLastBytes(String url, int numBytes)` | Issues `Range: bytes=-N` to retrieve the last N bytes of the remote file. |
| `byte[] fetchBytesAtOffset(String url, long offset, long size)` | Fetches a contiguous byte window `[offset, offset+size-1]`. |
| `byte[] fetchBytesWithSafetyBuffer(String url, long offset, long size, int safetyBuffer)` | Same as above but extends end by `safetyBuffer` bytes to accommodate header variance/data descriptors. |
| `void close()` | Placeholder for resource cleanup (currently no-op). |

### CentralDirectoryInfo (Model)
Represents either overall central directory context or an individual entry. Key fields:
- `fileName` – Name of the entry.
- `compressedSize`, `uncompressedSize` – Size metrics.
- `localHeaderOffset` – Byte offset to the local file header.
- `fileNameLength`, `extraFieldLength`, `fileCommentLength` – Length metadata from headers.
- `extraLen`, `commentLen` – Parsed lengths used during iteration.
- `size`, `offset`, `lastMBBytes` – Used during initial EOCD/central directory discovery.

### Exceptions
| Exception | Trigger |
|-----------|---------|
| `ZipHttpException` | HTTP request failures after retries or unexpected status codes (non-206). |
| `ZipExtractionException` | Structural ZIP issues (EOCD missing, invalid directory size). |
| `ZipException` | Standard Java ZIP parsing/decompression errors (unsupported compression, bad data). |

### Return & Error Behavior Summary
| Method | Success | Failure Mode |
|--------|---------|--------------|
| `getCentralDirectoryContents()` | List of entries | Empty list + logged error |
| `getAllFiles()` | List of decoded file byte lists | Empty list elements for individual failures |
| `getFile()` | Decoded file bytes | Empty list (extraction error) or exception (not found) |

## How It Works (Flow)
1. Fetch last N bytes (`LAST_MEGABYTE_SIZE`) of the ZIP to search for EOCD signature `0x06054b50`.
2. Read central directory size & offset from EOCD.
3. Range-fetch the entire central directory block.
4. Iterate central directory headers (`0x02014b50`) to build `CentralDirectoryInfo` entries.
5. For a requested file:
   - Range-fetch from its local file header offset until compressed data end (plus safety buffer).
   - Parse local header (`0x04034b50`), extract compressed payload.
   - Decompress (if deflate) or return raw bytes (stored).

## Usage Example
```java
String zipUrl = "https://example.com/path/to/archive.zip"; // Could be an HTTPS or pre-signed S3 URL
ZipExtraction extraction = new ZipExtraction(zipUrl);

// Discover contents (metadata only)
List<CentralDirectoryInfo> entries = extraction.getCentralDirectoryContents();
entries.forEach(e -> System.out.println(e.getFileName() + " (" + e.getUncompressedSize() + " bytes)"));

// Fetch a single file by name
List<Byte> fileBytes = extraction.getFile(entries, "document.txt");
byte[] raw = new byte[fileBytes.size()];
for (int i = 0; i < fileBytes.size(); i++) raw[i] = fileBytes.get(i);
System.out.println("Fetched document.txt length=" + raw.length);
```

## Using With AWS S3 (Pre-Signed URL)
You can generate a pre-signed URL and pass it directly as `zipUrl`—no need for direct AWS credentials in this library:
```java
AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1).build();
Date expiration = new Date(System.currentTimeMillis() + 15 * 60 * 1000); // 15 minutes
GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest("my-bucket", "archives/sample.zip")
        .withMethod(HttpMethod.GET)
        .withExpiration(expiration);
URL presigned = s3.generatePresignedUrl(req);
ZipExtraction extraction = new ZipExtraction(presigned.toString());
List<CentralDirectoryInfo> entries = extraction.getCentralDirectoryContents();
```
No AWS SDK calls are required inside the extraction process—the URL acts like any other HTTP endpoint that supports Range requests.

## Requirements
- Java 21
- Gradle (wrapper included)
- Network access to remote ZIP supporting HTTP Range (S3, most CDN/file servers)

## Build & Test
```bash
./gradlew clean build
```
Run only tests:
```bash
./gradlew test
```

## Error Handling
| Scenario | Exception |
|----------|-----------|
| EOCD not found | `ZipExtractionException` |
| Invalid central directory size | `ZipExtractionException` |
| HTTP range failures (exhausted retries) | `ZipHttpException` |
| Unsupported compression method | `ZipException` |

## Performance Notes
- Bandwidth: Only minimal metadata + requested file bytes fetched.
- Memory: Stores at most central directory block + individual file window.
- Decompression uses `Inflater` with an 8 KB buffer; tweak via `ZipExtractionConfig.DECOMPRESSION_BUFFER_SIZE`.

## Limitations / Roadmap
- Only supports standard (non-ZIP64) archives (EOCD parsing assumes 32-bit sizes).
- Does not currently validate CRC or data descriptors for streaming entries.
- No REST or CLI interface yet.
- Future Enhancements:
  - ZIP64 support.
  - CLI tool / REST endpoints.
  - Optional CRC validation.
  - Streaming output (InputStream instead of in-memory byte list).

## Extensibility
Inject a custom `ZipHttpClient` with different retry/backoff strategy:
```java
ZipHttpClient client = new ZipHttpClient(5, 500); // 5 attempts, 500ms base delay
ZipExtraction extraction = new ZipExtraction(zipUrl, client);
```

## Testing Approach
Unit tests validate configuration and error handling. Integration tests exercise construction and retrieval APIs with placeholder URLs. (Real remote ZIP testing requires an accessible archive supporting Range.)

## Security Considerations
- No secrets stored; when using S3, prefer time-bound pre-signed URLs.
- Ensure remote ZIP source is trusted (avoid decompression bombs).
- Caller is responsible for sanitizing file names if persisting to disk.

## Contributing
1. Fork & branch (feature/my-improvement)
2. Ensure tests pass: `./gradlew test`
3. Run formatting: Spotless auto-applies on build.
4. Submit PR with clear description.

## License
MIT License © 2025 Inkeet

## Minimal Example Project Snippet
```java
public class Example {
    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : "https://example.com/sample.zip";
        ZipExtraction extraction = new ZipExtraction(url);
        var entries = extraction.getCentralDirectoryContents();
        System.out.println("Found " + entries.size() + " entries");
        entries.stream().limit(5).forEach(e -> System.out.println(e.getFileName()));
    }
}
```

## Why HTTP Range Matters
Range requests let you surgically read only needed byte windows—perfect for ZIP archives whose metadata (central directory) sits at the end. This enables lazy, selective extraction without full archive replication.
