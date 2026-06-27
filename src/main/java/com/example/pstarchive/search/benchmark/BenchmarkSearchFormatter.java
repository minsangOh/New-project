package com.example.pstarchive.search.benchmark;

import java.io.PrintStream;

public class BenchmarkSearchFormatter {
    private final PrintStream out;

    public BenchmarkSearchFormatter(PrintStream out) {
        this.out = out;
    }

    public void print(BenchmarkReport report) {
        out.println("BENCHMARK SEARCH");
        out.println("store: " + report.storePath());
        out.println("query: " + report.query());
        out.println("field: " + report.field());
        out.println("limit: " + report.limit());
        out.println("repeat: " + report.repeat());
        out.println("includeBroken: " + report.includeBroken());
        out.println();
        for (BenchmarkEngineResult result : report.results()) {
            printEngine(result);
            out.println();
        }
        printDecisionHint(report);
    }

    private void printEngine(BenchmarkEngineResult result) {
        BenchmarkCounts counts = result.counts();
        out.println("[ENGINE " + result.engine() + "]");
        out.println("status: " + result.status());
        out.println("runs: " + result.runs());
        out.println("completedRuns: " + result.completedRuns());
        out.println("candidateCount: " + counts.candidateCount());
        out.println("verifiedMessages: " + counts.verifiedMessages());
        out.println("displayedMessages: " + counts.displayedMessages());
        out.println("totalMatches: " + counts.totalMatches());
        out.println("displayedMatches: " + counts.displayedMatches());
        out.println("hiddenBrokenMatches: " + counts.hiddenBrokenMatches());
        out.println("elapsedMs: " + result.elapsedMs());
        out.println("averageElapsedMs: " + result.averageElapsedMs());
        out.println("minElapsedMs: " + result.minElapsedMs());
        out.println("maxElapsedMs: " + result.maxElapsedMs());
        if (!result.success()) {
            out.println("error: " + result.error());
        }
    }

    private void printDecisionHint(BenchmarkReport report) {
        out.println("DECISION HINT");
        BenchmarkEngineResult like = result(report, "like");
        BenchmarkEngineResult fts5 = result(report, "fts5");
        if (like != null && fts5 != null && like.success() && fts5.success()) {
            String reason = incompatibleReason(like.counts(), fts5.counts());
            if (reason == null) {
                out.println("policyDecision: KEEP_LIKE_DEFAULT");
                out.println("reason: compatible_for_this_query_only");
                out.println("FTS5 compatible for this query. Keep LIKE as default until the full query set is compatible.");
            } else {
                out.println("policyDecision: KEEP_LIKE_DEFAULT");
                out.println("reason: " + reason);
                out.println("Do not switch default engine to FTS5 for this store/query set.");
                out.println("FTS5 may still be useful as a fast optional candidate engine.");
            }
        } else if (fts5 != null && !fts5.success()) {
            out.println("policyDecision: KEEP_LIKE_DEFAULT");
            out.println("reason: fts5_failed");
            out.println("FTS5 benchmark failed. Run build-search-index first or use --engine like.");
        } else {
            out.println("policyDecision: KEEP_LIKE_DEFAULT");
            out.println("reason: insufficient_comparison");
            out.println("Use repeated real-store runs before changing the default engine.");
        }
    }

    private BenchmarkEngineResult result(BenchmarkReport report, String engine) {
        return report.results().stream()
                .filter(value -> value.engine().equals(engine))
                .findFirst()
                .orElse(null);
    }

    private String incompatibleReason(BenchmarkCounts like, BenchmarkCounts fts5) {
        if (like.verifiedMessages() != fts5.verifiedMessages()) {
            return "fts5_verified_count_diff";
        }
        if (like.displayedMessages() != fts5.displayedMessages()) {
            return "fts5_displayed_count_diff";
        }
        if (like.displayedMatches() != fts5.displayedMatches()) {
            return "fts5_displayed_match_count_diff";
        }
        if (like.totalMatches() != fts5.totalMatches()) {
            return "fts5_total_match_count_diff";
        }
        if (like.hiddenBrokenMatches() != fts5.hiddenBrokenMatches()) {
            return "fts5_hidden_broken_count_diff";
        }
        return null;
    }
}
