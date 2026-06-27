package com.example.pstarchive.cli;

import com.example.pstarchive.search.SearchField;
import com.example.pstarchive.search.SearchResultFormatter;
import com.example.pstarchive.search.SearchEngineType;
import com.example.pstarchive.search.RawFieldVerifier;
import com.example.pstarchive.search.SearchQueryNormalizer;
import com.example.pstarchive.search.SearchStoreService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "search-store",
        mixinStandardHelpOptions = true,
        description = "Search a Phase 3A SQLite store. Default engine is accuracy-first LIKE; FTS5 is a fast optional candidate engine."
)
public class SearchStoreCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "SQLite store path.")
    private Path storePath;

    @Parameters(index = "1", description = "Search query.")
    private String query;

    @Option(names = "--limit", description = "Maximum verified messages to print. Default: ${DEFAULT-VALUE}")
    private int limit = 20;

    @Option(names = "--context", description = "Context characters before and after a match. Default: ${DEFAULT-VALUE}")
    private int context = 80;

    @Option(names = "--max-matches-per-message", description = "Maximum matches per verified message. Default: ${DEFAULT-VALUE}")
    private int maxMatchesPerMessage = 5;

    @Option(names = "--field", description = "Search field: subject, sender, recipients, cc, folder, body, all. Default: ${DEFAULT-VALUE}")
    private String field = "all";

    @Option(names = "--engine", description = "Candidate engine: like, fts5. Default: ${DEFAULT-VALUE}. Use fts5 for fast optional candidate search; use like for conservative accuracy.")
    private String engine = "like";

    @Option(names = "--include-broken", description = "Include BROKEN quality matches that are hidden by default.")
    private boolean includeBroken;

    @Option(names = {"-o", "--output"}, description = "Write report to a UTF-8 text file.")
    private Path outputPath;

    @Override
    public Integer call() {
        Path normalized = storePath.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            System.err.println("SQLite store does not exist: " + normalized);
            return 2;
        }
        List<SearchField> fields;
        try {
            fields = SearchField.fromOption(field);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return 2;
        }
        SearchEngineType engineType;
        try {
            engineType = SearchEngineType.fromOption(engine);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return 2;
        }
        return CommandOutput.write(outputPath, out -> {
            var response = new SearchStoreService(new SearchQueryNormalizer(), engineType.create(), new RawFieldVerifier())
                    .search(normalized, query, limit, context, maxMatchesPerMessage, fields);
            new SearchResultFormatter(out).print(new SearchResultFormatter.PathLabel(normalized.toString()), response, includeBroken);
        });
    }
}
