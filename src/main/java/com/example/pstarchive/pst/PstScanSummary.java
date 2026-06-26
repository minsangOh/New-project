package com.example.pstarchive.pst;

public record PstScanSummary(
        long folderCount,
        long messageCount,
        long failedCount,
        String parserVersion,
        String message
) {
}
