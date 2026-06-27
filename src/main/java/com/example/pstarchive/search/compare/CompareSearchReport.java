package com.example.pstarchive.search.compare;

import java.nio.file.Path;
import java.util.List;

public record CompareSearchReport(
        Path storePath,
        String query,
        String field,
        int limit,
        boolean includeBroken,
        long likeCandidates,
        long fts5Candidates,
        long likeVerifiedMessages,
        long fts5VerifiedMessages,
        long commonVerifiedMessages,
        long likeDisplayedMessages,
        long fts5DisplayedMessages,
        long commonDisplayedMessages,
        long likeOnlyDisplayedMessages,
        long fts5OnlyDisplayedMessages,
        long likeOnlyHiddenOnlyMessages,
        long fts5OnlyHiddenOnlyMessages,
        List<ComparedMessage> likeOnlyMessages,
        List<ComparedMessage> fts5OnlyMessages,
        String fts5Error
) {
    public long likeOnlyVerifiedMessages() {
        return likeOnlyMessages.size();
    }

    public long fts5OnlyVerifiedMessages() {
        return fts5OnlyMessages.size();
    }

    public boolean hasFts5Error() {
        return fts5Error != null && !fts5Error.isBlank();
    }
}
