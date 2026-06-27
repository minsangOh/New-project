package com.example.pstarchive.textquality;

public record TextFieldDiagnostic(
        long messageId,
        String field,
        String storedStatus,
        TextQualityResult quality,
        boolean statusMismatch,
        String value
) {
}
