package com.example.pstarchive.search;

public class RiskyQueryDetector {
    public boolean isRisky(NormalizedQuery query) {
        if (query == null || query.comparable() == null || query.comparable().isBlank()) {
            return false;
        }
        String value = query.comparable();
        return hasRiskyPunctuation(value)
                || hasAsciiLetterAndDigit(value)
                || isShortHangulQuery(value);
    }

    private boolean hasRiskyPunctuation(String value) {
        return value.indexOf('-') >= 0
                || value.indexOf('#') >= 0
                || value.indexOf('/') >= 0
                || value.indexOf('_') >= 0;
    }

    private boolean hasAsciiLetterAndDigit(String value) {
        boolean asciiLetter = false;
        boolean digit = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= 'a' && ch <= 'z') {
                asciiLetter = true;
            } else if (ch >= '0' && ch <= '9') {
                digit = true;
            }
            if (asciiLetter && digit) {
                return true;
            }
        }
        return false;
    }

    private boolean isShortHangulQuery(String value) {
        int hangul = 0;
        int visible = 0;
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            if (!Character.isWhitespace(codePoint)) {
                visible++;
                if (isHangul(codePoint)) {
                    hangul++;
                } else {
                    return false;
                }
            }
            i += Character.charCount(codePoint);
        }
        return visible >= 2 && visible <= 4 && hangul == visible;
    }

    private boolean isHangul(int codePoint) {
        return codePoint >= 0xAC00 && codePoint <= 0xD7A3;
    }
}