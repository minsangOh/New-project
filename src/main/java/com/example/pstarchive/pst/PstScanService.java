package com.example.pstarchive.pst;

import java.nio.file.Files;
import java.nio.file.Path;

public class PstScanService {
    private final PstScanner scanner;

    public PstScanService(PstScanner scanner) {
        this.scanner = scanner;
    }

    public PstScanSummary scan(Path pstPath, PstScanOptions options) {
        Path normalized = pstPath.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            options.output().println("[FATAL] PST file does not exist: " + normalized);
            PstScanSummary summary = new PstScanSummary(0, 0, 0, 0, 1, "java-libpst-0.9.3", "file missing");
            printSummary(summary, options);
            return summary;
        }
        if (!Files.isRegularFile(normalized)) {
            options.output().println("[FATAL] Path is not a regular file: " + normalized);
            PstScanSummary summary = new PstScanSummary(0, 0, 0, 0, 1, "java-libpst-0.9.3", "not a file");
            printSummary(summary, options);
            return summary;
        }
        return scanner.scan(normalized, options);
    }

    private void printSummary(PstScanSummary summary, PstScanOptions options) {
        options.output().println("SCAN SUMMARY");
        options.output().println("foldersVisited: " + summary.foldersVisited());
        options.output().println("messagesScanned: " + summary.messagesScanned());
        options.output().println("fieldErrors: " + summary.fieldErrors());
        options.output().println("messageErrors: " + summary.messageErrors());
        options.output().println("fatalErrors: " + summary.fatalErrors());
    }
}
