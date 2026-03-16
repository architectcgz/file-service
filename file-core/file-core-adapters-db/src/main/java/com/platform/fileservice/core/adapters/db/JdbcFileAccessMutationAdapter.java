package com.platform.fileservice.core.adapters.db;

import com.platform.fileservice.core.domain.exception.FileAccessMutationException;
import com.platform.fileservice.core.domain.exception.FileAssetNotFoundException;
import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.BlobObject;
import com.platform.fileservice.core.domain.model.FileAccessMutationContext;
import com.platform.fileservice.core.domain.model.FileAsset;
import com.platform.fileservice.core.domain.model.FileAssetStatus;
import com.platform.fileservice.core.ports.access.FileAccessMutationPort;
import org.springframework.dao.DataAccessException;
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
 * 直接基于 legacy file_records/storage_objects 表实现访问策略写模型。
 */
public final class JdbcFileAccessMutationAdapter implements FileAccessMutationPort {

    private static final String SELECT_FOR_CHANGE_SQL = """
            SELECT fr.id,
                   fr.app_id,
                   fr.user_id,
                   fr.storage_object_id,
                   fr.original_name,
                   fr.file_size AS file_size,
                   fr.content_type AS file_content_type,
                   fr.access_level,
                   fr.status,
                   fr.created_at AS file_created_at,
                   fr.updated_at AS file_updated_at,
                   so.id AS blob_object_id,
                   so.app_id AS blob_app_id,
                   so.storage_path AS blob_object_key,
                   so.bucket_name,
                   so.file_hash,
                   so.hash_algorithm,
                   so.file_size AS blob_file_size,
                   so.content_type AS blob_content_type,
                   so.reference_count,
                   so.created_at AS blob_created_at,
                   so.updated_at AS blob_updated_at
            FROM file_records fr
            LEFT JOIN storage_objects so ON so.id = fr.storage_object_id
            WHERE fr.id = ? AND fr.app_id = ?
            """;

    private static final String UPDATE_ACCESS_LEVEL_SQL = """
            UPDATE file_records
            SET access_level = ?, updated_at = ?
            WHERE id = ?
            """;

    private static final String INSERT_BLOB_OBJECT_SQL = """
            INSERT INTO storage_objects (
                id, app_id, file_hash, hash_algorithm, storage_path, bucket_name,
                file_size, content_type, reference_count, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String REBIND_BLOB_AND_ACCESS_LEVEL_SQL = """
            UPDATE file_records
            SET storage_object_id = ?, storage_path = ?, access_level = ?, updated_at = ?
            WHERE id = ?
            """;

    private static final String DECREMENT_BLOB_REFERENCE_SQL = """
            UPDATE storage_objects
            SET reference_count = reference_count - 1, updated_at = ?
            WHERE id = ? AND reference_count > 0
            """;

    private static final String LEGACY_STORAGE_PROVIDER = "legacy-s3";

    private final JdbcTemplate jdbcTemplate;

    public JdbcFileAccessMutationAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public FileAccessMutationContext loadForChange(String tenantId, String fileId) {
        List<FileAccessMutationContext> results = jdbcTemplate.query(
                SELECT_FOR_CHANGE_SQL,
                new FileAccessMutationContextRowMapper(),
                fileId,
                tenantId
        );
        if (results.isEmpty()) {
            throw new FileAssetNotFoundException("fileId not found: " + fileId);
        }
        return results.get(0);
    }

    @Override
    public void updateAccessLevel(String fileId, AccessLevel accessLevel) {
        Instant now = Instant.now();
        int rows = jdbcTemplate.update(
                UPDATE_ACCESS_LEVEL_SQL,
                accessLevel.name().toLowerCase(),
                Timestamp.from(now),
                fileId
        );
        assertSingleRow(rows, "failed to update access level for fileId: " + fileId);
    }

    @Override
    public void createBlobObject(BlobObject blobObject) {
        try {
            int rows = jdbcTemplate.update(
                    INSERT_BLOB_OBJECT_SQL,
                    blobObject.blobObjectId(),
                    blobObject.tenantId(),
                    blobObject.hashValue(),
                    blobObject.hashAlgorithm(),
                    blobObject.objectKey(),
                    blobObject.bucketName(),
                    blobObject.fileSize(),
                    blobObject.contentType(),
                    blobObject.referenceCount(),
                    toTimestamp(blobObject.createdAt()),
                    toTimestamp(blobObject.updatedAt())
            );
            assertSingleRow(rows, "failed to insert blob object: " + blobObject.blobObjectId());
        } catch (DataAccessException ex) {
            throw new FileAccessMutationException("failed to insert blob object: " + blobObject.blobObjectId(), ex);
        }
    }

    @Override
    public void rebindBlobAndAccessLevel(String fileId, BlobObject blobObject, AccessLevel accessLevel) {
        int rows = jdbcTemplate.update(
                REBIND_BLOB_AND_ACCESS_LEVEL_SQL,
                blobObject.blobObjectId(),
                blobObject.objectKey(),
                accessLevel.name().toLowerCase(),
                Timestamp.from(Instant.now()),
                fileId
        );
        assertSingleRow(rows, "failed to rebind blob object for fileId: " + fileId);
    }

    @Override
    public void decrementBlobReference(String blobObjectId) {
        int rows = jdbcTemplate.update(
                DECREMENT_BLOB_REFERENCE_SQL,
                Timestamp.from(Instant.now()),
                blobObjectId
        );
        assertSingleRow(rows, "failed to decrement blob reference: " + blobObjectId);
    }

    private void assertSingleRow(int rows, String message) {
        if (rows != 1) {
            throw new FileAccessMutationException(message);
        }
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static final class FileAccessMutationContextRowMapper implements RowMapper<FileAccessMutationContext> {

        @Override
        public FileAccessMutationContext mapRow(ResultSet rs, int rowNum) throws SQLException {
            FileAsset fileAsset = new FileAsset(
                    rs.getString("id"),
                    rs.getString("app_id"),
                    rs.getString("user_id"),
                    rs.getString("storage_object_id"),
                    rs.getString("original_name"),
                    rs.getString("blob_object_key"),
                    rs.getString("file_content_type"),
                    rs.getLong("file_size"),
                    mapAccessLevel(rs.getString("access_level")),
                    mapStatus(rs.getString("status")),
                    toInstant(rs.getTimestamp("file_created_at")),
                    toInstant(rs.getTimestamp("file_updated_at"))
            );

            if (!StringUtils.hasText(rs.getString("blob_object_id"))) {
                return new FileAccessMutationContext(fileAsset, null);
            }

            BlobObject blobObject = new BlobObject(
                    rs.getString("blob_object_id"),
                    rs.getString("blob_app_id"),
                    LEGACY_STORAGE_PROVIDER,
                    rs.getString("bucket_name"),
                    rs.getString("blob_object_key"),
                    rs.getString("file_hash"),
                    rs.getString("hash_algorithm"),
                    rs.getLong("blob_file_size"),
                    rs.getString("blob_content_type"),
                    rs.getInt("reference_count"),
                    toInstant(rs.getTimestamp("blob_created_at")),
                    toInstant(rs.getTimestamp("blob_updated_at"))
            );
            return new FileAccessMutationContext(fileAsset, blobObject);
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
