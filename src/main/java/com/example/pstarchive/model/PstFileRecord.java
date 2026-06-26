package com.example.pstarchive.model;

public record PstFileRecord(
        String pstId,
        String displayName,
        String currentPath,
        String originalPath,
        PstStatus status,
        String periodFrom,
        String periodTo,
        String splitStrategy,
        long sizeBytes,
        long mtimeEpochMs,
        String first1MbSha256,
        String last1MbSha256,
        String fileFingerprint,
        String parserVersion,
        String indexVersion,
        String createdAt,
        String updatedAt,
        String lastScanAt,
        String lastIndexAt,
        String lastVerifiedAt
) {
}
