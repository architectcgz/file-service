package com.platform.fileservice.core.adapters.db;

import com.platform.fileservice.core.domain.exception.UploadSessionMutationException;
import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.UploadMode;
import com.platform.fileservice.core.domain.model.UploadSession;
import com.platform.fileservice.core.domain.model.UploadSessionStatus;
import com.platform.fileservice.core.ports.repository.UploadSessionRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

/**
 * JDBC-backed upload session repository reusing the legacy upload_tasks table.
 */
public final class PostgresUploadSessionRepository implements UploadSessionRepository {

    private static final String SELECT_BY_ID_SQL = """
            SELECT id, app_id, user_id, file_name, file_size, file_hash, content_type, upload_id,
                   storage_path, chunk_size, total_chunks, file_id, status, created_at, updated_at, expires_at,
                   upload_mode, access_level
            FROM upload_tasks
            WHERE id = ?
            """;

    private static final String INSERT_SQL = """
            INSERT INTO upload_tasks (
                id, app_id, user_id, upload_mode, access_level, file_name, file_size, file_hash, content_type,
                chunk_size, total_chunks, upload_id, storage_path, file_id, status, created_at, updated_at, expires_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_ACTIVE_BY_HASH_SQL = """
            SELECT id, app_id, user_id, file_name, file_size, file_hash, content_type, upload_id,
                   storage_path, chunk_size, total_chunks, file_id, status, created_at, updated_at, expires_at,
                   upload_mode, access_level
            FROM upload_tasks
            WHERE app_id = ?
              AND user_id = ?
              AND file_hash = ?
              AND status IN ('initiated', 'uploading', 'completing')
            ORDER BY updated_at DESC, created_at DESC
            LIMIT 1
            """;

    private static final String SELECT_EXPIRED_SQL = """
            SELECT id, app_id, user_id, file_name, file_size, file_hash, content_type, upload_id,
                   storage_path, chunk_size, total_chunks, file_id, status, created_at, updated_at, expires_at,
                   upload_mode, access_level
            FROM upload_tasks
            WHERE status IN ('initiated', 'uploading', 'completing')
              AND expires_at IS NOT NULL
              AND expires_at < ?
            ORDER BY expires_at ASC
            LIMIT 100
            """;

    private static final String SELECT_BY_OWNER_SQL = """
            SELECT id, app_id, user_id, file_name, file_size, file_hash, content_type, upload_id,
                   storage_path, chunk_size, total_chunks, file_id, status, created_at, updated_at, expires_at,
                   upload_mode, access_level
            FROM upload_tasks
            WHERE app_id = ?
              AND user_id = ?
            ORDER BY updated_at DESC, created_at DESC
            LIMIT ?
            """;

    private static final String UPDATE_STATUS_SQL = """
            UPDATE upload_tasks
            SET status = ?, updated_at = ?
            WHERE id = ?
              AND status NOT IN ('completed', 'aborted', 'expired', 'failed')
            """;

    private static final String COMPLETE_SQL = """
            UPDATE upload_tasks
            SET status = 'completed', file_id = ?, updated_at = ?
            WHERE id = ?
              AND status NOT IN ('completed', 'aborted', 'expired', 'failed')
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresUploadSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public Optional<UploadSession> findById(String uploadSessionId) {
        List<UploadSession> results = jdbcTemplate.query(SELECT_BY_ID_SQL, new UploadSessionRowMapper(), uploadSessionId);
        return results.stream().findFirst();
    }

    @Override
    public Optional<UploadSession> findActiveByHash(String tenantId, String ownerId, String fileHash) {
        List<UploadSession> results = jdbcTemplate.query(
                SELECT_ACTIVE_BY_HASH_SQL,
                new UploadSessionRowMapper(),
                tenantId,
                ownerId,
                fileHash
        );
        return results.stream().findFirst();
    }

    @Override
    public UploadSession save(UploadSession uploadSession) {
        try {
            int rows = jdbcTemplate.update(
                    INSERT_SQL,
                    uploadSession.uploadSessionId(),
                    uploadSession.tenantId(),
                    uploadSession.ownerId(),
                    toUploadModeValue(uploadSession.uploadMode()),
                    toAccessLevelValue(uploadSession.targetAccessLevel()),
                    uploadSession.originalFilename(),
                    uploadSession.expectedSize(),
                    uploadSession.fileHash(),
                    uploadSession.contentType(),
                    uploadSession.chunkSizeBytes(),
                    uploadSession.totalParts(),
                    uploadSession.providerUploadId(),
                    uploadSession.objectKey(),
                    uploadSession.fileId(),
                    toStatusValue(uploadSession.status()),
                    toTimestamp(uploadSession.createdAt()),
                    toTimestamp(uploadSession.updatedAt()),
                    toTimestamp(uploadSession.expiresAt())
            );
            if (rows != 1) {
                throw new UploadSessionMutationException("failed to save upload session: " + uploadSession.uploadSessionId());
            }
        } catch (DataAccessException ex) {
            throw new UploadSessionMutationException("failed to save upload session: " + uploadSession.uploadSessionId(), ex);
        }
        return uploadSession;
    }

    @Override
    public List<UploadSession> findExpiredSessions(Instant before) {
        return jdbcTemplate.query(SELECT_EXPIRED_SQL, new UploadSessionRowMapper(), Timestamp.from(before));
    }

    @Override
    public List<UploadSession> findByOwner(String tenantId, String ownerId, int limit) {
        return jdbcTemplate.query(
                SELECT_BY_OWNER_SQL,
                new UploadSessionRowMapper(),
                tenantId,
                ownerId,
                limit
        );
    }

    @Override
    public boolean markCompleted(String uploadSessionId, String fileId) {
        int rows = jdbcTemplate.update(
                COMPLETE_SQL,
                fileId,
                Timestamp.from(Instant.now()),
                uploadSessionId
        );
        return rows == 1;
    }

    @Override
    public boolean updateStatus(String uploadSessionId, UploadSessionStatus status) {
        int rows = jdbcTemplate.update(
                UPDATE_STATUS_SQL,
                toStatusValue(status),
                Timestamp.from(Instant.now()),
                uploadSessionId
        );
        return rows == 1;
    }

    private String toStatusValue(UploadSessionStatus status) {
        return status.name().toLowerCase();
    }

    private String toUploadModeValue(UploadMode uploadMode) {
        return uploadMode.name().toLowerCase();
    }

    private String toAccessLevelValue(AccessLevel accessLevel) {
        return accessLevel.name().toLowerCase();
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static final class UploadSessionRowMapper implements RowMapper<UploadSession> {

        @Override
        public UploadSession mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new UploadSession(
                    rs.getString("id"),
                    rs.getString("app_id"),
                    rs.getString("user_id"),
                    mapUploadMode(rs.getString("upload_mode")),
                    mapAccessLevel(rs.getString("access_level")),
                    rs.getString("file_name"),
                    rs.getString("content_type"),
                    rs.getLong("file_size"),
                    rs.getString("file_hash"),
                    rs.getString("storage_path"),
                    Math.toIntExact(rs.getLong("chunk_size")),
                    rs.getInt("total_chunks"),
                    rs.getString("upload_id"),
                    rs.getString("file_id"),
                    mapStatus(rs.getString("status")),
                    toInstant(rs.getTimestamp("created_at")),
                    toInstant(rs.getTimestamp("updated_at")),
                    toInstant(rs.getTimestamp("expires_at"))
            );
        }

        private UploadMode mapUploadMode(String value) {
            if (value == null || value.isBlank()) {
                return UploadMode.DIRECT;
            }
            return UploadMode.valueOf(value.trim().toUpperCase());
        }

        private AccessLevel mapAccessLevel(String value) {
            if (value == null || value.isBlank()) {
                return AccessLevel.PUBLIC;
            }
            return AccessLevel.valueOf(value.trim().toUpperCase());
        }

        private UploadSessionStatus mapStatus(String value) {
            if (value == null || value.isBlank()) {
                return UploadSessionStatus.UPLOADING;
            }
            return UploadSessionStatus.valueOf(value.trim().toUpperCase());
        }

        private Instant toInstant(Timestamp timestamp) {
            return timestamp == null ? null : timestamp.toInstant();
        }
    }
}
