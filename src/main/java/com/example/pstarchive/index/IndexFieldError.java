package com.example.pstarchive.index;

public record IndexFieldError(
        String folderPath,
        Long descriptorNodeId,
        String stage,
        String fieldName,
        String errorType,
        String errorMessage
) {
}
