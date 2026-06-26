package com.example.pstarchive.cli;

import com.example.pstarchive.inspect.InspectionFormatter;
import com.example.pstarchive.inspect.StoreQualityReporter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "quality-report",
        mixinStandardHelpOptions = true,
        description = "Evaluate whether a Phase 3A SQLite store is ready for Phase 3B search candidate work."
)
public class QualityReportCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "SQLite store path.")
    private Path storePath;

    @Option(names = {"-o", "--output"}, description = "Write report to a UTF-8 text file.")
    private Path outputPath;

    @Override
    public Integer call() {
        Path normalized = storePath.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            System.err.println("SQLite store does not exist: " + normalized);
            return 2;
        }
        return CommandOutput.write(outputPath, out ->
                new InspectionFormatter(out).printQualityReport(new StoreQualityReporter().report(normalized))
        );
    }
}
