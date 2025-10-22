package com.http.request.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CentralDirectoryInfo {
    private long size;
    private long offset;
    private byte[] lastMBBytes;
    private long compressedSize;
    private long uncompressedSize;
    private long fileNameLength;
    private long extraFieldLength;
    private long fileCommentLength;
    private long localHeaderOffset;
    private String fileName;
    private int extraLen;
    private int commentLen;
}
