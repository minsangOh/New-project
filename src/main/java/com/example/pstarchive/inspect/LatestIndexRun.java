package com.example.pstarchive.inspect;

public record LatestIndexRun(
        long id,
        String pstPath,
        Long startedAt,
        Long finishedAt,
        Integer limitCount,
        boolean replaceMode,
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
        String status
) {
}
