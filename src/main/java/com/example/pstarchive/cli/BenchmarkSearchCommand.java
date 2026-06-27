package com.example.pstarchive.cli;

import com.example.pstarchive.search.SearchField;
import com.example.pstarchive.search.benchmark.BenchmarkEngineSelection;
import com.example.pstarchive.search.benchmark.BenchmarkReport;
import com.example.pstarchive.search.benchmark.BenchmarkSearchFormatter;
import com.example.pstarchive.search.benchmark.BenchmarkSearchService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "benchmark-search",
        mixinStandardHelpOptions = true,
        description = "Benchmark LIKE and FTS5 candidate search while preserving source-field verification."
)
public class BenchmarkSearchCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "SQLite store path.")
    private Path storePath;

    @Parameters(index = "1", description = "Search query.")
    private String query;

    @Option(names = "--engine", description = "Benchmark engine: like, fts5, both. Default: ${DEFAULT-VALUE}")
    private String engine = "both";

    @Option(names = "--limit", description = "Maximum verified messages per run. Default: ${DEFAULT-VALUE}")
    private int limit = 20;

    @Option(names = "--repeat", description = "Number of benchmark runs. Default: ${DEFAULT-VALUE}")
    private int repeat = 3;

    @Option(names = "--field", description = "Search field: subject, sender, recipients, cc, folder, body, all. Default: ${DEFAULT-VALUE}")
    private String field = "all";

    @Option(names = "--include-broken", description = "Include BROKEN quality matches in displayed counts.")
    private boolean includeBroken;

    @Option(names = {"-o", "--output"}, description = "Write benchmark report to a UTF-8 text file.")
    private Path outputPath;

    @Override
    public Integer call() {
        Path normalized = storePath.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            System.err.println("SQLite store does not exist: " + normalized);
            return 2;
        }
        BenchmarkEngineSelection selection;
        try {
            selection = BenchmarkEngineSelection.fromOption(engine);
            SearchField.fromOption(field);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return 2;
        }
        BenchmarkReport report = new BenchmarkSearchService()
                .benchmark(normalized, query, field, limit, repeat, includeBroken, selection.engines());
        int writeExit = CommandOutput.write(outputPath, out ->
                new BenchmarkSearchFormatter(out).print(report));
        return writeExit != 0 ? writeExit : (report.hasFailure() ? 1 : 0);
    }
}
