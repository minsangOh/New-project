package com.example.pstarchive.pst;

public record PstScanSummary(
        long foldersVisited,
        long messagesScanned,
        long fieldErrors,
        long messageErrors,
        long fatalErrors,
        String parserVersion,
        String message
) {
}
