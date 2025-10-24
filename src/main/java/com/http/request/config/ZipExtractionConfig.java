package com.http.request.config;

public final class ZipExtractionConfig {
    public static final int CONNECT_TIMEOUT_SECONDS = 30;
    public static final int REQUEST_TIMEOUT_SECONDS = 60;
    public static final int LAST_MEGABYTE_SIZE = 1048576;
    public static final int DECOMPRESSION_BUFFER_SIZE = 8192;
    public static final int FILE_FETCH_SAFETY_BUFFER = 1024;
    public static final int MAX_RETRY_ATTEMPTS = 3;
    public static final long RETRY_DELAY_BASE_MS = 1000;
    public static final int EOCD_SIGNATURE = 0x06054b50;
    public static final int CENTRAL_FILE_HEADER_SIGNATURE = 0x02014b50;
    public static final int LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50;
    public static final int COMPRESSION_METHOD_STORED = 0;
    public static final int COMPRESSION_METHOD_DEFLATE = 8;
    public static final int MIN_EOCD_SIZE = 22;
    public static final int MIN_CENTRAL_DIR_HEADER_SIZE = 46;
    public static final int MIN_LOCAL_FILE_HEADER_SIZE = 30;
    public static final int DATA_DESCRIPTOR_FLAG = 0x08;

    private ZipExtractionConfig() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
