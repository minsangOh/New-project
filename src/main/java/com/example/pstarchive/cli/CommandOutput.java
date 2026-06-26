package com.example.pstarchive.cli;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CommandOutput {
    private CommandOutput() {
    }

    public static int write(Path outputPath, OutputAction action) {
        if (outputPath == null) {
            try {
                action.write(System.out);
                return 0;
            } catch (Exception e) {
                System.err.println("Command failed: " + e.getClass().getSimpleName() + ": " + safeMessage(e));
                return 1;
            }
        }

        Path normalized = outputPath.toAbsolutePath().normalize();
        try {
            Path parent = normalized.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream file = new BufferedOutputStream(Files.newOutputStream(normalized))) {
                file.write(0xEF);
                file.write(0xBB);
                file.write(0xBF);
                try (PrintStream out = new PrintStream(file, true, StandardCharsets.UTF_8)) {
                    action.write(out);
                }
            }
            System.out.println("Report written: " + normalized);
            return 0;
        } catch (Exception e) {
            System.err.println("Unable to write report: " + e.getClass().getSimpleName() + ": " + safeMessage(e));
            return 1;
        }
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? "no message" : message.replace("\r", " ").replace("\n", " ");
    }

    @FunctionalInterface
    public interface OutputAction {
        void write(PrintStream out) throws Exception;
    }
}
