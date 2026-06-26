package com.example.pstarchive.inspect;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record StoreInspectionResult(
        Path storePath,
        long sizeBytes,
        long foldersCount,
        long messagesCount,
        long indexRunsCount,
        long indexErrorsCount,
        Optional<LatestIndexRun> latestRun,
        List<StatusCount> parseStatusCounts,
        List<StatusCount> subjectStatusCounts,
        List<StatusCount> bodyTextStatusCounts,
        List<StatusCount> bodyHtmlStatusCounts,
        List<StatusCount> bodyHtmlTextStatusCounts,
        long subjectNullCount,
        long bodyTextNullCount,
        long bodyHtmlNullCount,
        long bodyHtmlTextNullCount,
        double averageBodyTextLength,
        double averageBodyHtmlTextLength,
        String minSentAt,
        String maxSentAt,
        String minReceivedAt,
        String maxReceivedAt
) {
    public long bodySearchableCount() {
        return messagesCount - Math.min(messagesCount, bodyTextNullCount + bodyHtmlTextNullCount);
    }
}
