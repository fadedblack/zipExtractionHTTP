package com.http.request.config;

/**
 * Configuration constants for ZIP extraction operations.
 * Centralizes all magic numbers and configuration values used throughout the ZIP processing.
 */
public final class ZipExtractionConfig {

    // HTTP Client Configuration
    /**
     * Connection timeout in seconds for HTTP requests.
     */
    public static final int CONNECT_TIMEOUT_SECONDS = 30;

    /**
     * Request timeout in seconds for HTTP requests.
     */
    public static final int REQUEST_TIMEOUT_SECONDS = 60;

    // ZIP File Processing Constants
    /**
     * Size of the last megabyte to fetch for EOCD discovery (1MB in bytes).
     */
    public static final int LAST_MEGABYTE_SIZE = 1048576;

    /**
     * Buffer size for decompression operations (8KB).
     */
    public static final int DECOMPRESSION_BUFFER_SIZE = 8192;

    /**
     * Safety buffer size when fetching file content to account for header variations.
     */
    public static final int FILE_FETCH_SAFETY_BUFFER = 1024;

    // Retry Configuration
    /**
     * Maximum number of retry attempts for failed HTTP requests.
     */
    public static final int MAX_RETRY_ATTEMPTS = 3;

    /**
     * Base delay in milliseconds for exponential backoff retry logic.
     */
    public static final long RETRY_DELAY_BASE_MS = 1000;

    // ZIP File Structure Constants
    /**
     * End of Central Directory (EOCD) record signature.
     */
    public static final int EOCD_SIGNATURE = 0x06054b50;

    /**
     * Central File Header signature.
     */
    public static final int CENTRAL_FILE_HEADER_SIGNATURE = 0x02014b50;

    /**
     * Local File Header signature.
     */
    public static final int LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50;

    // ZIP Compression Methods
    /**
     * ZIP compression method: No compression (stored).
     */
    public static final int COMPRESSION_METHOD_STORED = 0;

    /**
     * ZIP compression method: Deflate compression.
     */
    public static final int COMPRESSION_METHOD_DEFLATE = 8;

    // ZIP File Structure Sizes
    /**
     * Minimum size of EOCD record in bytes.
     */
    public static final int MIN_EOCD_SIZE = 22;

    /**
     * Minimum size of Central Directory File Header in bytes.
     */
    public static final int MIN_CENTRAL_DIR_HEADER_SIZE = 46;

    /**
     * Minimum size of Local File Header in bytes.
     */
    public static final int MIN_LOCAL_FILE_HEADER_SIZE = 30;

    // Bit Flags
    /**
     * General purpose bit flag indicating presence of data descriptor.
     */
    public static final int DATA_DESCRIPTOR_FLAG = 0x08;

    /**
     * Private constructor to prevent instantiation.
     */
    private ZipExtractionConfig() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
