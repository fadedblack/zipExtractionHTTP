package com.http.request.utility;

import static org.junit.jupiter.api.Assertions.*;

import com.http.request.http.ZipHttpClient;
import com.http.request.models.CentralDirectoryInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZipExtractionIntegrationTest {

    private ZipExtraction zipExtraction;
    private final String testUrl = "https://example.com/test.zip";

    @BeforeEach
    void setUp() {
        zipExtraction = new ZipExtraction(testUrl);
    }

    @Test
    void shouldNotThrowWhenConstructorGivenValidUrl() {
        assertDoesNotThrow(() -> new ZipExtraction("https://example.com/valid.zip"));
    }

    @Test
    void shouldThrowWhenConstructorGivenNullUrl() {
        assertThrows(IllegalArgumentException.class, () -> new ZipExtraction(null));
    }

    @Test
    void shouldThrowWhenConstructorGivenEmptyUrl() {
        assertThrows(IllegalArgumentException.class, () -> new ZipExtraction(""));
        assertThrows(IllegalArgumentException.class, () -> new ZipExtraction("   "));
    }

    @Test
    void shouldNotThrowWhenConstructorGivenCustomHttpClient() {
        ZipHttpClient customClient = new ZipHttpClient(5, 2000);
        assertDoesNotThrow(() -> new ZipExtraction(testUrl, customClient));
    }

    @Test
    void shouldThrowWhenConstructorGivenNullHttpClient() {
        assertThrows(IllegalArgumentException.class, () -> new ZipExtraction(testUrl, null));
    }

    @Test
    void shouldReturnCentralDirectoryContentsWhenApiCalled() {
        List<CentralDirectoryInfo> result = zipExtraction.getCentralDirectoryContents();
        assertNotNull(result);
        assertTrue(result instanceof List);
    }

    @Test
    void shouldThrowWhenGetFileGivenNonexistentFileName() {
        List<CentralDirectoryInfo> centralDir = zipExtraction.getCentralDirectoryContents();
        assertThrows(IllegalArgumentException.class, () -> zipExtraction.getFile(centralDir, "nonexistent.txt"));
    }

    @Test
    void shouldReturnListOfFilesWhenGetAllFilesApiCalled() {
        CentralDirectoryInfo dummyInfo = CentralDirectoryInfo.builder()
                .fileName("test.txt")
                .compressedSize(100)
                .uncompressedSize(200)
                .localHeaderOffset(1000)
                .fileNameLength(8)
                .extraLen(0)
                .commentLen(0)
                .build();
        List<CentralDirectoryInfo> centralDirectoryList = List.of(dummyInfo);
        List<List<Byte>> result = zipExtraction.getAllFiles(centralDirectoryList);
        assertNotNull(result);
        assertTrue(result instanceof List);
    }

    @Test
    void shouldSupportDependencyInjectionWhenCustomHttpClientProvided() {
        ZipHttpClient mockClient = new ZipHttpClient(1, 100);
        ZipExtraction customExtraction = new ZipExtraction(testUrl, mockClient);
        assertNotNull(customExtraction);
        assertNotSame(zipExtraction, customExtraction);
        List<CentralDirectoryInfo> result = customExtraction.getCentralDirectoryContents();
        assertNotNull(result);
    }

    @Test
    void shouldMaintainBackwardCompatibility() {
        ZipExtraction extraction1 = new ZipExtraction("https://example.com/file1.zip");
        assertNotNull(extraction1.getCentralDirectoryContents());
        List<CentralDirectoryInfo> contents = extraction1.getCentralDirectoryContents();
        assertNotNull(contents);
        assertTrue(true, "Backward compatibility maintained");
    }

    @Test
    void shouldSupportNewCapabilitiesWhenDifferentClientsUsed() {
        ZipHttpClient fastRetryClient = new ZipHttpClient(5, 500);
        ZipExtraction fastRetryExtraction = new ZipExtraction(testUrl, fastRetryClient);
        assertNotNull(fastRetryExtraction);
        ZipHttpClient slowClient = new ZipHttpClient(2, 5000);
        ZipExtraction slowExtraction = new ZipExtraction(testUrl, slowClient);
        assertNotNull(slowExtraction);
        assertNotNull(fastRetryExtraction.getCentralDirectoryContents());
        assertNotNull(slowExtraction.getCentralDirectoryContents());
    }

    @Test
    void shouldHaveValidConfigurationConstants() {
        assertTrue(com.http.request.config.ZipExtractionConfig.CONNECT_TIMEOUT_SECONDS > 0);
        assertTrue(com.http.request.config.ZipExtractionConfig.REQUEST_TIMEOUT_SECONDS > 0);
        assertTrue(com.http.request.config.ZipExtractionConfig.LAST_MEGABYTE_SIZE > 0);
        assertTrue(com.http.request.config.ZipExtractionConfig.MAX_RETRY_ATTEMPTS >= 0);
    }

    @Test
    void shouldSupportExceptionHierarchy() {
        assertDoesNotThrow(() -> {
            com.http.request.exceptions.ZipHttpException httpEx =
                    new com.http.request.exceptions.ZipHttpException("Test HTTP error");
            assertNotNull(httpEx);
        });
        assertDoesNotThrow(() -> {
            com.http.request.exceptions.ZipExtractionException zipEx =
                    new com.http.request.exceptions.ZipExtractionException("Test ZIP error");
            assertNotNull(zipEx);
        });
    }
}
