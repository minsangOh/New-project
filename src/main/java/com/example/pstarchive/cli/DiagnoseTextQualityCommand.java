package com.example.pstarchive.cli;

import com.example.pstarchive.textquality.TextQualityFormatter;
import com.example.pstarchive.textquality.TextQualityReportService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "diagnose-text-quality",
        mixinStandardHelpOptions = true,
        description = "Diagnose stored SQLite message fields for NUL chars, mojibake patterns, and status mismatches."
)
public class DiagnoseTextQualityCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "SQLite store path.")
    private Path storePath;

    @Option(names = "--limit", description = "Maximum messages to inspect. Default: ${DEFAULT-VALUE}")
    private int limit = 100;

    @Option(names = {"-o", "--output"}, description = "Write report to a UTF-8 text file.")
    private Path outputPath;

    @Override
    public Integer call() {
        Path normalized = storePath.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            System.err.println("SQLite store does not exist: " + normalized);
            return 2;
        }
        if (limit <= 0) {
            System.err.println("--limit must be greater than 0");
            return 2;
        }
        return CommandOutput.write(outputPath, out ->
                new TextQualityFormatter(out).printReport(new TextQualityReportService().analyze(normalized, limit))
        );
    }
}
