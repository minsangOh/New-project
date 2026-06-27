package com.example.pstarchive.search.fts;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Fts5IndexBuilder {
    private static final int COMMIT_BATCH_SIZE = 500;

    public Fts5IndexSummary build(Path storePath, boolean replace) throws SQLException {
        long started = System.currentTimeMillis();
        long messagesRead = 0;
        long rowsIndexed = 0;
        long rowErrors = 0;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + storePath.toAbsolutePath().normalize())) {
            Fts5Schema.ensureMessagesTableExists(connection);
            Fts5Schema.create(connection);
            connection.setAutoCommit(false);
            if (replace) {
                Fts5Schema.clear(connection);
                connection.commit();
            }
            try (PreparedStatement select = connection.prepareStatement("""
                         SELECT id, subject, sender_name, sender_email, recipients, cc, folder_path, body_text, body_html_text
                         FROM messages
                         ORDER BY id
                         """);
                 PreparedStatement insert = connection.prepareStatement("""
                         INSERT OR REPLACE INTO messages_fts(
                           rowid, message_id, subject, sender_name, sender_email, recipients, cc, folder_path, body_text, body_html_text
                         ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                         """)) {
                try (ResultSet resultSet = select.executeQuery()) {
                    while (resultSet.next()) {
                        messagesRead++;
                        try {
                            bindInsert(insert, resultSet);
                            insert.executeUpdate();
                            rowsIndexed++;
                        } catch (SQLException e) {
                            rowErrors++;
                        }
                        if ((messagesRead % COMMIT_BATCH_SIZE) == 0) {
                            connection.commit();
                        }
                    }
                }
            }
            connection.commit();
        }
        String status;
        if (messagesRead == 0) {
            status = "EMPTY";
        } else if (rowErrors > 0 && rowsIndexed == 0) {
            status = "FAILED";
        } else if (rowErrors > 0) {
            status = "PARTIAL_SUCCESS";
        } else {
            status = "SUCCESS";
        }
        return new Fts5IndexSummary(messagesRead, rowsIndexed, rowErrors, System.currentTimeMillis() - started, status);
    }

    private void bindInsert(PreparedStatement insert, ResultSet resultSet) throws SQLException {
        long messageId = resultSet.getLong("id");
        insert.setLong(1, messageId);
        insert.setLong(2, messageId);
        insert.setString(3, resultSet.getString("subject"));
        insert.setString(4, resultSet.getString("sender_name"));
        insert.setString(5, resultSet.getString("sender_email"));
        insert.setString(6, resultSet.getString("recipients"));
        insert.setString(7, resultSet.getString("cc"));
        insert.setString(8, resultSet.getString("folder_path"));
        insert.setString(9, resultSet.getString("body_text"));
        insert.setString(10, resultSet.getString("body_html_text"));
    }
}
