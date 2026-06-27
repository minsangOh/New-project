package com.example.pstarchive.search;

import java.util.ArrayList;
import java.util.List;

public class RawFieldVerifier {
    private final SearchQueryNormalizer normalizer;
    private final MatchLocator locator;

    public RawFieldVerifier() {
        this(new SearchQueryNormalizer(), new MatchLocator());
    }

    public RawFieldVerifier(SearchQueryNormalizer normalizer, MatchLocator locator) {
        this.normalizer = normalizer;
        this.locator = locator;
    }

    public VerifiedMessage verify(SearchCandidate candidate, NormalizedQuery query, List<SearchField> fields,
                                  int contextChars, int maxMatchesPerMessage) {
        List<FieldMatch> matches = new ArrayList<>();
        for (SearchField field : fields) {
            if (matches.size() >= maxMatchesPerMessage) {
                break;
            }
            String value = candidate.fieldValue(field);
            if (value == null || value.isEmpty()) {
                continue;
            }
            int remaining = maxMatchesPerMessage - matches.size();
            matches.addAll(locator.locate(field.displayName(), value, query, contextChars, remaining, normalizer));
        }
        if (matches.isEmpty()) {
            return null;
        }
        return new VerifiedMessage(candidate, matches);
    }
}
