package com.example.pstarchive.textquality;

public record SanitizedText(
        String text,
        int nulRemoved
) {
}
