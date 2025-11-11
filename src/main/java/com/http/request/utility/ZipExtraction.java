package com.http.request.utility;

import com.http.request.config.ZipExtractionConfig;
import com.http.request.exceptions.ZipExtractionException;
import com.http.request.exceptions.ZipHttpException;
import com.http.request.http.ZipHttpClient;
import com.http.request.models.CentralDirectoryInfo;
import com.http.request.processors.StreamProcessorInterface;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ZipExtraction {
    private final String zipUrl;
    private final ZipHttpClient zipHttpClient;
    private final List<CentralDirectoryInfo> centralDirectoryInfos = new ArrayList<>();

    public ZipExtraction(String zipUrl) {
        if (zipUrl == null || zipUrl.isBlank()) {
            throw new IllegalArgumentException("ZIP URL is null or empty");
        }
        this.zipUrl = zipUrl;
        this.zipHttpClient = new ZipHttpClient();
    }

    public ZipExtraction(String zipUrl, ZipHttpClient zipHttpClient) {
        if (zipUrl == null || zipUrl.isBlank()) {
            throw new IllegalArgumentException("ZIP URL is null or empty");
        }
        if (zipHttpClient == null) {
            throw new IllegalArgumentException("ZipHttpClient cannot be null");
        }
        this.zipUrl = zipUrl;
        this.zipHttpClient = zipHttpClient;
    }

    public List<CentralDirectoryInfo> getCentralDirectoryContents() {
        log.info("Processing ZIP from URL: {}", zipUrl);
        try {
            byte[] lastMBBytes = fetchLastMegabyte();
            int eocdOffset = findEOCDOffset(lastMBBytes);
            CentralDirectoryInfo cdInfo = readCentralDirectoryInfo(lastMBBytes, eocdOffset);
            byte[] centralDirectoryBytes = fetchCentralDirectoryBytes(cdInfo);
            return parseCentralDirectoryEntries(centralDirectoryBytes);
        } catch (ZipHttpException e) {
            log.error("HTTP error processing ZIP from URL: {}", e.getMessage(), e);
            return List.of();
        } catch (IOException e) {
            log.error("ZIP processing error from URL: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private int findEOCDOffset(byte[] lastMBBytes) throws IOException {
        for (int pos = lastMBBytes.length - 4; pos >= 0; pos--) {
            int sig = ByteBuffer.wrap(lastMBBytes, pos, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getInt();
            if (sig == ZipExtractionConfig.EOCD_SIGNATURE) {
                log.info("Found EOCD at position: {} within last MB range", pos);
                return pos;
            }
        }
        throw new ZipExtractionException("EOCD (End of Central Directory) record not found in last MB of data");
    }

    private byte[] fetchLastMegabyte() throws ZipHttpException {
        return zipHttpClient.fetchLastBytes(zipUrl, ZipExtractionConfig.LAST_MEGABYTE_SIZE);
    }

    private CentralDirectoryInfo readCentralDirectoryInfo(byte[] lastMBBytes, int eocdOffset) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(lastMBBytes).order(ByteOrder.LITTLE_ENDIAN);
        long centralDirSize = Integer.toUnsignedLong(buf.getInt(eocdOffset + 12));
        long centralDirOffset = Integer.toUnsignedLong(buf.getInt(eocdOffset + 16));
        if (centralDirSize <= 0) {
            throw new ZipExtractionException("Invalid central directory size: " + centralDirSize);
        }
        log.info("Central directory size: {}, offset: {}", centralDirSize, centralDirOffset);
        return CentralDirectoryInfo.builder()
                .size(centralDirSize)
                .offset(centralDirOffset)
                .lastMBBytes(lastMBBytes)
                .build();
    }

    private byte[] fetchCentralDirectoryBytes(CentralDirectoryInfo cdInfo) throws ZipHttpException {
        return zipHttpClient.fetchBytesAtOffset(zipUrl, cdInfo.getOffset(), cdInfo.getSize());
    }

    private List<CentralDirectoryInfo> parseCentralDirectoryEntries(byte[] centralDirectoryBytes) {
        int offset = 0;
        log.info("\n--- File details from central directory ---");
        ByteBuffer buf = ByteBuffer.wrap(centralDirectoryBytes).order(ByteOrder.LITTLE_ENDIAN);
        List<CentralDirectoryInfo> entries = new ArrayList<>();
        while (offset + ZipExtractionConfig.MIN_CENTRAL_DIR_HEADER_SIZE <= centralDirectoryBytes.length) {
            int sig = buf.getInt(offset);
            if (sig != ZipExtractionConfig.CENTRAL_FILE_HEADER_SIGNATURE) {
                break;
            }
            long compressedSize = Integer.toUnsignedLong(buf.getInt(offset + 20));
            long uncompressedSize = Integer.toUnsignedLong(buf.getInt(offset + 24));
            long fileNameLength = Short.toUnsignedInt(buf.getShort(offset + 28));
            long extraFieldLength = Short.toUnsignedInt(buf.getShort(offset + 30));
            long fileCommentLength = Short.toUnsignedInt(buf.getShort(offset + 32));
            long localHeaderOffset = Integer.toUnsignedLong(buf.getInt(offset + 42));
            int fileNameLen = Short.toUnsignedInt(buf.getShort(offset + 28));
            int extraLen = Short.toUnsignedInt(buf.getShort(offset + 30));
            int commentLen = Short.toUnsignedInt(buf.getShort(offset + 32));
            int nameStart = offset + 46;
            int nameEnd = nameStart + fileNameLen;
            if (nameEnd > centralDirectoryBytes.length) break;
            String fileName = new String(centralDirectoryBytes, nameStart, fileNameLen);
            CentralDirectoryInfo entryInfo = new CentralDirectoryInfo(
                    centralDirectoryBytes.length,
                    offset,
                    centralDirectoryBytes,
                    compressedSize,
                    uncompressedSize,
                    fileNameLength,
                    extraFieldLength,
                    fileCommentLength,
                    localHeaderOffset,
                    fileName,
                    extraLen,
                    commentLen);
            entries.add(entryInfo);
            log.info("Entry: {}", entryInfo);
            offset = nameEnd + extraLen + commentLen;
        }
        log.info("-----------------------------------------");
        setCentralDirectoryInfos(entries);
        return entries;
    }

    private void setCentralDirectoryInfos(List<CentralDirectoryInfo> entries) {
        this.centralDirectoryInfos.addAll(entries);
    }

    private byte[] fetchFileBytesFromZip(CentralDirectoryInfo centralDirectoryInfo) throws ZipHttpException {
        long localHeaderOffset = centralDirectoryInfo.getLocalHeaderOffset();
        long compressedSize = centralDirectoryInfo.getCompressedSize();
        log.info(
                "Fetching file bytes for: {} (localHeaderOffset={}, compressedSize={})",
                centralDirectoryInfo.getFileName(),
                localHeaderOffset,
                compressedSize);

        if (compressedSize <= 0) {
            log.warn("Compressed size is zero or negative for file: {}", centralDirectoryInfo.getFileName());
            return new byte[0];
        }

        int fileNameLen = (int) centralDirectoryInfo.getFileNameLength();
        int extraLen = centralDirectoryInfo.getExtraLen();
        int headerSize = ZipExtractionConfig.MIN_LOCAL_FILE_HEADER_SIZE + fileNameLen + extraLen;

        long totalSize = headerSize + compressedSize;
        log.info(
                "Fetching {} bytes (header: {}, compressed: {}) with safety buffer for file: {}",
                totalSize,
                headerSize,
                compressedSize,
                centralDirectoryInfo.getFileName());

        return zipHttpClient.fetchBytesWithSafetyBuffer(
                zipUrl, localHeaderOffset, totalSize, ZipExtractionConfig.FILE_FETCH_SAFETY_BUFFER);
    }

    private byte[] decompress(byte[] bytes, int compressedSize) throws IOException {
        int localFileNameLen = Short.toUnsignedInt(
                ByteBuffer.wrap(bytes, 26, 2).order(ByteOrder.LITTLE_ENDIAN).getShort());
        int localExtraLen = Short.toUnsignedInt(
                ByteBuffer.wrap(bytes, 28, 2).order(ByteOrder.LITTLE_ENDIAN).getShort());
        int dataStart = ZipExtractionConfig.MIN_LOCAL_FILE_HEADER_SIZE + localFileNameLen + localExtraLen;

        ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes, dataStart, compressedSize);

        try (InflaterInputStream decompressorStream = new InflaterInputStream(byteStream, new Inflater(true))) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[compressedSize];
            int bytesRead;
            while ((bytesRead = decompressorStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }

            return byteArrayOutputStream.toByteArray();
        }
    }

    public List<byte[]> getAllFileContents() {
        List<byte[]> result = new ArrayList<>();

        centralDirectoryInfos.forEach(centralDirectoryInfo -> {
            result.add(getFileFromFileName(centralDirectoryInfo.getFileName()));
        });

        log.info("Processed {} files, returned {} file byte lists", centralDirectoryInfos.size(), result.size());
        return result;
    }

    public byte[] getFileFromFileName(String filename) {
        CentralDirectoryInfo centralDirectoryInfo = getCentralDirectoryInfoFromFileName(filename);
        try {
            byte[] allBytes = fetchFileBytesFromZip(centralDirectoryInfo);
            byte[] decoded = decompress(allBytes, (int) centralDirectoryInfo.getCompressedSize());
            log.info("Successfully extracted file: {} ({} bytes)", filename, decoded.length);
            return decoded;
        } catch (ZipHttpException e) {
            log.error("HTTP error extracting file {}: {}", filename, e.getMessage(), e);
            return new byte[0];
        } catch (Exception e) {
            log.error("Error extracting file {}: {}", filename, e.getMessage(), e);
            return new byte[0];
        }
    }

    public void streamFrom(String filename, StreamProcessorInterface processor)
            throws IOException, InterruptedException {
        InputStream inputStream = createInputStreamFromFile(filename);
        try {
            readFileFromStream(inputStream, processor);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void readFileFromStream(InputStream inputStream, StreamProcessorInterface processor) throws IOException {
        ZipInputStream zipInputStream = new ZipInputStream(inputStream);
        ZipEntry entry;

        while ((entry = zipInputStream.getNextEntry()) != null) {
            try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = zipInputStream.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                }
                // @Refactor: Can change the entry to return the CentralDirectoryInfo if needed
                processor.processStream(byteArrayOutputStream.toByteArray(), entry.getName());
            } catch (IOException e) {
                log.error("Error reading from ZIP stream: {}", e.getMessage(), e);
            }
        }
    }

    private InputStream createInputStreamFromFile(String filename) throws IOException, InterruptedException {
        return zipHttpClient.fetchStreamFromOffset(
                zipUrl, getCentralDirectoryInfoFromFileName(filename).getLocalHeaderOffset());
    }

    private CentralDirectoryInfo getCentralDirectoryInfoFromFileName(String filename) {
        return centralDirectoryInfos.stream()
                .filter(x -> x.getFileName().equals(filename))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("File " + filename + " not found in central directory"));
    }
}
