package com.example.pstarchive.search.fts;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class Fts5Schema {
    public static final String TABLE_NAME = "messages_fts";

    private Fts5Schema() {
    }

    public static void ensureMessagesTableExists(Connection connection) throws SQLException {
        if (!tableExists(connection, "messages")) {
            throw new SQLException("SQLite store does not contain required messages table");
        }
    }

    public static void create(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts5(
                      subject,
                      sender_name,
                      sender_email,
                      recipients,
                      cc,
                      folder_path,
                      body_text,
                      body_html_text,
                      message_id UNINDEXED
                    )
                    """);
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase(java.util.Locale.ROOT).contains("fts5")) {
                throw new SQLException("SQLite FTS5 is not available in this runtime: " + e.getMessage(), e);
            }
            throw e;
        }
    }

    public static void clear(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM messages_fts");
        }
    }

    public static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT 1 FROM sqlite_master
                WHERE type IN ('table', 'virtual table') AND name = ?
                """)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
