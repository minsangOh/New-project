package com.example.pstarchive.encoding;

import java.util.Set;

public final class KoreanTextQualityScorer {
    private static final Set<Character> COMMON_KOREAN_MOJIBAKE = Set.of(
            '좎', '룞', '뜝', '숈', '삕', '뙎', '뙣', '옄', '븳', '꽌', '쓣', '뿉', '엯', '덈', '떎');

    private KoreanTextQualityScorer() {
    }

    public static double brokenCharRatio(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }
        int suspicious = 0;
        int visible = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            visible++;
            if (ch == '\uFFFD' || ch == '?' || ch == '�' || ch == '占' || COMMON_KOREAN_MOJIBAKE.contains(ch)) {
                suspicious++;
            }
        }
        if (visible == 0) {
            return 0.0;
        }
        return suspicious / (double) visible;
    }

    public static double hangulRatio(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }
        int hangul = 0;
        int visible = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch) || Character.isDigit(ch) || isAsciiPunctuation(ch)) {
                continue;
            }
            visible++;
            if (ch >= '\uAC00' && ch <= '\uD7A3') {
                hangul++;
            }
        }
        if (visible == 0) {
            return 0.0;
        }
        return hangul / (double) visible;
    }

    private static boolean isAsciiPunctuation(char ch) {
        return ch < 128 && !Character.isLetterOrDigit(ch);
    }
}
