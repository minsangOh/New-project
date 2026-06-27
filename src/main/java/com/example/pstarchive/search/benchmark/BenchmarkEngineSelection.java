package com.example.pstarchive.search.benchmark;

import com.example.pstarchive.search.SearchEngineType;

import java.util.List;
import java.util.Locale;

public enum BenchmarkEngineSelection {
    LIKE,
    FTS5,
    BOTH;

    public List<SearchEngineType> engines() {
        return switch (this) {
            case LIKE -> List.of(SearchEngineType.LIKE);
            case FTS5 -> List.of(SearchEngineType.FTS5);
            case BOTH -> List.of(SearchEngineType.LIKE, SearchEngineType.FTS5);
        };
    }

    public static BenchmarkEngineSelection fromOption(String option) {
        if (option == null || option.isBlank()) {
            return BOTH;
        }
        return switch (option.toLowerCase(Locale.ROOT)) {
            case "like" -> LIKE;
            case "fts5" -> FTS5;
            case "both" -> BOTH;
            default -> throw new IllegalArgumentException("Unknown benchmark engine: " + option
                    + ". Supported engines: like, fts5, both");
        };
    }
}
