package com.example.pstarchive;

import com.example.pstarchive.cli.ArchiveCommand;
import com.example.pstarchive.cli.CommandOutput;
import com.example.pstarchive.encoding.ExtractedText;
import com.example.pstarchive.encoding.TextRecoveryStatus;
import com.example.pstarchive.index.IndexFieldError;
import com.example.pstarchive.index.MessageStoreWriter;
import com.example.pstarchive.index.PstIndexSummary;
import com.example.pstarchive.index.ShardStoreSchema;
import com.example.pstarchive.inspect.InspectionFormatter;
import com.example.pstarchive.inspect.MessageDetailReader;
import com.example.pstarchive.inspect.MessageSampler;
import com.example.pstarchive.inspect.StoreInspectionResult;
import com.example.pstarchive.inspect.StoreInspector;
import com.example.pstarchive.inspect.StoreQualityDecision;
import com.example.pstarchive.inspect.StoreQualityReport;
import com.example.pstarchive.inspect.StoreQualityReporter;
import com.example.pstarchive.pst.ExtractedFolder;
import com.example.pstarchive.pst.ExtractedMail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StoreInspectionTest {
    @TempDir
    Path tempDir;

    @Test
    void inspectStoreComputesCountsDistributionsNullsAndAverages() throws Exception {
        Path store = createStoreWithMessages(readySummary(), false);

        StoreInspectionResult result = new StoreInspector().inspect(store);

        assertEquals(1, result.foldersCount());
        assertEquals(2, result.messagesCount());
        assertEquals(1, result.indexRunsCount());
        assertEquals(0, result.indexErrorsCount());
        assertEquals(1, countStatus(result.subjectStatusCounts(), "OK"));
        assertEquals(1, countStatus(result.subjectStatusCounts(), "NULL"));
        assertEquals(1, result.subjectNullCount());
        assertTrue(result.averageBodyHtmlTextLength() > 0.0);
        assertEquals("SUCCESS", result.latestRun().orElseThrow().status());
    }

    @Test
    void qualityReportReadyWhenStoreLooksHealthy() throws Exception {
        Path store = createStoreWithMessages(readySummary(), false);

        StoreQualityReport report = new StoreQualityReporter().report(store);

        assertEquals(StoreQualityDecision.READY, report.decision());
        assertTrue(report.bodyTextCoveragePercent() >= 50.0);
    }

    @Test
    void qualityReportReadyWithWarningsForDegradedFields() throws Exception {
        Path store = createStoreWithMessages(new PstIndexSummary(1, 2, 2, 0, 0, 0, 10, 3, 0, 1, 0, 10, "SUCCESS"), true);

        StoreQualityReport report = new StoreQualityReporter().report(store);

        assertEquals(StoreQualityDecision.READY_WITH_WARNINGS, report.decision());
        assertFalse(report.warnings().isEmpty());
        assertFalse(report.topErrors().isEmpty());
    }

    @Test
    void qualityReportNotReadyWhenNoMessagesExist() throws Exception {
        Path store = tempDir.resolve("empty.sqlite");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + store.toAbsolutePath())) {
            ShardStoreSchema.migrate(connection);
        }

        StoreQualityReport report = new StoreQualityReporter().report(store);

        assertEquals(StoreQualityDecision.NOT_READY, report.decision());
        assertFalse(report.blockingIssues().isEmpty());
    }

    @Test
    void sampleMessagesUsesPreviews() throws Exception {
        Path store = createStoreWithMessages(readySummary(), false);

        var samples = new MessageSampler().sample(store, 1);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new InspectionFormatter(new PrintStream(buffer, true, StandardCharsets.UTF_8)).printSamples(samples);
        String output = buffer.toString(StandardCharsets.UTF_8);

        assertEquals(1, samples.size());
        assertTrue(output.contains("MESSAGE SAMPLES"));
        assertTrue(output.contains("body_html_text_preview"));
        assertTrue(output.contains("..."));
    }

    @Test
    void showMessageReturnsEmptyForMissingId() throws Exception {
        Path store = createStoreWithMessages(readySummary(), false);

        assertTrue(new MessageDetailReader().findById(store, 9999).isEmpty());
    }

    @Test
    void outputFileIsUtf8WithBom() throws Exception {
        Path output = tempDir.resolve("report.txt");

        int exit = CommandOutput.write(output, out -> out.println("hello"));
        byte[] bytes = Files.readAllBytes(output);

        assertEquals(0, exit);
        assertEquals((byte) 0xEF, bytes[0]);
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);
    }

    @Test
    void archiveCommandExposesStoreInspectionCommands() {
        CommandLine commandLine = new CommandLine(new ArchiveCommand());

        assertTrue(commandLine.getSubcommands().containsKey("inspect-store"));
        assertTrue(commandLine.getSubcommands().containsKey("sample-messages"));
        assertTrue(commandLine.getSubcommands().containsKey("show-message"));
        assertTrue(commandLine.getSubcommands().containsKey("quality-report"));
        assertDoesNotThrow(() -> commandLine.parseArgs("inspect-store", "store.sqlite"));
        assertDoesNotThrow(() -> commandLine.parseArgs("sample-messages", "store.sqlite", "--limit", "10", "--output", "sample.txt"));
        assertDoesNotThrow(() -> commandLine.parseArgs("show-message", "store.sqlite", "--id", "1", "--output", "message.txt"));
        assertDoesNotThrow(() -> commandLine.parseArgs("quality-report", "store.sqlite", "--output", "quality.txt"));
    }

    private Path createStoreWithMessages(PstIndexSummary summary, boolean withError) throws Exception {
        Path store = tempDir.resolve("store-" + System.nanoTime() + ".sqlite");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + store.toAbsolutePath())) {
            ShardStoreSchema.migrate(connection);
            connection.setAutoCommit(false);
            try (MessageStoreWriter writer = new MessageStoreWriter(connection)) {
                long runId = writer.startRun("sample.pst", 2, true);
                long folderId = writer.writeFolder(new ExtractedFolder(null, null, "/Root/Inbox", "Inbox", 10L, 2, 0));
                writer.writeMessage(mail(folderId, 100L, "RWP90H", TextRecoveryStatus.OK, longText()));
                writer.writeMessage(mail(folderId, 101L, null, TextRecoveryStatus.NULL, "body only"));
                if (withError) {
                    writer.writeError(new IndexFieldError("/Root/Inbox", 100L, "field", "subject", "TestError", "broken"));
                }
                writer.finishRun(runId, summary);
                connection.commit();
            }
        }
        return store;
    }

    private ExtractedMail mail(long folderId, long descriptorNodeId, String subject, TextRecoveryStatus subjectStatus, String body) {
        ExtractedText subjectText = new ExtractedText(subject, subjectStatus, "getter", null, null);
        ExtractedText ok = new ExtractedText("sender", TextRecoveryStatus.OK, "getter", null, null);
        ExtractedText bodyText = new ExtractedText(body, TextRecoveryStatus.OK, "getter", null, null);
        ExtractedText html = new ExtractedText("<p>" + body + "</p>", TextRecoveryStatus.OK, "getter", null, null);
        ExtractedText htmlText = new ExtractedText(body, TextRecoveryStatus.OK, "getter", null, null);
        return new ExtractedMail(
                folderId,
                "/Root/Inbox",
                descriptorNodeId,
                "message-" + descriptorNodeId,
                subjectText,
                ok,
                "sender@example.com",
                ok,
                ExtractedText.nullValue(),
                "2026-06-26T10:00:00+09:00",
                "2026-06-26T10:01:00+09:00",
                bodyText,
                html,
                htmlText,
                "OK",
                List.of()
        );
    }

    private PstIndexSummary readySummary() {
        return new PstIndexSummary(1, 2, 2, 0, 0, 0, 12, 0, 0, 0, 0, 10, "SUCCESS");
    }

    private long countStatus(List<com.example.pstarchive.inspect.StatusCount> counts, String status) {
        return counts.stream()
                .filter(count -> status.equals(count.status()))
                .mapToLong(com.example.pstarchive.inspect.StatusCount::count)
                .sum();
    }

    private String longText() {
        return "a".repeat(400);
    }
}
