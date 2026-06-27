package com.example.pstarchive.search;

public record FieldMatch(
        String field,
        String originalQuery,
        String matchedText,
        int offset,
        int length,
        int lineNumber,
        int paragraphNumber,
        String contextBefore,
        String contextAfter,
        String context,
        MatchPolicy matchPolicy
) {
}
