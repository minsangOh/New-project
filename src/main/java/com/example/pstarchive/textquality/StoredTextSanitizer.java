package com.example.pstarchive.textquality;

public final class StoredTextSanitizer {
    private StoredTextSanitizer() {
    }

    public static String sanitize(String text) {
        return sanitizeWithStats(text).text();
    }

    public static SanitizedText sanitizeWithStats(String text) {
        if (text == null) {
            return new SanitizedText(null, 0);
        }
        int nulCount = 0;
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\u0000') {
                nulCount++;
            } else {
                builder.append(ch);
            }
        }
        return new SanitizedText(builder.toString(), nulCount);
    }
}
