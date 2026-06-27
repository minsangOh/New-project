package com.example.pstarchive;

import com.example.pstarchive.cli.ArchiveCommand;
import com.example.pstarchive.encoding.ExtractedText;
import com.example.pstarchive.encoding.TextRecoveryStatus;
import com.example.pstarchive.index.MessageStoreWriter;
import com.example.pstarchive.index.PstIndexSummary;
import com.example.pstarchive.index.ShardStoreSchema;
import com.example.pstarchive.pst.ExtractedFolder;
import com.example.pstarchive.pst.ExtractedMail;
import com.example.pstarchive.search.fts.Fts5IndexBuilder;
import com.example.pstarchive.search.fts.Fts5IndexSummary;
import com.example.pstarchive.search.fts.Fts5Schema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Fts5IndexBuilderTest {
    private static final String SAMSUNG = "\uC0BC\uC131\uC804\uC790";
    private static final String WATER_PURIFIER = "\uC5BC\uC74C\uC815\uC218\uAE30";
    private static final String TAPING = "\uD14C\uC774\uD551";

    @TempDir
    Path tempDir;

    @Test
    void buildsFts5RowsFromMessagesAndKeepsMessageRowId() throws Exception {
        Path store = createStore();

        Fts5IndexSummary summary = new Fts5IndexBuilder().build(store, true);

        assertEquals("SUCCESS", summary.status());
        assertEquals(2, summary.messagesRead());
        assertEquals(2, summary.rowsIndexed());
        assertEquals(0, summary.rowErrors());
        try (Connection connection = open(store)) {
            assertTrue(Fts5Schema.tableExists(connection, "messages_fts"));
            assertEquals(2, count(connection, "messages_fts"));
            assertEquals(1, longValue(connection, "SELECT message_id FROM messages_fts WHERE rowid = 1"));
            assertEquals(1, countQuery(connection, "SELECT COUNT(*) FROM messages_fts WHERE messages_fts MATCH 'RWP90H'"));
            assertTrue(stringValue(connection, "SELECT body_text FROM messages_fts WHERE rowid = 1").contains("DA96-01767C"));
            assertTrue(stringValue(connection, "SELECT body_text FROM messages_fts WHERE rowid = 2").contains(SAMSUNG));
            assertTrue(stringValue(connection, "SELECT body_text FROM messages_fts WHERE rowid = 2").contains(WATER_PURIFIER));
            assertTrue(stringValue(connection, "SELECT body_text FROM messages_fts WHERE rowid = 2").contains(TAPING));
            assertTrue(stringValue(connection, "SELECT body_text FROM messages_fts WHERE rowid = 2").contains("1015#18"));
        }
    }

    @Test
    void replaceClearsExistingFtsRowsBeforeRebuild() throws Exception {
        Path store = createStore();
        Fts5IndexBuilder builder = new Fts5IndexBuilder();
        builder.build(store, true);
        try (Connection connection = open(store); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO messages_fts(rowid, message_id, subject) VALUES (999, 999, 'stale')");
            assertEquals(3, count(connection, "messages_fts"));
        }

        Fts5IndexSummary summary = builder.build(store, true);

        assertEquals("SUCCESS", summary.status());
        try (Connection connection = open(store)) {
            assertEquals(2, count(connection, "messages_fts"));
            assertEquals(0, countQuery(connection, "SELECT COUNT(*) FROM messages_fts WHERE rowid = 999"));
        }
    }

    @Test
    void archiveCommandExposesBuildSearchIndex() {
        CommandLine commandLine = new CommandLine(new ArchiveCommand());

        assertTrue(commandLine.getSubcommands().containsKey("build-search-index"));
        assertDoesNotThrow(() -> commandLine.parseArgs("build-search-index", "store.sqlite", "--replace"));
    }

    private Path createStore() throws Exception {
        Path store = tempDir.resolve("fts-store.sqlite");
        try (Connection connection = open(store)) {
            ShardStoreSchema.migrate(connection);
            connection.setAutoCommit(false);
            try (MessageStoreWriter writer = new MessageStoreWriter(connection)) {
                long runId = writer.startRun("sample.pst", 10, true);
                long folderId = writer.writeFolder(new ExtractedFolder(null, null, "/Root/Inbox", "Inbox", 10L, 1, 0));
                writer.writeMessage(mail(folderId, 100L,
                        "RWP90H harness request",
                        "sender@example.com",
                        "DA96-01767C body RWP90H"));
                writer.writeMessage(mail(folderId, 101L,
                        SAMSUNG + " " + WATER_PURIFIER,
                        "quality@example.com",
                        SAMSUNG + " " + WATER_PURIFIER + " " + TAPING + " 1015#18"));
                writer.finishRun(runId, new PstIndexSummary(1, 2, 2, 0, 0, 0, 14, 0, 0, 0, 0, 10, "SUCCESS"));
                connection.commit();
            }
        }
        return store;
    }

    private ExtractedMail mail(long folderId, long descriptorNodeId, String subject, String senderEmail, String body) {
        return new ExtractedMail(
                folderId,
                "/Root/Inbox",
                descriptorNodeId,
                "message-" + descriptorNodeId,
                text(subject),
                text("Sender"),
                senderEmail,
                text("me@example.com"),
                text("cc@example.com"),
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

    private Connection open(Path store) throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + store.toAbsolutePath());
    }

    private long count(Connection connection, String table) throws Exception {
        return countQuery(connection, "SELECT COUNT(*) FROM " + table);
    }

    private long countQuery(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
        }
    }

    private long longValue(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
        }
    }

    private String stringValue(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }
}
