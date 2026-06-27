package com.example.pstarchive.cli;

import com.example.pstarchive.search.fts.Fts5IndexBuilder;
import com.example.pstarchive.search.fts.Fts5IndexSummary;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "build-search-index",
        mixinStandardHelpOptions = true,
        description = "Build a SQLite FTS5 candidate-search index from the stored messages table."
)
public class BuildSearchIndexCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "SQLite store path.")
    private Path storePath;

    @Option(names = "--replace", description = "Delete existing FTS5 rows before rebuilding from messages.")
    private boolean replace;

    @Override
    public Integer call() {
        Path normalized = storePath.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            System.err.println("SQLite store does not exist: " + normalized);
            return 2;
        }
        System.out.println("FTS5 INDEX BUILD START");
        System.out.println("store: " + normalized);
        System.out.println("replace: " + replace);
        System.out.println();
        Fts5IndexSummary summary;
        try {
            summary = new Fts5IndexBuilder().build(normalized, replace);
        } catch (Exception e) {
            System.err.println("Unable to build FTS5 index: " + e.getClass().getSimpleName() + ": " + safe(e.getMessage()));
            return 1;
        }
        System.out.println("FTS5 INDEX SUMMARY");
        System.out.println("messagesRead: " + summary.messagesRead());
        System.out.println("rowsIndexed: " + summary.rowsIndexed());
        System.out.println("rowErrors: " + summary.rowErrors());
        System.out.println("elapsedMs: " + summary.elapsedMs());
        System.out.println("status: " + summary.status());
        return "FAILED".equals(summary.status()) ? 1 : 0;
    }

    private String safe(String message) {
        return message == null || message.isBlank() ? "no message" : message.replace('\r', ' ').replace('\n', ' ');
    }
}
