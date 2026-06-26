package com.example.pstarchive.encoding;

import java.util.Set;

public final class KoreanTextQualityScorer {
    private static final Set<Integer> COMMON_KOREAN_MOJIBAKE_CODE_POINTS = Set.of(
            0x5360, // U+5360 replacement-like CJK pattern
            0xC1F1,
            0xAF66,
            0xAFA9,
            0xC604,
            0xC88E,
            0xB8DE,
            0xC1FF,
            0xB71D,
            0xC208,
            0xC095,
            0xB64E,
            0xB663,
            0xB5D7,
            0xC6A9,
            0xC0BB,
            0x8AED,
            0x6028
    );

    private KoreanTextQualityScorer() {
    }

    public static double brokenCharRatio(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }
        int suspicious = 0;
        int visible = 0;
        int questionMarks = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            if (!Character.isWhitespace(codePoint)) {
                visible++;
                if (codePoint == '?') {
                    questionMarks++;
                }
                if (isBrokenReplacement(codePoint)
                        || isSuspiciousCodePoint(codePoint)
                        || isSuspiciousQuestionMark(text, i)) {
                    suspicious++;
                }
            }
            i += charCount;
        }
        if (visible == 0) {
            return 0.0;
        }
        if (questionMarks >= 2 && questionMarks / (double) visible >= 0.50) {
            suspicious = Math.max(suspicious, questionMarks);
        }
        return suspicious / (double) visible;
    }

    public static double hangulRatio(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }
        int hangul = 0;
        int visible = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            if (!Character.isWhitespace(codePoint)
                    && !Character.isDigit(codePoint)
                    && !isAsciiPunctuation(codePoint)) {
                visible++;
                if (isHangulSyllable(codePoint) && !isSuspiciousCodePoint(codePoint)) {
                    hangul++;
                }
            }
            i += charCount;
        }
        if (visible == 0) {
            return 0.0;
        }
        return hangul / (double) visible;
    }

    public static boolean looksBroken(String text) {
        return brokenCharRatio(text) > 0.08;
    }

    private static boolean isBrokenReplacement(int codePoint) {
        return codePoint == 0xFFFD;
    }

    private static boolean isSuspiciousCodePoint(int codePoint) {
        return COMMON_KOREAN_MOJIBAKE_CODE_POINTS.contains(codePoint);
    }

    private static boolean isSuspiciousQuestionMark(String text, int index) {
        if (text.charAt(index) != '?') {
            return false;
        }
        int previous = previousCodePoint(text, index);
        int next = nextCodePoint(text, index + 1);
        if (isSuspiciousCodePoint(previous) || isSuspiciousCodePoint(next)) {
            return true;
        }
        if (next != -1 && isHangulSyllable(next) && (previous == -1 || isHangulSyllable(previous))) {
            return true;
        }
        return previous != -1 && next != -1 && isHangulSyllable(previous) && isHangulSyllable(next);
    }

    private static int previousCodePoint(String text, int index) {
        if (index <= 0) {
            return -1;
        }
        return text.codePointBefore(index);
    }

    private static int nextCodePoint(String text, int index) {
        if (index >= text.length()) {
            return -1;
        }
        return text.codePointAt(index);
    }

    private static boolean isHangulSyllable(int codePoint) {
        return codePoint >= 0xAC00 && codePoint <= 0xD7A3;
    }

    private static boolean isAsciiPunctuation(int codePoint) {
        return codePoint < 128 && !Character.isLetterOrDigit(codePoint);
    }
}
