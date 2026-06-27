package com.example.pstarchive.search;

import java.util.List;

public record SearchResponse(
        String engine,
        NormalizedQuery query,
        int limit,
        int contextChars,
        long sqlCandidates,
        List<VerifiedMessage> verifiedMessages,
        long totalMatches
) {
}
