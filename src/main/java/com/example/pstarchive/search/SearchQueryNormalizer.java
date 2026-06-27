package com.example.pstarchive.search;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchQueryNormalizer {
    private static final int MAX_QUERY_LENGTH = 500;

    public NormalizedQuery normalize(String query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        String original = query;
        String normalized = normalizeComparable(query);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("query is too long: " + normalized.length());
        }
        return new NormalizedQuery(original, normalized, escapeLike(normalized), candidateTerms(normalized));
    }

    public String normalizeComparable(String text) {
        if (text == null) {
            return "";
        }
        String value = text.replace("\r\n", "\n").replace('\r', '\n');
        value = Normalizer.normalize(value, Normalizer.Form.NFKC);
        value = value.toLowerCase(Locale.ROOT);
        value = value.replaceAll("\\s+", " ").trim();
        return value;
    }

    public String normalizeWhitespaceInsensitive(String text) {
        return normalizeComparable(text).replace(" ", "");
    }

    private List<String> candidateTerms(String normalized) {
        List<String> terms = new ArrayList<>();
        terms.add(escapeLike(normalized));
        for (String token : normalized.split(" ")) {
            if (token.length() >= 2) {
                String escaped = escapeLike(token);
                if (!terms.contains(escaped)) {
                    terms.add(escaped);
                }
            }
        }
        return terms;
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
