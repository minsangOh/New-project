package com.example.pstarchive.pst;

import java.nio.file.Path;

public interface PstScanner {
    PstScanSummary scan(Path pstPath);
}
