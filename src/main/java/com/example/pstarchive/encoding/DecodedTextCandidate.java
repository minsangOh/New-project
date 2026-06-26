package com.example.pstarchive.encoding;

public record DecodedTextCandidate(
        String label,
        String text,
        double brokenCharRatio,
        double hangulRatio,
        boolean rawBytesAvailable
) {
}
