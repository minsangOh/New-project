package com.example.pstarchive.search;

import java.util.List;

public record VerifiedMessage(
        SearchCandidate candidate,
        List<FieldMatch> matches
) {
}
