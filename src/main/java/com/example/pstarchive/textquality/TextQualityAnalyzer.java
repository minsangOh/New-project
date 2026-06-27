package com.example.pstarchive.textquality;

import com.example.pstarchive.encoding.KoreanTextQualityScorer;
import com.example.pstarchive.util.TextPreviewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TextQualityAnalyzer {
    private static final int PREVIEW_LIMIT = 160;

    public TextQualityResult diagnose(String text) {
        SanitizedText sanitized = StoredTextSanitizer.sanitizeWithStats(text);
        String value = sanitized.text();
        if (value == null || value.isEmpty()) {
            return new TextQualityResult(TextQualityLevel.NULL, "null_or_empty", 0,
                    sanitized.nulRemoved(), 0, 0.0, 0, 0,
                    warnings(sanitized.nulRemoved(), 0.0, 0, 0), "<null>");
        }

        int visible = visibleChars(value);
        int questionMarks = countChar(value, '?');
        double questionRatio = visible == 0 ? 0.0 : questionMarks / (double) visible;
        int repeatedQuestionRuns = repeatedQuestionRuns(value);
        int mojibakePatterns = mojibakePatternCount(value);
        double brokenRatio = KoreanTextQualityScorer.brokenCharRatio(value);
        List<String> warnings = warnings(sanitized.nulRemoved(), questionRatio, repeatedQuestionRuns, mojibakePatterns);

        TextQualityLevel level = TextQualityLevel.OK;
        String reason = "ok";
        if (mojibakePatterns >= 4 || brokenRatio >= 0.35 || (questionMarks >= 8 && questionRatio >= 0.30)) {
            level = TextQualityLevel.BROKEN;
            reason = "high_mojibake_or_question_ratio";
        } else if (mojibakePatterns >= 2 || repeatedQuestionRuns >= 2 || brokenRatio >= 0.12 || (questionMarks >= 5 && questionRatio >= 0.12)) {
            level = TextQualityLevel.DEGRADED;
            reason = "degraded_text_signals";
        } else if (mojibakePatterns >= 1 || repeatedQuestionRuns >= 1 || brokenRatio >= 0.05 || (questionMarks >= 3 && questionRatio >= 0.08)) {
            level = TextQualityLevel.SUSPECT;
            reason = "suspect_text_signals";
        }

        return new TextQualityResult(level, reason, visible, sanitized.nulRemoved(), questionMarks, questionRatio,
                repeatedQuestionRuns, mojibakePatterns, warnings, TextPreviewer.preview(value, PREVIEW_LIMIT));
    }

    public TextQualityLevel downgradeStatusLevel(String storedStatus, String text) {
        TextQualityResult result = diagnose(text);
        if (result.level() == TextQualityLevel.BROKEN || result.level() == TextQualityLevel.DEGRADED) {
            return TextQualityLevel.DEGRADED;
        }
        if (storedStatus == null || storedStatus.isBlank()) {
            return TextQualityLevel.NULL;
        }
        try {
            return TextQualityLevel.valueOf(storedStatus.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return result.level();
        }
    }

    private List<String> warnings(int nulCount, double questionRatio, int repeatedQuestionRuns, int mojibakePatterns) {
        List<String> warnings = new ArrayList<>();
        if (nulCount > 0) {
            warnings.add("nul_chars_removed_or_present");
        }
        if (questionRatio >= 0.12) {
            warnings.add("high_question_mark_ratio");
        }
        if (repeatedQuestionRuns > 0) {
            warnings.add("repeated_question_marks");
        }
        if (mojibakePatterns > 0) {
            warnings.add("mojibake_pattern");
        }
        return warnings;
    }

    private int visibleChars(String text) {
        int visible = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (!Character.isWhitespace(codePoint)) {
                visible++;
            }
            i += Character.charCount(codePoint);
        }
        return visible;
    }

    private int countChar(String text, char target) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == target) {
                count++;
            }
        }
        return count;
    }

    private int repeatedQuestionRuns(String text) {
        int runs = 0;
        int current = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '?') {
                current++;
            } else {
                if (current >= 3) {
                    runs++;
                }
                current = 0;
            }
        }
        return current >= 3 ? runs + 1 : runs;
    }

    private int mojibakePatternCount(String text) {
        int count = 0;
        count += occurrences(text, "\uFFFD");
        count += occurrences(text, "\u5360");
        count += occurrences(text, "\uC88E");
        count += occurrences(text, "\uB8DE");
        count += occurrences(text, "\uC1FF");
        count += occurrences(text, "\uB71D");
        count += occurrences(text, "\uC208");
        count += occurrences(text, "\uC095");
        count += occurrences(text, "\uC1A1");
        count += occurrences(text, "\uC608");
        count += occurrences(text, "\uC724");
        count += occurrences(text, "\uCC37");
        count += occurrences(text, "\u82D1");
        return count;
    }

    private int occurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) >= 0) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}
