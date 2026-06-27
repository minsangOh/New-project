package com.example.pstarchive.search;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SearchStoreService {
    private final SearchQueryNormalizer normalizer;
    private final CandidateSearchEngine candidateSearchEngine;
    private final RawFieldVerifier verifier;

    public SearchStoreService() {
        this(new SearchQueryNormalizer(), new LikeCandidateSearcher(), new RawFieldVerifier());
    }

    public SearchStoreService(SearchQueryNormalizer normalizer, CandidateSearchEngine candidateSearchEngine, RawFieldVerifier verifier) {
        this.normalizer = normalizer;
        this.candidateSearchEngine = candidateSearchEngine;
        this.verifier = verifier;
    }

    public SearchResponse search(Path storePath, String queryText, int limit, int contextChars,
                                 int maxMatchesPerMessage, List<SearchField> fields) throws Exception {
        Path normalizedStore = storePath.toAbsolutePath().normalize();
        if (!Files.exists(normalizedStore)) {
            throw new IllegalArgumentException("SQLite store does not exist: " + normalizedStore);
        }
        NormalizedQuery query = normalizer.normalize(queryText);
        int safeLimit = Math.max(1, limit);
        int safeContext = Math.max(0, contextChars);
        int safeMaxMatches = Math.max(1, maxMatchesPerMessage);
        List<SearchCandidate> candidates = candidateSearchEngine.search(normalizedStore, query, fields, safeLimit);
        List<VerifiedMessage> verified = new ArrayList<>();
        long totalMatches = 0;
        for (SearchCandidate candidate : candidates) {
            if (verified.size() >= safeLimit) {
                break;
            }
            VerifiedMessage message = verifier.verify(candidate, query, fields, safeContext, safeMaxMatches);
            if (message != null) {
                verified.add(message);
                totalMatches += message.matches().size();
            }
        }
        return new SearchResponse(query, safeLimit, safeContext, candidates.size(), verified, totalMatches);
    }
}
