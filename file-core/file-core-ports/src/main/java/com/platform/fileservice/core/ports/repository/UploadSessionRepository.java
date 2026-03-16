package com.platform.fileservice.core.ports.repository;

import com.platform.fileservice.core.domain.model.UploadSession;
import com.platform.fileservice.core.domain.model.UploadSessionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository port for upload sessions.
 */
public interface UploadSessionRepository {

    Optional<UploadSession> findById(String uploadSessionId);

    Optional<UploadSession> findActiveByHash(String tenantId, String ownerId, String fileHash);

    List<UploadSession> findByOwner(String tenantId, String ownerId, int limit);

    UploadSession save(UploadSession uploadSession);

    List<UploadSession> findExpiredSessions(Instant before);

    boolean markCompleted(String uploadSessionId, String fileId);

    boolean updateStatus(String uploadSessionId, UploadSessionStatus status);
}
