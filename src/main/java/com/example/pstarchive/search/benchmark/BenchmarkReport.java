package com.example.pstarchive.search.benchmark;

import java.nio.file.Path;
import java.util.List;

public record BenchmarkReport(
        Path storePath,
        String query,
        String field,
        int limit,
        int repeat,
        boolean includeBroken,
        List<BenchmarkEngineResult> results
) {
    public boolean hasFailure() {
        return results.stream().anyMatch(result -> !result.success());
    }
}
