package com.example.pstarchive.model;

import java.util.Arrays;

public enum PstStatus {
    ACTIVE("active"),
    WARM("warm"),
    COLD("cold"),
    ARCHIVE("archive"),
    MISSING("missing"),
    INVALID("invalid");

    private final String value;

    PstStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static PstStatus fromValue(String value) {
        return Arrays.stream(values())
                .filter(status -> status.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported PST status: " + value));
    }
}
