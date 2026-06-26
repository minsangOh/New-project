package com.example.pstarchive;

import com.example.pstarchive.cli.ArchiveCommand;
import com.example.pstarchive.encoding.ExtractedText;
import com.example.pstarchive.encoding.TextRecoveryStatus;
import com.example.pstarchive.index.IndexFieldError;
import com.example.pstarchive.index.MessageStoreWriter;
import com.example.pstarchive.index.PstIndexSummary;
import com.example.pstarchive.index.ShardStoreSchema;
import com.example.pstarchive.pst.ExtractedFolder;
import com.example.pstarchive.pst.ExtractedMail;
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

class PstIndexStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void createsShardStoreSchema() throws Exception {
        try (Connection connection = openStore()) {
            ShardStoreSchema.migrate(connection);

            assertTrue(tableExists(connection, "folders"));
            assertTrue(tableExists(connection, "messages"));
            assertTrue(tableExists(connection, "index_errors"));
            assertTrue(tableExists(connection, "index_runs"));
        }
    }

    @Test
    void insertsFoldersMessagesErrorsAndRun() throws Exception {
        try (Connection connection = openStore()) {
            ShardStoreSchema.migrate(connection);
            connection.setAutoCommit(false);
            try (MessageStoreWriter writer = new MessageStoreWriter(connection)) {
                long runId = writer.startRun("sample.pst", 100, true);
                long folderId = writer.writeFolder(new ExtractedFolder(null, null, "/Root/Inbox", "Inbox", 10L, 1, 0));
                writer.writeMessage(sampleMail(folderId, 123L, "RWP90H"));
                writer.writeError(new IndexFieldError("/Root/Inbox", 123L, "field", "subject", "TestError", "broken"));
                writer.finishRun(runId, sampleSummary());
                connection.commit();
            }

            assertEquals(1, count(connection, "folders"));
            assertEquals(1, count(connection, "messages"));
            assertEquals(1, count(connection, "index_errors"));
            assertEquals(1, count(connection, "index_runs"));
            assertEquals("RWP90H", stringValue(connection, "SELECT subject FROM messages WHERE descriptor_node_id = 123"));
            assertEquals("DEGRADED", stringValue(connection, "SELECT body_html_status FROM messages WHERE descriptor_node_id = 123"));
        }
    }

    @Test
    void replaceModeClearsDataTables() throws Exception {
        try (Connection connection = openStore()) {
            ShardStoreSchema.migrate(connection);
            connection.setAutoCommit(false);
            try (MessageStoreWriter writer = new MessageStoreWriter(connection)) {
                long folderId = writer.writeFolder(new ExtractedFolder(null, null, "/Root/Inbox", "Inbox", 10L, 1, 0));
                writer.writeMessage(sampleMail(folderId, 123L, "before"));
                writer.writeError(new IndexFieldError("/Root/Inbox", 123L, "field", "subject", "TestError", "broken"));
                connection.commit();
            }

            ShardStoreSchema.replaceData(connection);
            connection.commit();

            assertEquals(0, count(connection, "folders"));
            assertEquals(0, count(connection, "messages"));
            assertEquals(0, count(connection, "index_errors"));
        }
    }

    @Test
    void duplicateDescriptorNodeIdUpdatesMessage() throws Exception {
        try (Connection connection = openStore()) {
            ShardStoreSchema.migrate(connection);
            connection.setAutoCommit(false);
            try (MessageStoreWriter writer = new MessageStoreWriter(connection)) {
                long folderId = writer.writeFolder(new ExtractedFolder(null, null, "/Root/Inbox", "Inbox", 10L, 1, 0));
                writer.writeMessage(sampleMail(folderId, 123L, "before"));
                writer.writeMessage(sampleMail(folderId, 123L, "after"));
                connection.commit();
            }

            assertEquals(1, count(connection, "messages"));
            assertEquals("after", stringValue(connection, "SELECT subject FROM messages WHERE descriptor_node_id = 123"));
        }
    }

    @Test
    void archiveCommandExposesIndexCommands() {
        CommandLine commandLine = new CommandLine(new ArchiveCommand());

        assertTrue(commandLine.getSubcommands().containsKey("index-file"));
        assertTrue(commandLine.getSubcommands().containsKey("index-pst"));
        assertDoesNotThrow(() -> commandLine.parseArgs("index-file", "sample.pst",
                "--out", "store.sqlite", "--limit", "10", "--replace"));
        assertDoesNotThrow(() -> commandLine.parseArgs("index-pst", "sample-id",
                "--limit", "10", "--replace"));
    }

    private Connection openStore() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve("store.sqlite").toAbsolutePath());
    }

    private ExtractedMail sampleMail(long folderId, long descriptorNodeId, String subject) {
        ExtractedText okSubject = new ExtractedText(subject, TextRecoveryStatus.OK, "getter", null, null);
        ExtractedText ok = new ExtractedText("value", TextRecoveryStatus.OK, "getter", null, null);
        ExtractedText degradedHtml = new ExtractedText("<p>value</p>", TextRecoveryStatus.DEGRADED, "MS949", null, null);
        ExtractedText htmlText = new ExtractedText("value", TextRecoveryStatus.DEGRADED, "MS949", null, null);
        return new ExtractedMail(
                folderId,
                "/Root/Inbox",
                descriptorNodeId,
                "message@example",
                okSubject,
                ok,
                "sender@example.com",
                ok,
                ExtractedText.nullValue(),
                "2026-06-26T10:00:00+09:00",
                "2026-06-26T10:01:00+09:00",
                ok,
                degradedHtml,
                htmlText,
                "OK",
                List.of()
        );
    }

    private PstIndexSummary sampleSummary() {
        return new PstIndexSummary(1, 1, 1, 0, 0, 0, 5, 2, 0, 1, 0, 10, "SUCCESS");
    }

    private boolean tableExists(Connection connection, String tableName) throws Exception {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, null, tableName, null)) {
            return resultSet.next();
        }
    }

    private long count(Connection connection, String tableName) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
        }
    }

    private String stringValue(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }
}
