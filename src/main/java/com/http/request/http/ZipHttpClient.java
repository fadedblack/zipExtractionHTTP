package com.http.request.http;

import com.http.request.config.ZipExtractionConfig;
import com.http.request.exceptions.ZipHttpException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ZipHttpClient {

    private final HttpClient httpClient;
    private final int maxRetryAttempts;
    private final long baseRetryDelayMs;

    public ZipHttpClient() {
        this.httpClient = createHttpClient();
        this.maxRetryAttempts = ZipExtractionConfig.MAX_RETRY_ATTEMPTS;
        this.baseRetryDelayMs = ZipExtractionConfig.RETRY_DELAY_BASE_MS;
    }

    public ZipHttpClient(int maxRetryAttempts, long baseRetryDelayMs) {
        this.httpClient = createHttpClient();
        this.maxRetryAttempts = maxRetryAttempts;
        this.baseRetryDelayMs = baseRetryDelayMs;
    }

    public byte[] fetchLastBytes(String url, int numBytes) throws ZipHttpException {
        String rangeHeader = "bytes=-" + numBytes;
        log.info("Fetching last {} bytes from URL: {}", numBytes, url);
        return fetchRangeBytes(url, rangeHeader);
    }

    public byte[] fetchBytesAtOffset(String url, long offset, long size) throws ZipHttpException {
        long rangeEnd = offset + size - 1;
        String rangeHeader = String.format("bytes=%d-%d", offset, rangeEnd);
        log.info("Fetching {} bytes at offset {} from URL: {}", size, offset, url);
        return fetchRangeBytes(url, rangeHeader);
    }

    public byte[] fetchBytesWithSafetyBuffer(String url, long offset, long size, int safetyBuffer)
            throws ZipHttpException {
        long rangeEnd = offset + size + safetyBuffer - 1;
        String rangeHeader = String.format("bytes=%d-%d", offset, rangeEnd);
        log.info("Fetching {} bytes with {} safety buffer at offset {} from URL: {}", size, safetyBuffer, offset, url);
        return fetchRangeBytes(url, rangeHeader);
    }

    private byte[] fetchRangeBytes(String url, String rangeHeader) throws ZipHttpException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Range", rangeHeader)
                .timeout(Duration.ofSeconds(ZipExtractionConfig.REQUEST_TIMEOUT_SECONDS))
                .GET()
                .build();

        return executeWithRetry(request, rangeHeader);
    }

    private byte[] executeWithRetry(HttpRequest request, String rangeHeader) throws ZipHttpException {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                log.debug("HTTP request attempt {} with Range: {}", attempt, rangeHeader);
                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

                validateHttpResponse(response.statusCode(), rangeHeader);

                log.info(
                        "Successfully fetched {} bytes with Range: {} (attempt {})",
                        response.body().length,
                        rangeHeader,
                        attempt);
                return response.body();

            } catch (IOException | InterruptedException e) {
                lastException = e;
                log.warn("HTTP request failed on attempt {} with Range: {}: {}", attempt, rangeHeader, e.getMessage());
            }
        }

        String errorMsg =
                String.format("HTTP request failed after %d attempts with Range: %s", maxRetryAttempts, rangeHeader);
        log.error(errorMsg, lastException);
        throw new ZipHttpException(errorMsg, lastException);
    }

    private void validateHttpResponse(int statusCode, String rangeHeader) throws ZipHttpException, IOException {
        if (statusCode == 206) {
            return;
        }

        throw new ZipHttpException("Failed to send request, Status: " + statusCode);
    }

    private HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(ZipExtractionConfig.CONNECT_TIMEOUT_SECONDS))
                .build();
    }

    public void close() {
        log.debug("ZipHttpClient resources released");
    }
}
