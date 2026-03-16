package com.platform.fileservice.core.adapters.db;

import com.platform.fileservice.core.domain.exception.FileAccessMutationException;
import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.FileAsset;
import com.platform.fileservice.core.domain.model.FileAssetStatus;
import com.platform.fileservice.core.ports.repository.FileAssetRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * PostgreSQL-backed file asset repository reusing the legacy file_records table.
 */
public final class PostgresFileAssetRepository implements FileAssetRepository {

    private static final String SELECT_BY_ID_SQL = """
            SELECT id, app_id, user_id, storage_object_id, original_name, storage_path, content_type,
                   file_size, access_level, status, created_at, updated_at
            FROM file_records
            WHERE id = ?
            """;

    private static final String INSERT_SQL = """
            INSERT INTO file_records (
                id, app_id, user_id, storage_object_id, original_name, storage_path, file_size,
                content_type, access_level, status, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresFileAssetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public Optional<FileAsset> findById(String fileId) {
        List<FileAsset> results = jdbcTemplate.query(SELECT_BY_ID_SQL, new FileAssetRowMapper(), fileId);
        return results.stream().findFirst();
    }

    @Override
    public FileAsset save(FileAsset fileAsset) {
        try {
            int rows = jdbcTemplate.update(
                    INSERT_SQL,
                    fileAsset.fileId(),
                    fileAsset.tenantId(),
                    fileAsset.ownerId(),
                    fileAsset.blobObjectId(),
                    fileAsset.originalFilename(),
                    fileAsset.objectKey(),
                    fileAsset.fileSize(),
                    fileAsset.contentType(),
                    toAccessLevelValue(fileAsset.accessLevel()),
                    toStatusValue(fileAsset.status()),
                    toTimestamp(fileAsset.createdAt()),
                    toTimestamp(fileAsset.updatedAt())
            );
            if (rows != 1) {
                throw new FileAccessMutationException("failed to save file asset: " + fileAsset.fileId());
            }
        } catch (DataAccessException ex) {
            throw new FileAccessMutationException("failed to save file asset: " + fileAsset.fileId(), ex);
        }
        return fileAsset;
    }

    private String toAccessLevelValue(AccessLevel accessLevel) {
        return accessLevel.name().toLowerCase();
    }

    private String toStatusValue(FileAssetStatus status) {
        return switch (status) {
            case ACTIVE -> "completed";
            case DELETED -> "deleted";
            case BLOCKED -> "blocked";
            case PENDING -> "pending";
        };
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static final class FileAssetRowMapper implements RowMapper<FileAsset> {

        @Override
        public FileAsset mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new FileAsset(
                    rs.getString("id"),
                    rs.getString("app_id"),
                    rs.getString("user_id"),
                    rs.getString("storage_object_id"),
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
