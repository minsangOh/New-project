package com.example.pstarchive.index;

import java.io.PrintStream;
import java.nio.file.Path;

public record PstIndexOptions(
        Path pstPath,
        Path storePath,
        int limit,
        boolean replace,
        PrintStream output
) {
    public PstIndexOptions {
        if (output == null) {
            output = System.out;
        }
    }
}
