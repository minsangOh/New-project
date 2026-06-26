package com.example.pstarchive.index;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class ShardStoreSchema {
    private ShardStoreSchema() {
    }

    public static void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("PRAGMA journal_mode=WAL");
            statement.executeUpdate("PRAGMA synchronous=NORMAL");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS folders (
                      id INTEGER PRIMARY KEY,
                      parent_id INTEGER,
                      folder_path TEXT NOT NULL UNIQUE,
                      display_name TEXT,
                      descriptor_node_id INTEGER,
                      item_count INTEGER,
                      subfolder_count INTEGER,
                      created_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS messages (
                      id INTEGER PRIMARY KEY,
                      folder_id INTEGER,
                      folder_path TEXT NOT NULL,
                      descriptor_node_id INTEGER,
                      internet_message_id TEXT,
                      subject TEXT,
                      subject_status TEXT,
                      subject_source TEXT,
                      sender_name TEXT,
                      sender_email TEXT,
                      recipients TEXT,
                      cc TEXT,
                      sent_at TEXT,
                      received_at TEXT,
                      body_text TEXT,
                      body_text_status TEXT,
                      body_text_source TEXT,
                      body_html TEXT,
                      body_html_status TEXT,
                      body_html_source TEXT,
                      body_html_text TEXT,
                      body_html_text_status TEXT,
                      body_text_length INTEGER,
                      body_html_length INTEGER,
                      body_html_text_length INTEGER,
                      indexed_at INTEGER NOT NULL,
                      parse_status TEXT NOT NULL,
                      UNIQUE(descriptor_node_id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS index_errors (
                      id INTEGER PRIMARY KEY,
                      folder_path TEXT,
                      descriptor_node_id INTEGER,
                      stage TEXT NOT NULL,
                      field_name TEXT,
                      error_type TEXT,
                      error_message TEXT,
                      created_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS index_runs (
                      id INTEGER PRIMARY KEY,
                      pst_path TEXT NOT NULL,
                      started_at INTEGER NOT NULL,
                      finished_at INTEGER,
                      limit_count INTEGER,
                      replace_mode INTEGER NOT NULL DEFAULT 0,
                      folders_visited INTEGER DEFAULT 0,
                      messages_seen INTEGER DEFAULT 0,
                      messages_saved INTEGER DEFAULT 0,
                      message_errors INTEGER DEFAULT 0,
                      field_errors INTEGER DEFAULT 0,
                      fatal_errors INTEGER DEFAULT 0,
                      ok_fields INTEGER DEFAULT 0,
                      degraded_fields INTEGER DEFAULT 0,
                      unrecoverable_fields INTEGER DEFAULT 0,
                      null_fields INTEGER DEFAULT 0,
                      status TEXT NOT NULL
                    )
                    """);
        }
    }

    public static void replaceData(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM messages");
            statement.executeUpdate("DELETE FROM folders");
            statement.executeUpdate("DELETE FROM index_errors");
        }
    }
}
