package com.example.pstarchive.index;

import com.example.pstarchive.encoding.ExtractedText;
import com.example.pstarchive.pst.ExtractedFolder;
import com.example.pstarchive.pst.ExtractedMail;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

public class MessageStoreWriter implements AutoCloseable {
    private final Connection connection;
    private final PreparedStatement upsertFolder;
    private final PreparedStatement selectFolderId;
    private final PreparedStatement upsertMessage;
    private final PreparedStatement insertError;

    public MessageStoreWriter(Connection connection) throws SQLException {
        this.connection = connection;
        this.upsertFolder = connection.prepareStatement("""
                INSERT INTO folders (
                  parent_id, folder_path, display_name, descriptor_node_id,
                  item_count, subfolder_count, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(folder_path) DO UPDATE SET
                  parent_id = excluded.parent_id,
                  display_name = excluded.display_name,
                  descriptor_node_id = excluded.descriptor_node_id,
                  item_count = excluded.item_count,
                  subfolder_count = excluded.subfolder_count
                """);
        this.selectFolderId = connection.prepareStatement("SELECT id FROM folders WHERE folder_path = ?");
        this.upsertMessage = connection.prepareStatement("""
                INSERT INTO messages (
                  folder_id, folder_path, descriptor_node_id, internet_message_id,
                  subject, subject_status, subject_source,
                  sender_name, sender_email, recipients, cc,
                  sent_at, received_at,
                  body_text, body_text_status, body_text_source,
                  body_html, body_html_status, body_html_source,
                  body_html_text, body_html_text_status,
                  body_text_length, body_html_length, body_html_text_length,
                  indexed_at, parse_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(descriptor_node_id) DO UPDATE SET
                  folder_id = excluded.folder_id,
                  folder_path = excluded.folder_path,
                  internet_message_id = excluded.internet_message_id,
                  subject = excluded.subject,
                  subject_status = excluded.subject_status,
                  subject_source = excluded.subject_source,
                  sender_name = excluded.sender_name,
                  sender_email = excluded.sender_email,
                  recipients = excluded.recipients,
                  cc = excluded.cc,
                  sent_at = excluded.sent_at,
                  received_at = excluded.received_at,
                  body_text = excluded.body_text,
                  body_text_status = excluded.body_text_status,
                  body_text_source = excluded.body_text_source,
                  body_html = excluded.body_html,
                  body_html_status = excluded.body_html_status,
                  body_html_source = excluded.body_html_source,
                  body_html_text = excluded.body_html_text,
                  body_html_text_status = excluded.body_html_text_status,
                  body_text_length = excluded.body_text_length,
                  body_html_length = excluded.body_html_length,
                  body_html_text_length = excluded.body_html_text_length,
                  indexed_at = excluded.indexed_at,
                  parse_status = excluded.parse_status
                """);
        this.insertError = connection.prepareStatement("""
                INSERT INTO index_errors (
                  folder_path, descriptor_node_id, stage, field_name,
                  error_type, error_message, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """);
    }

    public long writeFolder(ExtractedFolder folder) throws SQLException {
        bindNullableLong(upsertFolder, 1, folder.parentId());
        upsertFolder.setString(2, folder.folderPath());
        upsertFolder.setString(3, folder.displayName());
        bindNullableLong(upsertFolder, 4, folder.descriptorNodeId());
        bindNullableInteger(upsertFolder, 5, folder.itemCount());
        bindNullableInteger(upsertFolder, 6, folder.subfolderCount());
        upsertFolder.setLong(7, System.currentTimeMillis());
        upsertFolder.executeUpdate();

        selectFolderId.setString(1, folder.folderPath());
        try (ResultSet resultSet = selectFolderId.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getLong(1);
            }
        }
        throw new SQLException("Folder id was not found after upsert: " + folder.folderPath());
    }

    public int writeMessage(ExtractedMail mail) throws SQLException {
        bindNullableLong(upsertMessage, 1, mail.folderId());
        upsertMessage.setString(2, mail.folderPath());
        bindNullableLong(upsertMessage, 3, mail.descriptorNodeId());
        upsertMessage.setString(4, mail.internetMessageId());
        bindText(upsertMessage, 5, 6, 7, mail.subject());
        upsertMessage.setString(8, text(mail.senderName()));
        upsertMessage.setString(9, mail.senderEmail());
        upsertMessage.setString(10, text(mail.recipients()));
        upsertMessage.setString(11, text(mail.cc()));
        upsertMessage.setString(12, mail.sentAt());
        upsertMessage.setString(13, mail.receivedAt());
        bindText(upsertMessage, 14, 15, 16, mail.bodyText());
        bindText(upsertMessage, 17, 18, 19, mail.bodyHtml());
        upsertMessage.setString(20, text(mail.bodyHtmlText()));
        upsertMessage.setString(21, status(mail.bodyHtmlText()));
        upsertMessage.setInt(22, mail.bodyText().length());
        upsertMessage.setInt(23, mail.bodyHtml().length());
        upsertMessage.setInt(24, mail.bodyHtmlText().length());
        upsertMessage.setLong(25, System.currentTimeMillis());
        upsertMessage.setString(26, mail.parseStatus());
        return upsertMessage.executeUpdate();
    }

    public void writeError(IndexFieldError error) throws SQLException {
        insertError.setString(1, error.folderPath());
        bindNullableLong(insertError, 2, error.descriptorNodeId());
        insertError.setString(3, error.stage());
        insertError.setString(4, error.fieldName());
        insertError.setString(5, error.errorType());
        insertError.setString(6, error.errorMessage());
        insertError.setLong(7, System.currentTimeMillis());
        insertError.executeUpdate();
    }

    public long startRun(String pstPath, int limit, boolean replace) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO index_runs (
                  pst_path, started_at, limit_count, replace_mode, status
                ) VALUES (?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, pstPath);
            statement.setLong(2, System.currentTimeMillis());
            statement.setInt(3, limit);
            statement.setInt(4, replace ? 1 : 0);
            statement.setString(5, "RUNNING");
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT last_insert_rowid()")) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
        }
    }

    public void finishRun(long runId, PstIndexSummary summary) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE index_runs SET
                  finished_at = ?,
                  folders_visited = ?,
                  messages_seen = ?,
                  messages_saved = ?,
                  message_errors = ?,
                  field_errors = ?,
                  fatal_errors = ?,
                  ok_fields = ?,
                  degraded_fields = ?,
                  unrecoverable_fields = ?,
                  null_fields = ?,
                  status = ?
                WHERE id = ?
                """)) {
            statement.setLong(1, System.currentTimeMillis());
            statement.setLong(2, summary.foldersVisited());
            statement.setLong(3, summary.messagesSeen());
            statement.setLong(4, summary.messagesSaved());
            statement.setLong(5, summary.messageErrors());
            statement.setLong(6, summary.fieldErrors());
            statement.setLong(7, summary.fatalErrors());
            statement.setLong(8, summary.okFields());
            statement.setLong(9, summary.degradedFields());
            statement.setLong(10, summary.unrecoverableFields());
            statement.setLong(11, summary.nullFields());
            statement.setString(12, summary.status());
            statement.setLong(13, runId);
            statement.executeUpdate();
        }
    }

    public void commit() throws SQLException {
        connection.commit();
    }

    private void bindText(PreparedStatement statement, int textIndex, int statusIndex, int sourceIndex, ExtractedText text) throws SQLException {
        statement.setString(textIndex, text(text));
        statement.setString(statusIndex, status(text));
        statement.setString(sourceIndex, text.source());
    }

    private String text(ExtractedText text) {
        return text == null ? null : text.text();
    }

    private String status(ExtractedText text) {
        return text == null || text.status() == null ? "NULL" : text.status().value();
    }

    private void bindNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setLong(index, value);
        }
    }

    private void bindNullableInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    @Override
    public void close() throws SQLException {
        upsertFolder.close();
        selectFolderId.close();
        upsertMessage.close();
        insertError.close();
    }
}
