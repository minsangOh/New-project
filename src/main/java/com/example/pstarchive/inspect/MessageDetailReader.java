package com.example.pstarchive.inspect;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class MessageDetailReader {
    public Optional<MessageDetail> findById(Path storePath, long id) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + storePath.toAbsolutePath().normalize());
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM messages WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new MessageDetail(
                        resultSet.getLong("id"),
                        nullableLong(resultSet, "folder_id"),
                        resultSet.getString("folder_path"),
                        nullableLong(resultSet, "descriptor_node_id"),
                        resultSet.getString("internet_message_id"),
                        resultSet.getString("subject"),
                        resultSet.getString("subject_status"),
                        resultSet.getString("subject_source"),
                        resultSet.getString("sender_name"),
                        resultSet.getString("sender_email"),
                        resultSet.getString("recipients"),
                        resultSet.getString("cc"),
                        resultSet.getString("sent_at"),
                        resultSet.getString("received_at"),
                        resultSet.getString("body_text"),
                        resultSet.getString("body_text_status"),
                        resultSet.getString("body_text_source"),
                        resultSet.getString("body_html"),
                        resultSet.getString("body_html_status"),
                        resultSet.getString("body_html_source"),
                        resultSet.getString("body_html_text"),
                        resultSet.getString("body_html_text_status"),
                        nullableInteger(resultSet, "body_text_length"),
                        nullableInteger(resultSet, "body_html_length"),
                        nullableInteger(resultSet, "body_html_text_length"),
                        nullableLong(resultSet, "indexed_at"),
                        resultSet.getString("parse_status")
                ));
            }
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
