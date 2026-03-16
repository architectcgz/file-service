package com.platform.fileservice.core.adapters.db;

import com.platform.fileservice.core.domain.exception.FileAccessDeniedException;
import com.platform.fileservice.core.domain.exception.FileAssetNotFoundException;
import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.FileAsset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcAuthorizedFileAccessAdapterTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcAuthorizedFileAccessAdapter adapter;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:file_access;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        adapter = new JdbcAuthorizedFileAccessAdapter(jdbcTemplate);

        jdbcTemplate.execute("DROP TABLE IF EXISTS file_records");
        jdbcTemplate.execute("""
                CREATE TABLE file_records (
                    id VARCHAR(64) PRIMARY KEY,
                    app_id VARCHAR(32) NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
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
    void shouldLoadPublicFileInTenantScope() {
        insertFile("file-001", "blog", "user-001", "public", "completed");

        FileAsset fileAsset = adapter.loadAccessibleFile("blog", "file-001", null);

        assertEquals("file-001", fileAsset.fileId());
        assertEquals(AccessLevel.PUBLIC, fileAsset.accessLevel());
        assertEquals("blog/2026/03/14/user-001/files/test.txt", fileAsset.objectKey());
    }

    @Test
    void shouldRejectPrivateFileForAnotherUser() {
        insertFile("file-001", "blog", "user-001", "private", "completed");

        assertThrows(FileAccessDeniedException.class,
                () -> adapter.loadAccessibleFile("blog", "file-001", "user-002"));
    }

    @Test
    void shouldRejectDeletedFile() {
        insertFile("file-001", "blog", "user-001", "public", "deleted");

        assertThrows(FileAssetNotFoundException.class,
                () -> adapter.loadAccessibleFile("blog", "file-001", null));
    }

    @Test
    void shouldRejectAnotherTenantFileAsNotFound() {
        insertFile("file-001", "im", "user-001", "public", "completed");

        assertThrows(FileAssetNotFoundException.class,
                () -> adapter.loadAccessibleFile("blog", "file-001", null));
    }

    private void insertFile(String fileId, String appId, String userId, String accessLevel, String status) {
        jdbcTemplate.update("""
                        INSERT INTO file_records (
                            id, app_id, user_id, original_name, storage_path, file_size, content_type, access_level, status, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                fileId,
                appId,
                userId,
                "test.txt",
                appId + "/2026/03/14/" + userId + "/files/test.txt",
                1024L,
                "text/plain",
                accessLevel,
                status
        );
    }
}
