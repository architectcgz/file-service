package com.platform.fileservice.core.application.service;

import com.platform.fileservice.core.domain.model.UploadSession;
import com.platform.fileservice.core.domain.model.UploadMode;
import com.platform.fileservice.core.domain.model.UploadSessionStatus;
import com.platform.fileservice.core.ports.repository.UploadSessionRepository;
import com.platform.fileservice.core.ports.storage.ObjectStoragePort;
import com.platform.fileservice.core.ports.system.ClockPort;

import java.util.Objects;
import java.util.List;

/**
 * Application service entry for reconciliation and cleanup workflows.
 */
public final class CleanupAppService {

    private final UploadSessionRepository uploadSessionRepository;
    private final ObjectStoragePort objectStoragePort;
    private final ClockPort clockPort;

    public CleanupAppService(UploadSessionRepository uploadSessionRepository,
                             ObjectStoragePort objectStoragePort,
                             ClockPort clockPort) {
        this.uploadSessionRepository = uploadSessionRepository;
        this.objectStoragePort = objectStoragePort;
        this.clockPort = clockPort;
    }

    public List<UploadSession> findExpiredUploadSessions() {
        return uploadSessionRepository.findExpiredSessions(clockPort.now());
    }

    public boolean expireUploadSession(UploadSession uploadSession) {
        Objects.requireNonNull(uploadSession, "uploadSession must not be null");
        String bucketName = uploadSession.objectKey() == null
                ? null
                : objectStoragePort.resolveBucketName(uploadSession.targetAccessLevel());
        if (uploadSession.hasMultipartUpload()) {
            objectStoragePort.abortMultipartUpload(
                    bucketName,
                    uploadSession.objectKey(),
                    uploadSession.providerUploadId()
            );
        } else if (uploadSession.uploadMode() == UploadMode.PRESIGNED_SINGLE
                && uploadSession.objectKey() != null) {
            objectStoragePort.deleteObject(bucketName, uploadSession.objectKey());
        }
        return uploadSessionRepository.updateStatus(uploadSession.uploadSessionId(), UploadSessionStatus.EXPIRED);
    }

    public int expireUploadSessions() {
        int expiredCount = 0;
        for (UploadSession uploadSession : findExpiredUploadSessions()) {
            if (expireUploadSession(uploadSession)) {
                expiredCount++;
            }
        }
        return expiredCount;
    }
}
