package com.platform.fileservice.core.adapters.db;

import com.platform.fileservice.core.domain.exception.FileAccessDeniedException;
import com.platform.fileservice.core.domain.exception.FileAssetNotFoundException;
import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.FileAsset;
import com.platform.fileservice.core.domain.model.FileAssetStatus;
import com.platform.fileservice.core.ports.access.AuthorizedFileAccessPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 直接读取 legacy file_records 表并复用现有访问语义。
 */
public final class JdbcAuthorizedFileAccessAdapter implements AuthorizedFileAccessPort {

    private static final String SELECT_FILE_SQL = """
            SELECT id, app_id, user_id, original_name, storage_path, content_type, file_size, access_level, status, created_at, updated_at
            FROM file_records
            WHERE id = ? AND app_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuthorizedFileAccessAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public FileAsset loadAccessibleFile(String tenantId, String fileId, String subjectId) {
        List<FileAsset> results = jdbcTemplate.query(SELECT_FILE_SQL, new FileAssetRowMapper(), fileId, tenantId);
        if (results.isEmpty()) {
            throw new FileAssetNotFoundException("fileId not found: " + fileId);
        }

        FileAsset fileAsset = results.get(0);
        if (fileAsset.status() == FileAssetStatus.DELETED) {
            throw new FileAssetNotFoundException("fileId deleted: " + fileId);
        }
        if (fileAsset.accessLevel() == AccessLevel.PUBLIC) {
            return fileAsset;
        }
        if (!StringUtils.hasText(subjectId) || !subjectId.equals(fileAsset.ownerId())) {
            throw new FileAccessDeniedException("access denied for fileId: " + fileId);
        }
        return fileAsset;
    }

    private static final class FileAssetRowMapper implements RowMapper<FileAsset> {

        @Override
        public FileAsset mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new FileAsset(
                    rs.getString("id"),
                    rs.getString("app_id"),
                    rs.getString("user_id"),
                    null,
                    rs.getString("original_name"),
                    rs.getString("storage_path"),
                    rs.getString("content_type"),
                    rs.getLong("file_size"),
                    mapAccessLevel(rs.getString("access_level")),
                    mapStatus(rs.getString("status")),
                    toInstant(rs.getTimestamp("created_at")),
                    toInstant(rs.getTimestamp("updated_at"))
            );
        }

        private AccessLevel mapAccessLevel(String value) {
            String normalized = value == null ? "private" : value.trim().toUpperCase();
            return AccessLevel.valueOf(normalized);
        }

        private FileAssetStatus mapStatus(String value) {
            if (value == null) {
                return FileAssetStatus.PENDING;
            }
            return switch (value.trim().toUpperCase()) {
                case "COMPLETED" -> FileAssetStatus.ACTIVE;
                case "DELETED" -> FileAssetStatus.DELETED;
                case "BLOCKED" -> FileAssetStatus.BLOCKED;
                default -> FileAssetStatus.PENDING;
            };
        }

        private Instant toInstant(Timestamp timestamp) {
            return timestamp == null ? null : timestamp.toInstant();
        }
    }
}
