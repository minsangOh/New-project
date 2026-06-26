package com.example.pstarchive.cli;

import com.example.pstarchive.encoding.EncodingProbeService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

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
        return new EncodingProbeService().probe(normalized, limit, System.out);
    }
}
