package com.example.pstarchive.search;

import java.util.List;

public record SearchResponse(
        NormalizedQuery query,
        int limit,
        int contextChars,
        long sqlCandidates,
        List<VerifiedMessage> verifiedMessages,
        long totalMatches
) {
}
