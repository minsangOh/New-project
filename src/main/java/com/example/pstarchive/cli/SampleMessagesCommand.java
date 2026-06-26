package com.example.pstarchive.cli;

import com.example.pstarchive.inspect.InspectionFormatter;
import com.example.pstarchive.inspect.MessageSampler;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "sample-messages",
        mixinStandardHelpOptions = true,
        description = "Print stored message samples with short body previews."
)
public class SampleMessagesCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "SQLite store path.")
    private Path storePath;

    @Option(names = "--limit", description = "Maximum number of message samples to print. Default: ${DEFAULT-VALUE}")
    private int limit = 10;

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
                new InspectionFormatter(out).printSamples(new MessageSampler().sample(normalized, limit))
        );
    }
}
