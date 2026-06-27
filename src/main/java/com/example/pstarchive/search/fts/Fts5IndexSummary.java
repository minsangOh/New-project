package com.example.pstarchive.search.fts;

public record Fts5IndexSummary(
        long messagesRead,
        long rowsIndexed,
        long rowErrors,
        long elapsedMs,
        String status
) {
}
