package com.example.pstarchive.search.benchmark;

import com.example.pstarchive.search.FieldMatch;
import com.example.pstarchive.search.RawFieldVerifier;
import com.example.pstarchive.search.SearchCandidate;
import com.example.pstarchive.search.SearchEngineType;
import com.example.pstarchive.search.SearchField;
import com.example.pstarchive.search.SearchQueryNormalizer;
import com.example.pstarchive.search.SearchResponse;
import com.example.pstarchive.search.SearchStoreService;
import com.example.pstarchive.search.VerifiedMessage;
import com.example.pstarchive.textquality.TextQualityAnalyzer;
import com.example.pstarchive.textquality.TextQualityLevel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BenchmarkSearchService {
    private static final int BENCHMARK_CONTEXT_CHARS = 80;
    private static final int BENCHMARK_MAX_MATCHES_PER_MESSAGE = 5;

    private final TextQualityAnalyzer qualityAnalyzer = new TextQualityAnalyzer();

    public BenchmarkReport benchmark(Path storePath, String query, String fieldOption, int limit, int repeat,
                                     boolean includeBroken, List<SearchEngineType> engines) {
        int safeLimit = Math.max(1, limit);
        int safeRepeat = Math.max(1, repeat);
        List<SearchField> fields = SearchField.fromOption(fieldOption);
        List<BenchmarkEngineResult> results = new ArrayList<>();
        for (SearchEngineType engine : engines) {
            results.add(benchmarkEngine(storePath, query, safeLimit, safeRepeat, includeBroken, fields, engine));
        }
        return new BenchmarkReport(storePath.toAbsolutePath().normalize(), query,
                fieldOption == null || fieldOption.isBlank() ? "all" : fieldOption,
                safeLimit, safeRepeat, includeBroken, List.copyOf(results));
    }

    private BenchmarkEngineResult benchmarkEngine(Path storePath, String query, int limit, int repeat,
                                                  boolean includeBroken, List<SearchField> fields,
                                                  SearchEngineType engine) {
        SearchStoreService service = new SearchStoreService(new SearchQueryNormalizer(), engine.create(), new RawFieldVerifier());
        long totalElapsed = 0;
        long minElapsed = Long.MAX_VALUE;
        long maxElapsed = 0;
        int completedRuns = 0;
        BenchmarkCounts counts = BenchmarkCounts.empty();
        for (int run = 0; run < repeat; run++) {
            long started = System.nanoTime();
            try {
                SearchResponse response = service.search(storePath, query, limit, BENCHMARK_CONTEXT_CHARS,
                        BENCHMARK_MAX_MATCHES_PER_MESSAGE, fields);
                long elapsed = elapsedMs(started);
                totalElapsed += elapsed;
                minElapsed = Math.min(minElapsed, elapsed);
                maxElapsed = Math.max(maxElapsed, elapsed);
                completedRuns++;
                counts = counts(response, includeBroken);
            } catch (Exception e) {
                long elapsed = elapsedMs(started);
                totalElapsed += elapsed;
                minElapsed = minElapsed == Long.MAX_VALUE ? elapsed : Math.min(minElapsed, elapsed);
                maxElapsed = Math.max(maxElapsed, elapsed);
                return new BenchmarkEngineResult(engine.option(), repeat, completedRuns, "FAILED", safeMessage(e),
                        counts, totalElapsed, average(totalElapsed, Math.max(1, completedRuns)), minElapsed, maxElapsed);
            }
        }
        return new BenchmarkEngineResult(engine.option(), repeat, completedRuns, "SUCCESS", null, counts, totalElapsed,
                average(totalElapsed, completedRuns), minElapsed == Long.MAX_VALUE ? 0 : minElapsed, maxElapsed);
    }

    private BenchmarkCounts counts(SearchResponse response, boolean includeBroken) {
        long displayedMessages = 0;
        long displayedMatches = 0;
        long hiddenBrokenMatches = 0;
        for (VerifiedMessage message : response.verifiedMessages()) {
            long visibleForMessage = 0;
            for (FieldMatch match : message.matches()) {
                if (!includeBroken && isBroken(message.candidate(), match)) {
                    hiddenBrokenMatches++;
                } else {
                    visibleForMessage++;
                }
            }
            if (visibleForMessage > 0) {
                displayedMessages++;
                displayedMatches += visibleForMessage;
            }
        }
        return new BenchmarkCounts(response.sqlCandidates(), response.verifiedMessages().size(),
                displayedMessages, response.totalMatches(), displayedMatches, hiddenBrokenMatches);
    }

    private boolean isBroken(SearchCandidate candidate, FieldMatch match) {
        SearchField field = Arrays.stream(SearchField.values())
                .filter(value -> value.columnName().equalsIgnoreCase(match.field())
                        || value.displayName().equalsIgnoreCase(match.field()))
                .findFirst()
                .orElse(null);
        return qualityAnalyzer.diagnose(field == null ? null : candidate.fieldValue(field)).level() == TextQualityLevel.BROKEN;
    }

    private long elapsedMs(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    private long average(long elapsedMs, int runs) {
        return runs <= 0 ? 0 : Math.round(elapsedMs / (double) runs);
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.replace('\r', ' ').replace('\n', ' ');
    }
}
