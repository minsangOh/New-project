package com.example.pstarchive.search;

import com.example.pstarchive.search.fts.Fts5CandidateSearcher;

import java.util.Locale;

public enum SearchEngineType {
    LIKE("like"),
    FTS5("fts5");

    private final String option;

    SearchEngineType(String option) {
        this.option = option;
    }

    public String option() {
        return option;
    }

    public CandidateSearchEngine create() {
        return switch (this) {
            case LIKE -> new LikeCandidateSearcher();
            case FTS5 -> new Fts5CandidateSearcher();
        };
    }

    public static SearchEngineType fromOption(String option) {
        if (option == null || option.isBlank()) {
            return LIKE;
        }
        String normalized = option.toLowerCase(Locale.ROOT);
        for (SearchEngineType type : values()) {
            if (type.option.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown search engine: " + option + ". Supported engines: like, fts5");
    }
}
