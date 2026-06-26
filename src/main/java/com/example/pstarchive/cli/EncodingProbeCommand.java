package com.example.pstarchive.cli;

import com.example.pstarchive.encoding.EncodingProbeService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "encoding-probe",
        mixinStandardHelpOptions = true,
        description = "Diagnose Korean text encoding recovery candidates for a PST file."
)
public class EncodingProbeCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "PST file path.")
    private Path pstPath;

    @Option(names = "--limit", description = "Maximum number of mail probes to print. Default: ${DEFAULT-VALUE}")
    private int limit = 10;

    @Option(names = {"-o", "--output"}, description = "Write the full probe report to a UTF-8 text file.")
    private Path outputPath;

    @Override
    public Integer call() {
        Path normalized = pstPath.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            System.err.println("PST file does not exist: " + normalized);
            return 2;
        }
        if (!Files.isRegularFile(normalized)) {
            System.err.println("Path is not a regular file: " + normalized);
            return 2;
        }
        if (outputPath == null) {
            return new EncodingProbeService().probe(normalized, limit, System.out);
        }
        return writeProbeReport(normalized);
    }

    private int writeProbeReport(Path pstPath) {
        Path normalizedOutput = outputPath.toAbsolutePath().normalize();
        Path parent = normalizedOutput.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream file = new BufferedOutputStream(Files.newOutputStream(normalizedOutput))) {
                file.write(0xEF);
                file.write(0xBB);
                file.write(0xBF);
                try (PrintStream out = new PrintStream(file, true, StandardCharsets.UTF_8)) {
                    int exitCode = new EncodingProbeService().probe(pstPath, limit, out);
                    System.out.println("Encoding probe report written: " + normalizedOutput);
                    System.out.println("Open the report as UTF-8. It includes console charset diagnostics.");
                    return exitCode;
                }
            }
        } catch (IOException e) {
            System.err.println("Unable to write encoding probe report: " + e.getMessage());
            return 2;
        }
    }
}
