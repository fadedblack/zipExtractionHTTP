package com.http.request.utility;

import com.http.request.config.ZipExtractionConfig;
import com.http.request.exceptions.ZipExtractionException;
import com.http.request.exceptions.ZipHttpException;
import com.http.request.http.ZipHttpClient;
import com.http.request.models.CentralDirectoryInfo;
import com.http.request.processors.StreamProcessorInterface;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
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
            log.error("HTTP error processing ZIP from URL: " + e.getMessage(), e);
            return List.of();
        } catch (IOException e) {
            log.error("ZIP processing error from URL: " + e.getMessage(), e);
            return List.of();
        }
    }

    private int findEOCDOffset(byte[] lastMBBytes) throws IOException {
        for (int pos = lastMBBytes.length - 4; pos >= 0; pos--) {
            int sig = ByteBuffer.wrap(lastMBBytes, pos, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getInt();
            if (sig == ZipExtractionConfig.EOCD_SIGNATURE) {
                log.info("Found EOCD at position: " + pos + " within last MB range");
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

    private byte[] decompressFileBytes(byte[] allBytes, long compressedSize) throws IOException {
        int sig = ByteBuffer.wrap(allBytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (sig != ZipExtractionConfig.LOCAL_FILE_HEADER_SIGNATURE) {
            throw new ZipException("Local file header signature not found");
        }

        int generalPurposeBitFlag = Short.toUnsignedInt(
                ByteBuffer.wrap(allBytes, 6, 2).order(ByteOrder.LITTLE_ENDIAN).getShort());
        boolean hasDataDescriptor = (generalPurposeBitFlag & ZipExtractionConfig.DATA_DESCRIPTOR_FLAG) != 0;
        log.info(
                "General purpose bit flag: 0x{}, Has data descriptor: {}",
                Integer.toHexString(generalPurposeBitFlag),
                hasDataDescriptor);

        long localCompressedSize = Integer.toUnsignedLong(
                ByteBuffer.wrap(allBytes, 18, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
        log.info(
                "Compressed size from central directory: {}, from local header: {}",
                compressedSize,
                localCompressedSize);

        int localFileNameLen = Short.toUnsignedInt(
                ByteBuffer.wrap(allBytes, 26, 2).order(ByteOrder.LITTLE_ENDIAN).getShort());
        int localExtraLen = Short.toUnsignedInt(
                ByteBuffer.wrap(allBytes, 28, 2).order(ByteOrder.LITTLE_ENDIAN).getShort());
        int dataStart = ZipExtractionConfig.MIN_LOCAL_FILE_HEADER_SIZE + localFileNameLen + localExtraLen;

        int copyLength = (int) (hasDataDescriptor && localCompressedSize == 0 ? compressedSize : localCompressedSize);

        log.info(
                "decompressFileBytes: allBytes.length={}, localFileNameLen={}, localExtraLen={}, dataStart={}, compressedSize={}, dataStart+compressedSize={}",
                allBytes.length,
                localFileNameLen,
                localExtraLen,
                dataStart,
                compressedSize,
                dataStart + copyLength);

        byte[] compressedData = new byte[copyLength];
        System.arraycopy(allBytes, dataStart, compressedData, 0, copyLength);

        int compressionMethod = Short.toUnsignedInt(
                ByteBuffer.wrap(allBytes, 8, 2).order(ByteOrder.LITTLE_ENDIAN).getShort());
        log.info("Compression method: {}", compressionMethod);

        if (compressionMethod == ZipExtractionConfig.COMPRESSION_METHOD_STORED) {
            log.info("File is stored without compression, returning raw data");
            return compressedData;
        } else if (compressionMethod == ZipExtractionConfig.COMPRESSION_METHOD_DEFLATE) {
            log.info("Attempting to decompress {} bytes using deflate", compressedData.length);
            try {
                Inflater inflater = new Inflater(true);
                inflater.setInput(compressedData);
                byte[] buffer = new byte[ZipExtractionConfig.DECOMPRESSION_BUFFER_SIZE];
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                while (!inflater.finished()) {
                    int count = inflater.inflate(buffer);
                    if (count == 0 && inflater.needsInput()) {
                        break;
                    }
                    baos.write(buffer, 0, count);
                }
                inflater.end();
                byte[] decompressed = baos.toByteArray();
                log.info("Successfully decompressed {} bytes to {} bytes", compressedData.length, decompressed.length);
                return decompressed;
            } catch (DataFormatException e) {
                log.error(
                        "Failed to decompress data: {}. This might indicate corrupted or incomplete compressed data.",
                        e.getMessage());
                log.error(
                        "Compressed data length: {}, Expected compressed size: {}", compressedData.length, copyLength);
                throw new ZipException("Failed to decompress file data: " + e.getMessage());
            }
        } else {
            throw new ZipException("Unsupported compression method: " + compressionMethod);
        }
    }

    public List<List<Byte>> getAllFiles(List<CentralDirectoryInfo> centralDirectoryContents) {
        List<List<Byte>> result = new ArrayList<>();

        for (CentralDirectoryInfo centralDirectoryInfo : centralDirectoryContents) {
            try {
                byte[] allBytes = fetchFileBytesFromZip(centralDirectoryInfo);
                byte[] decoded = decompressFileBytes(allBytes, centralDirectoryInfo.getCompressedSize());

                List<Byte> fileBytes = new ArrayList<>(decoded.length);
                for (byte b : decoded) {
                    fileBytes.add(b);
                }

                result.add(fileBytes);
                log.info(
                        "Successfully processed file: {} ({} bytes)",
                        centralDirectoryInfo.getFileName(),
                        decoded.length);

            } catch (ZipHttpException e) {
                log.error("HTTP error extracting file {}: {}", centralDirectoryInfo.getFileName(), e.getMessage(), e);
                result.add(Collections.emptyList());
            } catch (Exception e) {
                log.error("Error extracting file {}: {}", centralDirectoryInfo.getFileName(), e.getMessage(), e);
                result.add(Collections.emptyList());
            }
        }

        log.info("Processed {} files, returned {} file byte lists", centralDirectoryContents.size(), result.size());
        return result;
    }

    public List<Byte> getFile(String filename) {
        CentralDirectoryInfo centralDirectoryInfo = getCentralDirectoryInfoFromFileName(filename);

        try {
            byte[] allBytes = fetchFileBytesFromZip(centralDirectoryInfo);
            byte[] decoded = decompressFileBytes(allBytes, centralDirectoryInfo.getCompressedSize());
            List<Byte> result = new ArrayList<>(decoded.length);
            for (byte b : decoded) {
                result.add(b);
            }
            log.info("Successfully extracted file: {} ({} bytes)", filename, decoded.length);
            return result;
        } catch (ZipHttpException e) {
            log.error("HTTP error extracting file {}: {}", filename, e.getMessage(), e);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error extracting file {}: {}", filename, e.getMessage(), e);
            return Collections.emptyList();
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
