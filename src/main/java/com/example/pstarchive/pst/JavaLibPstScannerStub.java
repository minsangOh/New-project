package com.example.pstarchive.pst;

import java.nio.file.Path;

public class JavaLibPstScannerStub implements PstScanner {
    @Override
    public PstScanSummary scan(Path pstPath, PstScanOptions options) {
        return new PstScanSummary(
                0,
                0,
                0,
                0,
                0,
                "java-libpst-not-enabled",
                "Phase 1 defines the scanner contract only. java-libpst benchmark is planned for Phase 2."
        );
    }
}
