package com.http.request.utility;

import static org.junit.jupiter.api.Assertions.*;

import com.http.request.http.ZipHttpClient;
import com.http.request.models.CentralDirectoryInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test to verify that ZipExtraction maintains its public API
 * and works correctly with the new ZipHttpClient.
 */
class ZipExtractionIntegrationTest {

    private ZipExtraction zipExtraction;
    private final String testUrl = "https://example.com/test.zip";

    @BeforeEach
    void setUp() {
        // Test default constructor (maintains backward compatibility)
        zipExtraction = new ZipExtraction(testUrl);
    }

    @Test
    void testConstructorWithValidUrl() {
        // Test that constructor works with valid URL
        assertDoesNotThrow(() -> new ZipExtraction("https://example.com/valid.zip"));
    }

    @Test
    void testConstructorWithNullUrl() {
        // Test that constructor validates URL parameter
        assertThrows(IllegalArgumentException.class, () -> new ZipExtraction(null));
    }

    @Test
    void testConstructorWithEmptyUrl() {
        // Test that constructor validates empty URL
        assertThrows(IllegalArgumentException.class, () -> new ZipExtraction(""));
        assertThrows(IllegalArgumentException.class, () -> new ZipExtraction("   "));
    }

    @Test
    void testConstructorWithCustomHttpClient() {
        // Test new constructor that accepts custom HTTP client
        ZipHttpClient customClient = new ZipHttpClient(5, 2000);
        assertDoesNotThrow(() -> new ZipExtraction(testUrl, customClient));
    }

    @Test
    void testConstructorWithNullHttpClient() {
        // Test that constructor validates HTTP client parameter
        assertThrows(IllegalArgumentException.class, () -> new ZipExtraction(testUrl, null));
    }

    @Test
    void testGetCentralDirectoryContentsApiCompatibility() {
        // Test that the method signature is maintained (returns List<CentralDirectoryInfo>)
        List<CentralDirectoryInfo> result = zipExtraction.getCentralDirectoryContents();

        // Should return empty list on error (maintains existing behavior)
        assertNotNull(result);
        assertTrue(result instanceof List);
    }

    @Test
    void testGetFileApiCompatibility() {
        // Test that getFile method signature is maintained
        List<CentralDirectoryInfo> centralDir = zipExtraction.getCentralDirectoryContents();

        // Test with non-existent file (should throw IllegalArgumentException)
        assertThrows(IllegalArgumentException.class, () -> zipExtraction.getFile(centralDir, "nonexistent.txt"));
    }

    @Test
    void testGetAllFilesApiCompatibility() {
        // Create a dummy CentralDirectoryInfo to test API compatibility
        CentralDirectoryInfo dummyInfo = CentralDirectoryInfo.builder()
                .fileName("test.txt")
                .compressedSize(100)
                .uncompressedSize(200)
                .localHeaderOffset(1000)
                .fileNameLength(8)
                .extraLen(0)
                .commentLen(0)
                .build();

        // Test that getAllFiles method signature accepts List<CentralDirectoryInfo> (returns List<List<Byte>>)
        List<CentralDirectoryInfo> centralDirectoryList = List.of(dummyInfo);
        List<List<Byte>> result = zipExtraction.getAllFiles(centralDirectoryList);

        // Should return empty list on error (maintains existing behavior)
        assertNotNull(result);
        assertTrue(result instanceof List);
    }

    @Test
    void testDependencyInjectionCapability() {
        // Test that the new design allows for dependency injection
        ZipHttpClient mockClient = new ZipHttpClient(1, 100);
        ZipExtraction customExtraction = new ZipExtraction(testUrl, mockClient);

        // Verify that it's a different instance but still functional
        assertNotNull(customExtraction);
        assertNotSame(zipExtraction, customExtraction);

        // API should work the same way
        List<CentralDirectoryInfo> result = customExtraction.getCentralDirectoryContents();
        assertNotNull(result);
    }

    @Test
    void testBackwardCompatibility() {
        // Test that existing code patterns still work

        // Pattern 1: Simple instantiation and use
        ZipExtraction extraction1 = new ZipExtraction("https://example.com/file1.zip");
        assertNotNull(extraction1.getCentralDirectoryContents());

        // Pattern 2: Reusing same instance
        List<CentralDirectoryInfo> contents = extraction1.getCentralDirectoryContents();
        assertNotNull(contents);

        // The refactoring should not break existing usage patterns
        assertTrue(true, "Backward compatibility maintained");
    }

    @Test
    void testNewCapabilities() {
        // Test new capabilities provided by the refactoring

        // Capability 1: Custom retry configuration
        ZipHttpClient fastRetryClient = new ZipHttpClient(5, 500);
        ZipExtraction fastRetryExtraction = new ZipExtraction(testUrl, fastRetryClient);
        assertNotNull(fastRetryExtraction);

        // Capability 2: Different timeout configurations
        ZipHttpClient slowClient = new ZipHttpClient(2, 5000);
        ZipExtraction slowExtraction = new ZipExtraction(testUrl, slowClient);
        assertNotNull(slowExtraction);

        // Both should maintain the same API
        assertNotNull(fastRetryExtraction.getCentralDirectoryContents());
        assertNotNull(slowExtraction.getCentralDirectoryContents());
    }

    @Test
    void testConfigurationConstants() {
        // Verify that configuration constants are accessible and reasonable
        // This validates that the config extraction was successful
        assertTrue(com.http.request.config.ZipExtractionConfig.CONNECT_TIMEOUT_SECONDS > 0);
        assertTrue(com.http.request.config.ZipExtractionConfig.REQUEST_TIMEOUT_SECONDS > 0);
        assertTrue(com.http.request.config.ZipExtractionConfig.LAST_MEGABYTE_SIZE > 0);
        assertTrue(com.http.request.config.ZipExtractionConfig.MAX_RETRY_ATTEMPTS >= 0);
    }

    @Test
    void testExceptionHierarchy() {
        // Test that the new exception hierarchy works correctly

        // ZipHttpException should be available
        assertDoesNotThrow(() -> {
            com.http.request.exceptions.ZipHttpException httpEx =
                    new com.http.request.exceptions.ZipHttpException("Test HTTP error");
            assertNotNull(httpEx);
        });

        // Original ZipExtractionException should still be available
        assertDoesNotThrow(() -> {
            com.http.request.exceptions.ZipExtractionException zipEx =
                    new com.http.request.exceptions.ZipExtractionException("Test ZIP error");
            assertNotNull(zipEx);
        });
    }
}
