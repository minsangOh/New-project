package com.example.pstarchive.encoding;

public record ExtractedText(
        String text,
        TextRecoveryStatus status,
        String source,
        String errorType,
        String errorMessage
) {
    public int length() {
        return text == null ? 0 : text.length();
    }

    public boolean hasError() {
        return errorType != null;
    }

    public static ExtractedText nullValue() {
        return new ExtractedText(null, TextRecoveryStatus.NULL, "<none>", null, null);
    }

    public static ExtractedText error(String errorType, String errorMessage) {
        return new ExtractedText(null, TextRecoveryStatus.ERROR, "<error>", errorType, errorMessage);
    }
}
