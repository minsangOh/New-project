package com.example.pstarchive;

import com.example.pstarchive.pst.JavaLibPstScanner;
import com.example.pstarchive.pst.PstScanOptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JavaLibPstScannerIntegrationTest {
    @Test
    void scansRealPstWhenPstTestFileIsConfigured() {
        String pstTestFile = System.getenv("PST_TEST_FILE");
        assumeTrue(pstTestFile != null && !pstTestFile.isBlank(), "PST_TEST_FILE is not set; skipping PST integration test.");

        Path pstPath = Path.of(pstTestFile);
        assumeTrue(Files.exists(pstPath), "PST_TEST_FILE does not exist: " + pstPath);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        var summary = new JavaLibPstScanner().scan(
                pstPath,
                new PstScanOptions(10, "integration-test", new PrintStream(output, true, StandardCharsets.UTF_8))
        );

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("PST SCAN START"));
        assertTrue(text.contains("SCAN SUMMARY"));
        assertTrue(summary.fatalErrors() == 0, "Expected no fatal errors. Output:\n" + text);
    }
}
