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
            if (sameCounts(like.counts(), fts5.counts())) {
                out.println("FTS5 looks compatible for this query.");
            } else {
                out.println("LIKE and FTS5 returned different verified counts. Review before changing defaults.");
            }
        } else if (fts5 != null && !fts5.success()) {
            out.println("FTS5 benchmark failed. Run build-search-index first or use --engine like.");
        } else {
            out.println("Use repeated real-store runs before changing the default engine.");
        }
    }

    private BenchmarkEngineResult result(BenchmarkReport report, String engine) {
        return report.results().stream()
                .filter(value -> value.engine().equals(engine))
                .findFirst()
                .orElse(null);
    }

    private boolean sameCounts(BenchmarkCounts left, BenchmarkCounts right) {
        return left.candidateCount() == right.candidateCount()
                && left.verifiedMessages() == right.verifiedMessages()
                && left.displayedMessages() == right.displayedMessages()
                && left.totalMatches() == right.totalMatches()
                && left.displayedMatches() == right.displayedMatches()
                && left.hiddenBrokenMatches() == right.hiddenBrokenMatches();
    }
}
