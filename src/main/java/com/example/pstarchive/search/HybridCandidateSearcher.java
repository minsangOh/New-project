package com.example.pstarchive.search;

import com.example.pstarchive.search.fts.Fts5CandidateSearcher;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HybridCandidateSearcher implements CandidateSearchEngine {
    private final CandidateSearchEngine fts5Searcher;
    private final CandidateSearchEngine likeSearcher;
    private final HybridBackfillPolicy backfillPolicy;
    private final RiskyQueryDetector riskyQueryDetector;

    public HybridCandidateSearcher() {
        this(HybridBackfillPolicy.AUTO);
    }

    public HybridCandidateSearcher(HybridBackfillPolicy backfillPolicy) {
        this(new Fts5CandidateSearcher(), new LikeCandidateSearcher(), backfillPolicy, new RiskyQueryDetector());
    }

    public HybridCandidateSearcher(CandidateSearchEngine fts5Searcher,
                                   CandidateSearchEngine likeSearcher,
                                   HybridBackfillPolicy backfillPolicy,
                                   RiskyQueryDetector riskyQueryDetector) {
        this.fts5Searcher = fts5Searcher;
        this.likeSearcher = likeSearcher;
        this.backfillPolicy = backfillPolicy == null ? HybridBackfillPolicy.AUTO : backfillPolicy;
        this.riskyQueryDetector = riskyQueryDetector == null ? new RiskyQueryDetector() : riskyQueryDetector;
    }

    @Override
    public List<SearchCandidate> search(Path storePath, NormalizedQuery query, List<SearchField> fields, int limit) throws Exception {
        List<SearchCandidate> fts5Candidates = fts5Searcher.search(storePath, query, fields, limit);
        boolean shouldBackfill = backfillPolicy.shouldBackfill(riskyQueryDetector.isRisky(query));
        if (!shouldBackfill) {
            return fts5Candidates;
        }

        Map<Long, SearchCandidate> merged = new LinkedHashMap<>();
        addAll(merged, fts5Candidates);
        addAll(merged, likeSearcher.search(storePath, query, fields, limit));
        return List.copyOf(new ArrayList<>(merged.values()));
    }

    @Override
    public String name() {
        return "hybrid";
    }

    private void addAll(Map<Long, SearchCandidate> merged, List<SearchCandidate> candidates) {
        for (SearchCandidate candidate : candidates) {
            merged.putIfAbsent(candidate.id(), candidate);
        }
    }
}