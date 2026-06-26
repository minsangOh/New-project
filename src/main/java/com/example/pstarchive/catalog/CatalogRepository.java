package com.example.pstarchive.catalog;

import com.example.pstarchive.model.PstFileRecord;
import com.example.pstarchive.model.PstFingerprint;
import com.example.pstarchive.model.PstStatus;
import com.example.pstarchive.util.TimeUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CatalogRepository {
    private final CatalogDatabase database;

    public CatalogRepository(CatalogDatabase database) {
        this.database = database;
    }

    public void insertPst(PstFileRecord record) throws SQLException {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO pst_files (
                          pst_id, display_name, current_path, original_path, status,
                          period_from, period_to, split_strategy, size_bytes, mtime_epoch_ms,
                          first_1mb_sha256, last_1mb_sha256, file_fingerprint,
                          parser_version, index_version, created_at, updated_at,
                          last_scan_at, last_index_at, last_verified_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    bindRecord(statement, record);
                    statement.executeUpdate();
                }
                insertLocation(connection, record.pstId(), record.currentPath(), record.sizeBytes(), record.mtimeEpochMs(), true);
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO shard_stats(pst_id, updated_at) VALUES (?, ?)
                        """)) {
                    statement.setString(1, record.pstId());
                    statement.setString(2, TimeUtils.nowIso());
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public List<PstFileRecord> listPsts() throws SQLException {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM pst_files ORDER BY created_at, display_name");
             ResultSet resultSet = statement.executeQuery()) {
            List<PstFileRecord> records = new ArrayList<>();
            while (resultSet.next()) {
                records.add(mapRecord(resultSet));
            }
            return records;
        }
    }

    public Optional<PstFileRecord> findById(String pstId) throws SQLException {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM pst_files WHERE pst_id = ?")) {
            statement.setString(1, pstId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRecord(resultSet));
                }
                return Optional.empty();
            }
        }
    }

    public Optional<PstFileRecord> findByFingerprint(String fingerprint) throws SQLException {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM pst_files WHERE file_fingerprint = ?")) {
            statement.setString(1, fingerprint);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRecord(resultSet));
                }
                return Optional.empty();
            }
        }
    }

    public void updateStatus(String pstId, PstStatus status) throws SQLException {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE pst_files SET status = ?, updated_at = ? WHERE pst_id = ?
                     """)) {
            statement.setString(1, status.value());
            statement.setString(2, TimeUtils.nowIso());
            statement.setString(3, pstId);
            statement.executeUpdate();
        }
    }

    public void updatePath(String pstId, String newPath, PstFingerprint fingerprint) throws SQLException {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE pst_files
                        SET current_path = ?, size_bytes = ?, mtime_epoch_ms = ?,
                            first_1mb_sha256 = ?, last_1mb_sha256 = ?, file_fingerprint = ?,
                            updated_at = ?
                        WHERE pst_id = ?
                        """)) {
                    statement.setString(1, newPath);
                    statement.setLong(2, fingerprint.fileSize());
                    statement.setLong(3, fingerprint.mtimeEpochMs());
                    statement.setString(4, fingerprint.first1MbSha256());
                    statement.setString(5, fingerprint.last1MbSha256());
                    statement.setString(6, fingerprint.fileFingerprint());
                    statement.setString(7, TimeUtils.nowIso());
                    statement.setString(8, pstId);
                    statement.executeUpdate();
                }
                insertLocation(connection, pstId, newPath, fingerprint.fileSize(), fingerprint.mtimeEpochMs(), true);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public void markVerified(String pstId, PstStatus status, boolean exists, Long sizeBytes, Long mtimeEpochMs) throws SQLException {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE pst_files
                        SET status = ?, last_verified_at = ?, updated_at = ?
                        WHERE pst_id = ?
                        """)) {
                    String now = TimeUtils.nowIso();
                    statement.setString(1, status.value());
                    statement.setString(2, now);
                    statement.setString(3, now);
                    statement.setString(4, pstId);
                    statement.executeUpdate();
                }
                PstFileRecord current = findByIdInConnection(connection, pstId).orElseThrow();
                insertLocation(connection, pstId, current.currentPath(),
                        sizeBytes == null ? current.sizeBytes() : sizeBytes,
                        mtimeEpochMs == null ? current.mtimeEpochMs() : mtimeEpochMs,
                        exists);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public Map<String, Integer> countByStatus() throws SQLException {
        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT status, COUNT(*) count FROM pst_files GROUP BY status ORDER BY status")) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            while (resultSet.next()) {
                counts.put(resultSet.getString("status"), resultSet.getInt("count"));
            }
            return counts;
        }
    }

    private Optional<PstFileRecord> findByIdInConnection(Connection connection, String pstId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM pst_files WHERE pst_id = ?")) {
            statement.setString(1, pstId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRecord(resultSet));
                }
                return Optional.empty();
            }
        }
    }

    private void insertLocation(Connection connection, String pstId, String path, long sizeBytes, long mtimeEpochMs, boolean exists) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO pst_locations(pst_id, path, seen_at, size_bytes, mtime_epoch_ms, exists_flag)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, pstId);
            statement.setString(2, path);
            statement.setString(3, TimeUtils.nowIso());
            statement.setLong(4, sizeBytes);
            statement.setLong(5, mtimeEpochMs);
            statement.setInt(6, exists ? 1 : 0);
            statement.executeUpdate();
        }
    }

    private void bindRecord(PreparedStatement statement, PstFileRecord record) throws SQLException {
        statement.setString(1, record.pstId());
        statement.setString(2, record.displayName());
        statement.setString(3, record.currentPath());
        statement.setString(4, record.originalPath());
        statement.setString(5, record.status().value());
        statement.setString(6, record.periodFrom());
        statement.setString(7, record.periodTo());
        statement.setString(8, record.splitStrategy());
        statement.setLong(9, record.sizeBytes());
        statement.setLong(10, record.mtimeEpochMs());
        statement.setString(11, record.first1MbSha256());
        statement.setString(12, record.last1MbSha256());
        statement.setString(13, record.fileFingerprint());
        statement.setString(14, record.parserVersion());
        statement.setString(15, record.indexVersion());
        statement.setString(16, record.createdAt());
        statement.setString(17, record.updatedAt());
        statement.setString(18, record.lastScanAt());
        statement.setString(19, record.lastIndexAt());
        statement.setString(20, record.lastVerifiedAt());
    }

    private PstFileRecord mapRecord(ResultSet resultSet) throws SQLException {
        return new PstFileRecord(
                resultSet.getString("pst_id"),
                resultSet.getString("display_name"),
                resultSet.getString("current_path"),
                resultSet.getString("original_path"),
                PstStatus.fromValue(resultSet.getString("status")),
                resultSet.getString("period_from"),
                resultSet.getString("period_to"),
                resultSet.getString("split_strategy"),
                resultSet.getLong("size_bytes"),
                resultSet.getLong("mtime_epoch_ms"),
                resultSet.getString("first_1mb_sha256"),
                resultSet.getString("last_1mb_sha256"),
                resultSet.getString("file_fingerprint"),
                resultSet.getString("parser_version"),
                resultSet.getString("index_version"),
                resultSet.getString("created_at"),
                resultSet.getString("updated_at"),
                resultSet.getString("last_scan_at"),
                resultSet.getString("last_index_at"),
                resultSet.getString("last_verified_at")
        );
    }
}
