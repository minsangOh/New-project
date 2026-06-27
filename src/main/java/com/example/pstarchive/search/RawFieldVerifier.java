package com.example.pstarchive.search;

import com.example.pstarchive.textquality.TextQualityAnalyzer;
import com.example.pstarchive.textquality.TextQualityLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RawFieldVerifier {
    private final SearchQueryNormalizer normalizer;
    private final MatchLocator locator;
    private final TextQualityAnalyzer qualityAnalyzer;

    public RawFieldVerifier() {
        this(new SearchQueryNormalizer(), new MatchLocator(), new TextQualityAnalyzer());
    }

    public RawFieldVerifier(SearchQueryNormalizer normalizer, MatchLocator locator) {
        this(normalizer, locator, new TextQualityAnalyzer());
    }

    public RawFieldVerifier(SearchQueryNormalizer normalizer, MatchLocator locator, TextQualityAnalyzer qualityAnalyzer) {
        this.normalizer = normalizer;
        this.locator = locator;
        this.qualityAnalyzer = qualityAnalyzer;
    }

    public VerifiedMessage verify(SearchCandidate candidate, NormalizedQuery query, List<SearchField> fields,
                                  int contextChars, int maxMatchesPerMessage) {
        List<FieldMatch> matches = new ArrayList<>();
        int safeMaxMatches = Math.max(1, maxMatchesPerMessage);
        for (SearchField field : fields) {
            String value = candidate.fieldValue(field);
            if (value == null || value.isEmpty()) {
                continue;
            }
            matches.addAll(locator.locate(field.displayName(), value, query, contextChars, safeMaxMatches, normalizer));
        }
        if (matches.isEmpty()) {
            return null;
        }
        List<FieldMatch> sorted = matches.stream()
                .sorted(matchComparator(candidate))
                .limit(safeMaxMatches)
                .toList();
        return new VerifiedMessage(candidate, sorted);
    }

    private Comparator<FieldMatch> matchComparator(SearchCandidate candidate) {
        return Comparator
                .comparingInt((FieldMatch match) -> fieldPriority(match.field()))
                .thenComparingInt(match -> qualityPriority(candidate, match.field()))
                .thenComparingInt(FieldMatch::offset);
    }

    private int fieldPriority(String fieldName) {
        SearchField field = fieldFor(fieldName);
        if (field == null) {
            return 99;
        }
        return switch (field) {
            case SUBJECT -> 0;
            case SENDER_NAME, SENDER_EMAIL -> 1;
            case RECIPIENTS, CC -> 2;
            case FOLDER_PATH -> 3;
            case BODY_TEXT -> 4;
            case BODY_HTML_TEXT -> 5;
        };
    }

    private int qualityPriority(SearchCandidate candidate, String fieldName) {
        SearchField field = fieldFor(fieldName);
        TextQualityLevel level = qualityAnalyzer.diagnose(field == null ? null : candidate.fieldValue(field)).level();
        return switch (level) {
            case OK -> 0;
            case SUSPECT -> 1;
            case DEGRADED -> 2;
            case BROKEN -> 3;
            case NULL -> 4;
        };
    }

    private SearchField fieldFor(String fieldName) {
        for (SearchField field : SearchField.values()) {
            if (field.columnName().equalsIgnoreCase(fieldName) || field.displayName().equalsIgnoreCase(fieldName)) {
                return field;
            }
        }
        return null;
    }
}
