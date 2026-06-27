package com.example.pstarchive.cli;

import com.example.pstarchive.search.SearchField;
import com.example.pstarchive.search.compare.CompareSearchFormatter;
import com.example.pstarchive.search.compare.CompareSearchReport;
import com.example.pstarchive.search.compare.CompareSearchService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "compare-search-engines",
        mixinStandardHelpOptions = true,
        description = "Diagnose verified messages found by LIKE but missed by FTS5, without changing search defaults."
)
public class CompareSearchEnginesCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "SQLite store path.")
    private Path storePath;

    @Parameters(index = "1", description = "Search query.")
    private String query;

    @Option(names = "--limit", description = "Maximum verified messages per engine. Default: ${DEFAULT-VALUE}")
    private int limit = 20;

    @Option(names = "--field", description = "Search field: subject, sender, recipients, cc, folder, body, all. Default: ${DEFAULT-VALUE}")
    private String field = "all";

    @Option(names = "--include-broken", description = "Include BROKEN quality matches in visible match counts.")
    private boolean includeBroken;

    @Option(names = {"-o", "--output"}, description = "Write comparison report to a UTF-8 text file.")
    private Path outputPath;

    @Override
    public Integer call() {
        Path normalized = storePath.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            System.err.println("SQLite store does not exist: " + normalized);
            return 2;
        }
        try {
            SearchField.fromOption(field);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return 2;
        }
        try {
            CompareSearchReport report = new CompareSearchService()
                    .compare(normalized, query, field, limit, includeBroken);
            int writeExit = CommandOutput.write(outputPath, out -> new CompareSearchFormatter(out).print(report));
            return writeExit != 0 ? writeExit : (report.hasFts5Error() ? 1 : 0);
        } catch (Exception e) {
            System.err.println("Unable to compare search engines: " + e.getClass().getSimpleName() + ": " + safe(e.getMessage()));
            return 1;
        }
    }

    private String safe(String message) {
        return message == null || message.isBlank() ? "no message" : message.replace('\r', ' ').replace('\n', ' ');
    }
}
