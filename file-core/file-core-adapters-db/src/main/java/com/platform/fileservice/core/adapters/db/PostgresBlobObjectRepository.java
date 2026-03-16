package com.platform.fileservice.core.adapters.db;

import com.platform.fileservice.core.domain.exception.FileAccessMutationException;
import com.platform.fileservice.core.domain.model.BlobObject;
import com.platform.fileservice.core.ports.repository.BlobObjectRepository;
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
 * PostgreSQL-backed blob object repository reusing the legacy storage_objects table.
 */
public final class PostgresBlobObjectRepository implements BlobObjectRepository {

    private static final String LEGACY_STORAGE_PROVIDER = "legacy-s3";

    private static final String SELECT_BY_ID_SQL = """
            SELECT id, app_id, file_hash, hash_algorithm, storage_path, bucket_name,
                   file_size, content_type, reference_count, created_at, updated_at
            FROM storage_objects
            WHERE id = ?
            """;

    private static final String SELECT_BY_HASH_SQL = """
            SELECT id, app_id, file_hash, hash_algorithm, storage_path, bucket_name,
                   file_size, content_type, reference_count, created_at, updated_at
            FROM storage_objects
            WHERE app_id = ? AND file_hash = ? AND bucket_name = ?
            ORDER BY created_at ASC
            LIMIT 1
            """;

    private static final String INSERT_SQL = """
            INSERT INTO storage_objects (
                id, app_id, file_hash, hash_algorithm, storage_path, bucket_name,
                file_size, content_type, reference_count, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INCREMENT_REFERENCE_COUNT_SQL = """
            UPDATE storage_objects
            SET reference_count = reference_count + 1, updated_at = ?
            WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresBlobObjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public Optional<BlobObject> findById(String blobObjectId) {
        List<BlobObject> results = jdbcTemplate.query(SELECT_BY_ID_SQL, new BlobObjectRowMapper(), blobObjectId);
        return results.stream().findFirst();
    }

    @Override
    public Optional<BlobObject> findByHash(String tenantId, String hashValue, String bucketName) {
        List<BlobObject> results = jdbcTemplate.query(
                SELECT_BY_HASH_SQL,
                new BlobObjectRowMapper(),
                tenantId,
                hashValue,
                bucketName
        );
        return results.stream().findFirst();
    }

    @Override
    public BlobObject save(BlobObject blobObject) {
        try {
            int rows = jdbcTemplate.update(
                    INSERT_SQL,
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
            if (rows != 1) {
                throw new FileAccessMutationException("failed to save blob object: " + blobObject.blobObjectId());
            }
        } catch (DataAccessException ex) {
            throw new FileAccessMutationException("failed to save blob object: " + blobObject.blobObjectId(), ex);
        }
        return blobObject;
    }

    @Override
    public boolean incrementReferenceCount(String blobObjectId) {
        int rows = jdbcTemplate.update(
                INCREMENT_REFERENCE_COUNT_SQL,
                Timestamp.from(Instant.now()),
                blobObjectId
        );
        return rows == 1;
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static final class BlobObjectRowMapper implements RowMapper<BlobObject> {

        @Override
        public BlobObject mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new BlobObject(
                    rs.getString("id"),
                    rs.getString("app_id"),
                    LEGACY_STORAGE_PROVIDER,
                    rs.getString("bucket_name"),
                    rs.getString("storage_path"),
                    rs.getString("file_hash"),
                    rs.getString("hash_algorithm"),
                    rs.getLong("file_size"),
                    rs.getString("content_type"),
                    rs.getInt("reference_count"),
                    toInstant(rs.getTimestamp("created_at")),
                    toInstant(rs.getTimestamp("updated_at"))
            );
        }

        private Instant toInstant(Timestamp timestamp) {
            return timestamp == null ? null : timestamp.toInstant();
        }
    }
}
