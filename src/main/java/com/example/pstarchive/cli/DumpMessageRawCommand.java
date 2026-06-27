package com.example.pstarchive.cli;

import com.example.pstarchive.inspect.MessageDetail;
import com.example.pstarchive.inspect.MessageDetailReader;
import com.example.pstarchive.textquality.TextQualityFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

@Command(
        name = "dump-message-raw",
        mixinStandardHelpOptions = true,
        description = "Dump one stored message with text quality counters and Unicode code point samples."
)
public class DumpMessageRawCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "SQLite store path.")
    private Path storePath;

    @Option(names = "--id", required = true, description = "messages.id value.")
    private long messageId;

    @Option(names = {"-o", "--output"}, description = "Write report to a UTF-8 text file.")
    private Path outputPath;

    @Override
    public Integer call() {
        Path normalized = storePath.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            System.err.println("SQLite store does not exist: " + normalized);
            return 2;
        }
        Optional<MessageDetail> detail;
        try {
            detail = new MessageDetailReader().findById(normalized, messageId);
        } catch (Exception e) {
            System.err.println("Unable to read message: " + e.getClass().getSimpleName() + ": " + safe(e.getMessage()));
            return 1;
        }
        if (detail.isEmpty()) {
            System.err.println("Message not found: " + messageId);
            return 2;
        }
        return CommandOutput.write(outputPath, out -> new TextQualityFormatter(out).printRawDump(detail.get()));
    }

    private String safe(String message) {
        return message == null || message.isBlank() ? "no message" : message.replace('\r', ' ').replace('\n', ' ');
    }
}
