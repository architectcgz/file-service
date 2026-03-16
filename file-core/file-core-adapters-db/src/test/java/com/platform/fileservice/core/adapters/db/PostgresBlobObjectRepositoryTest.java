package com.platform.fileservice.core.adapters.db;

import com.platform.fileservice.core.domain.model.BlobObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresBlobObjectRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private PostgresBlobObjectRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:blob_object;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new PostgresBlobObjectRepository(jdbcTemplate);

        jdbcTemplate.execute("DROP TABLE IF EXISTS storage_objects");
        jdbcTemplate.execute("""
                CREATE TABLE storage_objects (
                    id VARCHAR(64) PRIMARY KEY,
                    app_id VARCHAR(32) NOT NULL,
                    file_hash VARCHAR(128) NOT NULL,
                    hash_algorithm VARCHAR(20) NOT NULL DEFAULT 'MD5',
                    storage_path VARCHAR(500) NOT NULL,
                    bucket_name VARCHAR(128),
                    file_size BIGINT NOT NULL,
                    content_type VARCHAR(100),
                    reference_count INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """);
    }

    @Test
    void shouldSaveFindAndIncrementBlobObject() {
        BlobObject blobObject = new BlobObject(
                "blob-001",
                "blog",
                "legacy-s3",
                "private-bucket",
                "blog/2026/03/14/user-001/uploads/demo.mp4",
                "hash-001",
                "MD5",
                1024L,
                "video/mp4",
                1,
                Instant.parse("2026-03-14T00:00:00Z"),
                Instant.parse("2026-03-14T00:00:00Z")
        );

        repository.save(blobObject);

        Optional<BlobObject> stored = repository.findById("blob-001");
        assertTrue(stored.isPresent());
        assertEquals("private-bucket", stored.get().bucketName());
        assertEquals("hash-001", stored.get().hashValue());

        Optional<BlobObject> byHash = repository.findByHash("blog", "hash-001", "private-bucket");
        assertTrue(byHash.isPresent());
        assertTrue(repository.incrementReferenceCount("blob-001"));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT reference_count FROM storage_objects WHERE id = ?",
                Integer.class,
                "blob-001"
        ));
    }
}
