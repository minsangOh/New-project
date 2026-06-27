package com.example.pstarchive;

import com.example.pstarchive.cli.ArchiveCommand;
import com.example.pstarchive.encoding.ExtractedText;
import com.example.pstarchive.encoding.TextRecoveryStatus;
import com.example.pstarchive.index.MessageStoreWriter;
import com.example.pstarchive.index.PstIndexSummary;
import com.example.pstarchive.index.ShardStoreSchema;
import com.example.pstarchive.pst.ExtractedFolder;
import com.example.pstarchive.pst.ExtractedMail;
import com.example.pstarchive.search.compare.CompareSearchReport;
import com.example.pstarchive.search.compare.CompareSearchService;
import com.example.pstarchive.search.fts.Fts5IndexBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompareSearchEnginesTest {
    private static final String SAMSUNG = "\uC0BC\uC131\uC804\uC790";

    @TempDir
    Path tempDir;

    @Test
    void archiveCommandExposesCompareSearchEngines() {
        CommandLine commandLine = new CommandLine(new ArchiveCommand());

        assertTrue(commandLine.getSubcommands().containsKey("compare-search-engines"));
        assertDoesNotThrow(() -> commandLine.parseArgs("compare-search-engines", "store.sqlite", "RWP90H",
                "--limit", "20", "--field", "subject", "--include-broken", "--output", "compare.txt"));
    }

    @Test
    void reportsLikeOnlyMessagesWhenFts5MissesCandidate() throws Exception {
        Path store = createStore();
        new Fts5IndexBuilder().build(store, true);
        deleteFtsRow(store, 1);

        CompareSearchReport report = new CompareSearchService().compare(store, "RWP90H", "all", 20, false);

        assertEquals(2, report.likeVerifiedMessages());
        assertEquals(1, report.fts5VerifiedMessages());
        assertEquals(1, report.commonVerifiedMessages());
        assertEquals(1, report.likeOnlyVerifiedMessages());
        assertEquals(0, report.fts5OnlyVerifiedMessages());
        assertEquals(1, report.likeOnlyMessages().get(0).messageId());
        assertTrue(report.likeOnlyMessages().get(0).matchedFields().contains("subject"));
        assertTrue(report.likeOnlyMessages().get(0).matchPolicies().contains("EXACT"));
        assertTrue(report.likeOnlyMessages().get(0).preview().length() <= 123);
    }

    @Test
    void reportsZeroLikeOnlyWhenVerifiedSetsMatch() throws Exception {
        Path store = createStore();
        new Fts5IndexBuilder().build(store, true);

        CompareSearchReport report = new CompareSearchService().compare(store, "RWP90H", "all", 20, false);

        assertEquals(2, report.likeVerifiedMessages());
        assertEquals(2, report.fts5VerifiedMessages());
        assertEquals(2, report.commonVerifiedMessages());
        assertEquals(0, report.likeOnlyVerifiedMessages());
        assertEquals(0, report.fts5OnlyVerifiedMessages());
    }

    @Test
    void fieldSubjectIsApplied() throws Exception {
        Path store = createStore();
        new Fts5IndexBuilder().build(store, true);
        deleteFtsRow(store, 1);

        CompareSearchReport report = new CompareSearchService().compare(store, "RWP90H", "subject", 20, false);

        assertEquals("subject", report.field());
        assertEquals(1, report.likeVerifiedMessages());
        assertEquals(0, report.fts5VerifiedMessages());
        assertEquals(1, report.likeOnlyVerifiedMessages());
        assertTrue(report.likeOnlyMessages().get(0).matchedFields().contains("subject"));
    }

    @Test
    void outputFileIsUtf8AndContainsDiagnosticSections() throws Exception {
        Path store = createStore();
        new Fts5IndexBuilder().build(store, true);
        deleteFtsRow(store, 1);
        Path output = tempDir.resolve("compare-output.txt");

        int exit = new CommandLine(new ArchiveCommand()).execute("compare-search-engines", store.toString(), "RWP90H",
                "--limit", "20", "--output", output.toString());

        assertEquals(0, exit);
        byte[] bytes = Files.readAllBytes(output);
        assertEquals((byte) 0xEF, bytes[0]);
        String text = Files.readString(output, StandardCharsets.UTF_8);
        assertTrue(text.contains("COMPARE SEARCH ENGINES"));
        assertTrue(text.contains("LIKE ONLY VERIFIED MESSAGES"));
        assertTrue(text.contains("messageId: 1"));
        assertTrue(text.contains("FTS5 missed verified messages. Keep LIKE as default."));
        assertFalse(text.contains("MATCHES"));
    }

    private Path createStore() throws Exception {
        Path store = tempDir.resolve("compare-store.sqlite");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + store.toAbsolutePath())) {
            ShardStoreSchema.migrate(connection);
            connection.setAutoCommit(false);
            try (MessageStoreWriter writer = new MessageStoreWriter(connection)) {
                long runId = writer.startRun("sample.pst", 10, true);
                long folderId = writer.writeFolder(new ExtractedFolder(null, null, "/Root/Inbox", "Inbox", 10L, 1, 0));
                writer.writeMessage(mail(folderId, 1L,
                        "RWP90H subject " + SAMSUNG,
                        "body without repeated query"));
                writer.writeMessage(mail(folderId, 2L,
                        "Different subject",
                        "body RWP90H DA96-01767C"));
                writer.finishRun(runId, new PstIndexSummary(1, 2, 2, 0, 0, 0, 14, 0, 0, 0, 0, 10, "SUCCESS"));
                connection.commit();
            }
        }
        return store;
    }

    private void deleteFtsRow(Path store, long rowId) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + store.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM messages_fts WHERE rowid = " + rowId);
        }
    }

    private ExtractedMail mail(long folderId, long descriptorNodeId, String subject, String body) {
        return new ExtractedMail(
                folderId,
                "/Root/Inbox",
                descriptorNodeId,
                "message-" + descriptorNodeId,
                text(subject),
                text("Sender"),
                "sender@example.com",
                text("me@example.com"),
                ExtractedText.nullValue(),
                "2026-06-18T14:22:00+09:00",
                "2026-06-18T14:23:00+09:00",
                text(body),
                text("<html><body>" + body + "</body></html>"),
                text(body),
                "OK",
                List.of()
        );
    }

    private ExtractedText text(String value) {
        return new ExtractedText(value, TextRecoveryStatus.OK, "fixture", null, null);
    }
}
