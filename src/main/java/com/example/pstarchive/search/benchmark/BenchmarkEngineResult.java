package com.example.pstarchive.search.benchmark;

public record BenchmarkEngineResult(
        String engine,
        int runs,
        int completedRuns,
        String status,
        String error,
        BenchmarkCounts counts,
        long elapsedMs,
        long averageElapsedMs,
        long minElapsedMs,
        long maxElapsedMs
) {
    public boolean success() {
        return "SUCCESS".equals(status);
    }
}
