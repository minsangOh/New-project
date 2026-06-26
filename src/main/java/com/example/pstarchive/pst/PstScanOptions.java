package com.example.pstarchive.pst;

import java.io.PrintStream;

public record PstScanOptions(
        int limit,
        String mode,
        PrintStream output
) {
    public PstScanOptions {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be zero or greater");
        }
        if (output == null) {
            output = System.out;
        }
    }
}
