package com.example.pstarchive.catalog;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class CatalogSchema {
    public static final int CURRENT_VERSION = 1;

    private CatalogSchema() {
    }

    public static void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                      version INTEGER PRIMARY KEY,
                      applied_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS pst_files (
                      pst_id TEXT PRIMARY KEY,
                      display_name TEXT NOT NULL,
                      current_path TEXT NOT NULL,
                      original_path TEXT NOT NULL,
                      status TEXT NOT NULL,
                      period_from TEXT,
                      period_to TEXT,
                      split_strategy TEXT,
                      size_bytes INTEGER,
                      mtime_epoch_ms INTEGER,
                      first_1mb_sha256 TEXT,
                      last_1mb_sha256 TEXT,
                      file_fingerprint TEXT,
                      parser_version TEXT,
                      index_version TEXT,
                      created_at TEXT,
                      updated_at TEXT,
                      last_scan_at TEXT,
                      last_index_at TEXT,
                      last_verified_at TEXT
                    )
                    """);
            statement.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_pst_files_fingerprint
                    ON pst_files(file_fingerprint)
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS pst_locations (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      pst_id TEXT NOT NULL,
                      path TEXT NOT NULL,
                      seen_at TEXT NOT NULL,
                      size_bytes INTEGER,
                      mtime_epoch_ms INTEGER,
                      exists_flag INTEGER,
                      FOREIGN KEY (pst_id) REFERENCES pst_files(pst_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS shard_stats (
                      pst_id TEXT PRIMARY KEY,
                      message_count INTEGER DEFAULT 0,
                      indexed_count INTEGER DEFAULT 0,
                      error_count INTEGER DEFAULT 0,
                      min_sent_at TEXT,
                      max_sent_at TEXT,
                      lucene_size_bytes INTEGER DEFAULT 0,
                      sqlite_size_bytes INTEGER DEFAULT 0,
                      updated_at TEXT,
                      FOREIGN KEY (pst_id) REFERENCES pst_files(pst_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS index_runs (
                      run_id TEXT PRIMARY KEY,
                      pst_id TEXT,
                      mode TEXT,
                      started_at TEXT,
                      finished_at TEXT,
                      status TEXT,
                      processed_count INTEGER DEFAULT 0,
                      failed_count INTEGER DEFAULT 0,
                      heap_max_mb INTEGER,
                      FOREIGN KEY (pst_id) REFERENCES pst_files(pst_id)
                    )
                    """);
            statement.execute("""
                    INSERT OR IGNORE INTO schema_version(version, applied_at)
                    VALUES (1, datetime('now'))
                    """);
        }
    }
}
