package com.example.pstarchive.index;

public record PstIndexSummary(
        long foldersVisited,
        long messagesSeen,
        long messagesSaved,
        long messageErrors,
        long fieldErrors,
        long fatalErrors,
        long okFields,
        long degradedFields,
        long unrecoverableFields,
        long nullFields,
        long errorFields,
        long elapsedMs,
        String status
) {
}
