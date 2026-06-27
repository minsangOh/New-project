package com.example.pstarchive.search;

import java.util.List;

public record NormalizedQuery(
        String original,
        String comparable,
        String escapedLike,
        List<String> escapedCandidateTerms
) {
}
