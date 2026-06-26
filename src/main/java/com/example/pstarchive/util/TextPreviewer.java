package com.example.pstarchive.util;

public final class TextPreviewer {
    public static final int DEFAULT_LIMIT = 500;

    private TextPreviewer() {
    }

    public static String preview(String text) {
        return preview(text, DEFAULT_LIMIT);
    }

    public static String preview(String text, int limit) {
        if (text == null) {
            return "<null>";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.isEmpty()) {
            return "<empty>";
        }
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be zero or greater");
        }
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit) + "...";
    }
}
