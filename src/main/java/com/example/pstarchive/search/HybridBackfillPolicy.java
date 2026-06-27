package com.example.pstarchive.search;

import java.util.Locale;

public enum HybridBackfillPolicy {
    AUTO("auto"),
    ALWAYS("always"),
    NEVER("never");

    private final String option;

    HybridBackfillPolicy(String option) {
        this.option = option;
    }

    public String option() {
        return option;
    }

    public boolean shouldBackfill(boolean riskyQuery) {
        return switch (this) {
            case AUTO -> riskyQuery;
            case ALWAYS -> true;
            case NEVER -> false;
        };
    }

    public static HybridBackfillPolicy fromOption(String option) {
        if (option == null || option.isBlank()) {
            return AUTO;
        }
        String normalized = option.toLowerCase(Locale.ROOT);
        for (HybridBackfillPolicy policy : values()) {
            if (policy.option.equals(normalized)) {
                return policy;
            }
        }
        throw new IllegalArgumentException("Unknown hybrid backfill policy: " + option
                + ". Supported policies: auto, always, never");
    }
}