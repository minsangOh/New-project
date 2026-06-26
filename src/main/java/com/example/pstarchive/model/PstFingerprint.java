package com.example.pstarchive.model;

public record PstFingerprint(
        long fileSize,
        long mtimeEpochMs,
        String first1MbSha256,
        String last1MbSha256,
        String fileFingerprint
) {
}
