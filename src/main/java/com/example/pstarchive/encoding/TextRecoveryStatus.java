package com.example.pstarchive.encoding;

public enum TextRecoveryStatus {
    OK,
    DEGRADED,
    UNRECOVERABLE,
    NULL,
    ERROR;

    public String value() {
        return name();
    }

    public static TextRecoveryStatus fromProbeStatus(String status) {
        if (status == null || status.isBlank()) {
            return ERROR;
        }
        try {
            return TextRecoveryStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return ERROR;
        }
    }
}
