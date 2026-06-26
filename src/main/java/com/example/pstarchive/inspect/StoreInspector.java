package com.example.pstarchive.inspect;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StoreInspector {
    public StoreInspectionResult inspect(Path storePath) throws Exception {
        Path normalized = storePath.toAbsolutePath().normalize();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + normalized)) {
            return new StoreInspectionResult(
                    normalized,
                    Files.size(normalized),
                    count(connection, "folders"),
                    count(connection, "messages"),
                    count(connection, "index_runs"),
                    count(connection, "index_errors"),
                    latestRun(connection),
                    statusCounts(connection, "parse_status"),
                    statusCounts(connection, "subject_status"),
                    statusCounts(connection, "body_text_status"),
                    statusCounts(connection, "body_html_status"),
                    statusCounts(connection, "body_html_text_status"),
                    nullCount(connection, "subject"),
                    nullCount(connection, "body_text"),
                    nullCount(connection, "body_html"),
                    nullCount(connection, "body_html_text"),
                    average(connection, "body_text_length"),
                    average(connection, "body_html_text_length"),
                    stringScalar(connection, "SELECT MIN(sent_at) FROM messages WHERE sent_at IS NOT NULL").orElse("<null>"),
                    stringScalar(connection, "SELECT MAX(sent_at) FROM messages WHERE sent_at IS NOT NULL").orElse("<null>"),
                    stringScalar(connection, "SELECT MIN(received_at) FROM messages WHERE received_at IS NOT NULL").orElse("<null>"),
                    stringScalar(connection, "SELECT MAX(received_at) FROM messages WHERE received_at IS NOT NULL").orElse("<null>")
            );
        }
    }

    public long count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
        }
    }

    private List<StatusCount> statusCounts(Connection connection, String column) throws SQLException {
        List<StatusCount> counts = new ArrayList<>();
        String sql = "SELECT COALESCE(" + column + ", 'NULL') AS status, COUNT(*) AS count "
                + "FROM messages GROUP BY COALESCE(" + column + ", 'NULL') ORDER BY count DESC, status";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                counts.add(new StatusCount(resultSet.getString("status"), resultSet.getLong("count")));
            }
        }
        return counts;
    }

    private long nullCount(Connection connection, String column) throws SQLException {
        String sql = "SELECT COUNT(*) FROM messages WHERE " + column + " IS NULL OR " + column + " = ''";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
        }
    }

    private double average(Connection connection, String column) throws SQLException {
        String sql = "SELECT AVG(" + column + ") FROM messages WHERE " + column + " IS NOT NULL";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getDouble(1) : 0.0;
        }
    }

    private Optional<String> stringScalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (resultSet.next()) {
                return Optional.ofNullable(resultSet.getString(1));
            }
            return Optional.empty();
        }
    }

    private Optional<LatestIndexRun> latestRun(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, pst_path, started_at, finished_at, limit_count, replace_mode,
                       folders_visited, messages_seen, messages_saved, message_errors,
                       field_errors, fatal_errors, ok_fields, degraded_fields,
                       unrecoverable_fields, null_fields, status
                FROM index_runs ORDER BY id DESC LIMIT 1
                """);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return Optional.empty();
            }
            return Optional.of(new LatestIndexRun(
                    resultSet.getLong("id"),
                    resultSet.getString("pst_path"),
                    nullableLong(resultSet, "started_at"),
                    nullableLong(resultSet, "finished_at"),
                    nullableInteger(resultSet, "limit_count"),
                    resultSet.getInt("replace_mode") == 1,
                    resultSet.getLong("folders_visited"),
                    resultSet.getLong("messages_seen"),
                    resultSet.getLong("messages_saved"),
                    resultSet.getLong("message_errors"),
                    resultSet.getLong("field_errors"),
                    resultSet.getLong("fatal_errors"),
                    resultSet.getLong("ok_fields"),
                    resultSet.getLong("degraded_fields"),
                    resultSet.getLong("unrecoverable_fields"),
                    resultSet.getLong("null_fields"),
                    resultSet.getString("status")
            ));
        }
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }
}
