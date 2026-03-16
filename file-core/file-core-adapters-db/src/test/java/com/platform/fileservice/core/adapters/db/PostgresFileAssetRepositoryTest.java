package com.platform.fileservice.core.adapters.db;

import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.FileAsset;
import com.platform.fileservice.core.domain.model.FileAssetStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresFileAssetRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private PostgresFileAssetRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:file_asset;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new PostgresFileAssetRepository(jdbcTemplate);

        jdbcTemplate.execute("DROP TABLE IF EXISTS file_records");
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
    void shouldSaveAndFindFileAsset() {
        FileAsset fileAsset = new FileAsset(
                "file-001",
                "blog",
                "user-001",
                "blob-001",
                "demo.mp4",
                "blog/2026/03/14/user-001/uploads/demo.mp4",
                "video/mp4",
                1024L,
                AccessLevel.PRIVATE,
                FileAssetStatus.ACTIVE,
                Instant.parse("2026-03-14T00:00:00Z"),
                Instant.parse("2026-03-14T00:00:00Z")
        );

        repository.save(fileAsset);

        Optional<FileAsset> stored = repository.findById("file-001");
        assertTrue(stored.isPresent());
        assertEquals("blob-001", stored.get().blobObjectId());
        assertEquals("blog/2026/03/14/user-001/uploads/demo.mp4", stored.get().objectKey());
        assertEquals(FileAssetStatus.ACTIVE, stored.get().status());
    }
}
