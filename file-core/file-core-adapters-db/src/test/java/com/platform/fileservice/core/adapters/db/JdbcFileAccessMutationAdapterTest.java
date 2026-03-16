package com.platform.fileservice.core.adapters.db;

import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.BlobObject;
import com.platform.fileservice.core.domain.model.FileAccessMutationContext;
import com.platform.fileservice.core.domain.model.FileAssetStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JdbcFileAccessMutationAdapterTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcFileAccessMutationAdapter adapter;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:file_access_mutation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        adapter = new JdbcFileAccessMutationAdapter(jdbcTemplate);

        jdbcTemplate.execute("DROP TABLE IF EXISTS file_records");
        jdbcTemplate.execute("DROP TABLE IF EXISTS storage_objects");
        jdbcTemplate.execute("""
                CREATE TABLE storage_objects (
                    id VARCHAR(64) PRIMARY KEY,
                    app_id VARCHAR(32) NOT NULL,
                    file_hash VARCHAR(128) NOT NULL,
                    hash_algorithm VARCHAR(20) NOT NULL,
                    storage_path VARCHAR(500) NOT NULL,
                    bucket_name VARCHAR(128),
                    file_size BIGINT NOT NULL,
                    content_type VARCHAR(100),
                    reference_count INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE file_records (
                    id VARCHAR(64) PRIMARY KEY,
                    app_id VARCHAR(32) NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    storage_object_id VARCHAR(64),
                    original_name VARCHAR(255) NOT NULL,
                    storage_path VARCHAR(500) NOT NULL,
                    file_size BIGINT NOT NULL,
                    content_type VARCHAR(100),
                    access_level VARCHAR(20) DEFAULT 'public',
                    status VARCHAR(20) NOT NULL DEFAULT 'pending',
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """);
    }

    @Test
    void shouldLoadMutationContextWithBoundBlobObject() {
        insertStorageObject("blob-001", "blog", "images/file-001.png", "public-bucket", 2);
        insertFile("file-001", "blog", "user-001", "blob-001", "images/file-001.png", "public", "completed");

        FileAccessMutationContext context = adapter.loadForChange("blog", "file-001");

        assertEquals("file-001", context.fileAsset().fileId());
        assertEquals(AccessLevel.PUBLIC, context.fileAsset().accessLevel());
        assertEquals(FileAssetStatus.ACTIVE, context.fileAsset().status());
        assertNotNull(context.blobObject());
        assertEquals("blob-001", context.blobObject().blobObjectId());
        assertEquals("public-bucket", context.blobObject().bucketName());
        assertEquals(2, context.blobObject().referenceCount());
    }

    @Test
    void shouldUpdateAccessLevelOnly() {
        insertFile("file-001", "blog", "user-001", null, "images/file-001.png", "public", "completed");

        adapter.updateAccessLevel("file-001", AccessLevel.PRIVATE);

        assertEquals("private", jdbcTemplate.queryForObject(
                "SELECT access_level FROM file_records WHERE id = ?",
                String.class,
                "file-001"
        ));
    }

    @Test
    void shouldCreateBlobAndRebindFile() {
        insertStorageObject("blob-001", "blog", "images/file-001.png", "public-bucket", 2);
        insertFile("file-001", "blog", "user-001", "blob-001", "images/file-001.png", "public", "completed");

        BlobObject newBlobObject = new BlobObject(
                "blob-002",
                "blog",
                "legacy-s3",
                "private-bucket",
                "images/file-001.png",
                "hash-001",
                "MD5",
                1024L,
                "image/png",
                1,
                Instant.parse("2026-03-14T00:00:00Z"),
                Instant.parse("2026-03-14T00:00:00Z")
        );

        adapter.createBlobObject(newBlobObject);
        adapter.rebindBlobAndAccessLevel("file-001", newBlobObject, AccessLevel.PRIVATE);
        adapter.decrementBlobReference("blob-001");

        assertEquals("blob-002", jdbcTemplate.queryForObject(
                "SELECT storage_object_id FROM file_records WHERE id = ?",
                String.class,
                "file-001"
        ));
        assertEquals("private", jdbcTemplate.queryForObject(
                "SELECT access_level FROM file_records WHERE id = ?",
                String.class,
                "file-001"
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT reference_count FROM storage_objects WHERE id = ?",
                Integer.class,
                "blob-001"
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT reference_count FROM storage_objects WHERE id = ?",
                Integer.class,
                "blob-002"
        ));
    }

    private void insertStorageObject(String blobObjectId, String appId, String storagePath, String bucketName, int referenceCount) {
        jdbcTemplate.update("""
                        INSERT INTO storage_objects (
                            id, app_id, file_hash, hash_algorithm, storage_path, bucket_name,
                            file_size, content_type, reference_count, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                blobObjectId,
                appId,
                "hash-001",
                "MD5",
                storagePath,
                bucketName,
                1024L,
                "image/png",
                referenceCount
        );
    }

    private void insertFile(String fileId, String appId, String userId, String storageObjectId,
                            String storagePath, String accessLevel, String status) {
        jdbcTemplate.update("""
                        INSERT INTO file_records (
                            id, app_id, user_id, storage_object_id, original_name, storage_path,
                            file_size, content_type, access_level, status, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                fileId,
                appId,
                userId,
                storageObjectId,
                "demo.png",
                storagePath,
                1024L,
                "image/png",
                accessLevel,
                status
        );
    }
}
