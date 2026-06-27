package com.example.pstarchive.search.benchmark;

public record BenchmarkCounts(
        long candidateCount,
        long verifiedMessages,
        long displayedMessages,
        long totalMatches,
        long displayedMatches,
        long hiddenBrokenMatches
) {
    public static BenchmarkCounts empty() {
        return new BenchmarkCounts(0, 0, 0, 0, 0, 0);
    }
}
