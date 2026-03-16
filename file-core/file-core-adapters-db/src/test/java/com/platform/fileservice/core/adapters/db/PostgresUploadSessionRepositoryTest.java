package com.platform.fileservice.core.adapters.db;

import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.UploadMode;
import com.platform.fileservice.core.domain.model.UploadSession;
import com.platform.fileservice.core.domain.model.UploadSessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresUploadSessionRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private PostgresUploadSessionRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:upload_session;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new PostgresUploadSessionRepository(jdbcTemplate);

        jdbcTemplate.execute("DROP TABLE IF EXISTS upload_tasks");
        jdbcTemplate.execute("""
                CREATE TABLE upload_tasks (
                    id VARCHAR(64) PRIMARY KEY,
                    app_id VARCHAR(32) NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    upload_mode VARCHAR(32),
                    access_level VARCHAR(20),
                    file_name VARCHAR(255) NOT NULL,
                    file_size BIGINT NOT NULL,
                    content_type VARCHAR(100),
                    chunk_size BIGINT NOT NULL,
                    total_chunks INT NOT NULL,
                    uploaded_chunks INT NOT NULL DEFAULT 0,
                    upload_id VARCHAR(255),
                    storage_path VARCHAR(500),
                    file_id VARCHAR(64),
                    file_hash VARCHAR(128),
                    hash_algorithm VARCHAR(20) DEFAULT 'MD5',
                    status VARCHAR(20) NOT NULL DEFAULT 'uploading',
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP,
                    completed_at TIMESTAMP,
                    expires_at TIMESTAMP
                )
                """);
    }

    @Test
    void shouldSaveAndLoadUploadSession() {
        UploadSession uploadSession = new UploadSession(
                "session-001",
                "blog",
                "user-001",
                UploadMode.PRESIGNED_SINGLE,
                AccessLevel.PRIVATE,
                "demo.pdf",
                "application/pdf",
                11L * 1024 * 1024,
                "hash-001",
                "blog/2026/03/14/user-001/uploads/session-001-demo.pdf",
                5 * 1024 * 1024,
                3,
                "upload-001",
                null,
                UploadSessionStatus.UPLOADING,
                Instant.parse("2026-03-14T00:00:00Z"),
                Instant.parse("2026-03-14T00:00:00Z"),
                Instant.parse("2026-03-15T00:00:00Z")
        );

        repository.save(uploadSession);

        Optional<UploadSession> stored = repository.findById("session-001");
        assertTrue(stored.isPresent());
        assertEquals(UploadMode.PRESIGNED_SINGLE, stored.get().uploadMode());
        assertEquals(AccessLevel.PRIVATE, stored.get().targetAccessLevel());
        assertEquals(UploadSessionStatus.UPLOADING, stored.get().status());
        assertEquals("blog/2026/03/14/user-001/uploads/session-001-demo.pdf", stored.get().objectKey());
        assertEquals(null, stored.get().fileId());
        assertEquals(5 * 1024 * 1024L, jdbcTemplate.queryForObject(
                "SELECT chunk_size FROM upload_tasks WHERE id = ?",
                Long.class,
                "session-001"
        ));
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT total_chunks FROM upload_tasks WHERE id = ?",
                Integer.class,
                "session-001"
        ));
        assertEquals("blog/2026/03/14/user-001/uploads/session-001-demo.pdf", jdbcTemplate.queryForObject(
                "SELECT storage_path FROM upload_tasks WHERE id = ?",
                String.class,
                "session-001"
        ));
    }

    @Test
    void shouldFindExpiredSessionsAndUpdateStatus() {
        insertTask("session-001", "initiated", "direct", "private", "2026-03-14T00:00:00Z");
        insertTask("session-002", "completed", "direct", "public", "2026-03-14T00:00:00Z");

        List<UploadSession> expiredSessions = repository.findExpiredSessions(Instant.parse("2026-03-14T01:00:00Z"));

        assertEquals(1, expiredSessions.size());
        assertEquals("session-001", expiredSessions.get(0).uploadSessionId());
        assertEquals("blog/2026/03/14/user-001/uploads/session-001-demo.pdf", expiredSessions.get(0).objectKey());
        assertTrue(repository.updateStatus("session-001", UploadSessionStatus.EXPIRED));
        assertEquals("expired", jdbcTemplate.queryForObject(
                "SELECT status FROM upload_tasks WHERE id = ?",
                String.class,
                "session-001"
        ));
    }

    @Test
    void shouldMarkCompletedWithFileId() {
        insertTask("session-001", "uploading", "direct", "private", "2026-03-15T00:00:00Z");

        assertTrue(repository.markCompleted("session-001", "file-001"));
        assertEquals("completed", jdbcTemplate.queryForObject(
                "SELECT status FROM upload_tasks WHERE id = ?",
                String.class,
                "session-001"
        ));
        assertEquals("file-001", jdbcTemplate.queryForObject(
                "SELECT file_id FROM upload_tasks WHERE id = ?",
                String.class,
                "session-001"
        ));
    }

    @Test
    void shouldFindActiveSessionByHash() {
        insertTask("session-001", "uploading", "direct", "private", "2026-03-15T00:00:00Z");
        insertTask("session-002", "completed", "direct", "private", "2026-03-15T00:00:00Z");

        Optional<UploadSession> activeSession = repository.findActiveByHash("blog", "user-001", "hash-001");

        assertTrue(activeSession.isPresent());
        assertEquals("session-001", activeSession.get().uploadSessionId());
    }

    private void insertTask(String id, String status, String uploadMode, String accessLevel, String expiresAt) {
        jdbcTemplate.update("""
                        INSERT INTO upload_tasks (
                            id, app_id, user_id, upload_mode, access_level, file_name, file_size, content_type,
                            chunk_size, total_chunks, uploaded_chunks, upload_id, storage_path, file_id, file_hash, status,
                            created_at, updated_at, expires_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                "blog",
                "user-001",
                uploadMode,
                accessLevel,
                "demo.pdf",
                11L * 1024 * 1024,
                "application/pdf",
                5 * 1024 * 1024L,
                3,
                0,
                "upload-001",
                "blog/2026/03/14/user-001/uploads/" + id + "-demo.pdf",
                null,
                "hash-001",
                status,
                Timestamp.from(Instant.parse("2026-03-14T00:00:00Z")),
                Timestamp.from(Instant.parse("2026-03-14T00:00:00Z")),
                Timestamp.from(Instant.parse(expiresAt))
        );
    }
}
