package com.example.pstarchive.cli;

import com.example.pstarchive.index.PstIndexOptions;
import com.example.pstarchive.index.PstIndexService;
import com.example.pstarchive.index.PstIndexSummary;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "index-file",
        mixinStandardHelpOptions = true,
        description = "Extract PST messages into a standalone SQLite shard store. Search is not implemented in this phase."
)
public class IndexFileCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "PST file path.")
    private Path pstPath;

    @Option(names = "--out", required = true, description = "SQLite store path to create or update.")
    private Path outPath;

    @Option(names = "--limit", description = "Maximum number of messages to store. Default: ${DEFAULT-VALUE}")
    private int limit = 1000;

    @Option(names = "--replace", description = "Delete existing folders, messages, and index_errors before indexing.")
    private boolean replace;

    @Override
    public Integer call() {
        Path normalizedPst = pstPath.toAbsolutePath().normalize();
        if (!Files.exists(normalizedPst)) {
            System.err.println("PST file does not exist: " + normalizedPst);
            return 2;
        }
        if (!Files.isRegularFile(normalizedPst)) {
            System.err.println("Path is not a regular file: " + normalizedPst);
            return 2;
        }
        Path normalizedOut = outPath.toAbsolutePath().normalize();
        PstIndexSummary summary = new PstIndexService().index(
                new PstIndexOptions(normalizedPst, normalizedOut, limit, replace, System.out)
        );
        return "FAILED".equals(summary.status()) ? 1 : 0;
    }
}
