package com.example.pstarchive.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MatchLocator {
    public List<FieldMatch> locate(String field, String text, NormalizedQuery query, int contextChars,
                                   int maxMatches, SearchQueryNormalizer normalizer) {
        if (text == null || text.isEmpty() || maxMatches <= 0) {
            return List.of();
        }
        List<FieldMatch> exact = locateByNeedle(field, text, query.original(), query, contextChars, maxMatches, MatchPolicy.EXACT, false);
        if (!exact.isEmpty()) {
            return exact;
        }
        List<FieldMatch> ci = locateByNeedle(field, text, query.original(), query, contextChars, maxMatches, MatchPolicy.CASE_INSENSITIVE, true);
        if (!ci.isEmpty()) {
            return ci;
        }
        List<FieldMatch> normalized = locateNormalized(field, text, query, contextChars, maxMatches, normalizer, false);
        if (!normalized.isEmpty()) {
            return normalized;
        }
        return locateNormalized(field, text, query, contextChars, maxMatches, normalizer, true);
    }

    private List<FieldMatch> locateByNeedle(String field, String text, String needle, NormalizedQuery query,
                                            int contextChars, int maxMatches, MatchPolicy policy, boolean ignoreCase) {
        if (needle == null || needle.isEmpty()) {
            return List.of();
        }
        String haystack = ignoreCase ? text.toLowerCase(Locale.ROOT) : text;
        String comparableNeedle = ignoreCase ? needle.toLowerCase(Locale.ROOT) : needle;
        List<FieldMatch> matches = new ArrayList<>();
        int from = 0;
        while (matches.size() < maxMatches) {
            int offset = haystack.indexOf(comparableNeedle, from);
            if (offset < 0) {
                break;
            }
            matches.add(match(field, text, query.original(), offset, comparableNeedle.length(), contextChars, policy));
            from = offset + Math.max(1, comparableNeedle.length());
        }
        return matches;
    }

    private List<FieldMatch> locateNormalized(String field, String text, NormalizedQuery query, int contextChars,
                                              int maxMatches, SearchQueryNormalizer normalizer, boolean whitespaceInsensitive) {
        String normalizedText = whitespaceInsensitive
                ? normalizer.normalizeWhitespaceInsensitive(text)
                : normalizer.normalizeComparable(text);
        String normalizedQuery = whitespaceInsensitive
                ? normalizer.normalizeWhitespaceInsensitive(query.original())
                : query.comparable();
        if (normalizedQuery.isEmpty() || !normalizedText.contains(normalizedQuery)) {
            return List.of();
        }
        int approximateOffset = approximateOriginalOffset(text, normalizedQuery);
        if (approximateOffset < 0) {
            approximateOffset = 0;
        }
        MatchPolicy policy = whitespaceInsensitive ? MatchPolicy.WHITESPACE_INSENSITIVE : MatchPolicy.NORMALIZED;
        return List.of(match(field, text, query.original(), approximateOffset,
                Math.min(query.original().length(), Math.max(1, text.length() - approximateOffset)), contextChars, policy));
    }

    private int approximateOriginalOffset(String text, String normalizedQuery) {
        String lower = text.toLowerCase(Locale.ROOT);
        String firstToken = normalizedQuery.split(" ")[0];
        if (firstToken.isEmpty()) {
            return -1;
        }
        return lower.indexOf(firstToken);
    }

    private FieldMatch match(String field, String text, String originalQuery, int offset, int length,
                             int contextChars, MatchPolicy policy) {
        int safeOffset = Math.max(0, Math.min(offset, text.length()));
        int safeLength = Math.max(0, Math.min(length, text.length() - safeOffset));
        int beforeStart = Math.max(0, safeOffset - contextChars);
        int afterEnd = Math.min(text.length(), safeOffset + safeLength + contextChars);
        String before = text.substring(beforeStart, safeOffset);
        String matched = text.substring(safeOffset, safeOffset + safeLength);
        String after = text.substring(safeOffset + safeLength, afterEnd);
        String prefix = beforeStart > 0 ? "..." : "";
        String suffix = afterEnd < text.length() ? "..." : "";
        return new FieldMatch(
                field,
                originalQuery,
                matched,
                safeOffset,
                safeLength,
                lineNumber(text, safeOffset),
                paragraphNumber(text, safeOffset),
                before,
                after,
                prefix + before + "[" + matched + "]" + after + suffix,
                policy
        );
    }

    private int lineNumber(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private int paragraphNumber(String text, int offset) {
        int paragraph = 1;
        boolean previousBlank = false;
        int lineStart = 0;
        for (int i = 0; i <= offset && i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                String line = text.substring(lineStart, i).trim();
                boolean blank = line.isEmpty();
                if (blank && !previousBlank && i < offset) {
                    paragraph++;
                }
                previousBlank = blank;
                lineStart = i + 1;
            }
        }
        return paragraph;
    }
}
