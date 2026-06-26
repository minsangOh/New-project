package com.example.pstarchive;

import com.example.pstarchive.index.PstIndexOptions;
import com.example.pstarchive.index.PstIndexService;
import com.example.pstarchive.index.PstIndexSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class PstIndexIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    @EnabledIfEnvironmentVariable(named = "PST_TEST_FILE", matches = ".+")
    void indexesRealPstWhenEnvironmentVariableIsSet() throws Exception {
        Path pstPath = Path.of(System.getenv("PST_TEST_FILE"));
        assertTrue(Files.exists(pstPath), "PST_TEST_FILE must point to an existing PST file");

        Path storePath = tempDir.resolve("store.sqlite");
        PstIndexSummary summary = new PstIndexService().index(
                new PstIndexOptions(pstPath, storePath, 10, true, System.out)
        );

        assertTrue("SUCCESS".equals(summary.status()) || "PARTIAL_SUCCESS".equals(summary.status()));
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + storePath.toAbsolutePath())) {
            assertTrue(count(connection, "folders") >= 1);
            assertTrue(count(connection, "messages") >= 1);
            assertTrue(count(connection, "index_runs") >= 1);
        }
    }

    private long count(Connection connection, String tableName) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
        }
    }
}
