package com.platform.fileservice.core.application.service;

import com.platform.fileservice.core.domain.model.AccessTicketGrant;
import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.BlobObject;
import com.platform.fileservice.core.domain.model.FileAsset;
import com.platform.fileservice.core.domain.model.FileAssetStatus;
import com.platform.fileservice.core.domain.model.FileAccessMutationContext;
import com.platform.fileservice.core.domain.exception.FileAccessDeniedException;
import com.platform.fileservice.core.domain.exception.FileAssetNotFoundException;
import com.platform.fileservice.core.ports.access.AuthorizedFileAccessPort;
import com.platform.fileservice.core.ports.access.FileAccessMutationPort;
import com.platform.fileservice.core.ports.security.AccessTicketPort;
import com.platform.fileservice.core.ports.storage.ObjectStoragePort;
import com.platform.fileservice.core.ports.system.ClockPort;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Application service entry for access resolution and ticket issuing.
 */
public final class AccessAppService {

    private final AuthorizedFileAccessPort authorizedFileAccessPort;
    private final FileAccessMutationPort fileAccessMutationPort;
    private final ObjectStoragePort objectStoragePort;
    private final AccessTicketPort accessTicketPort;
    private final ClockPort clockPort;
    private final TransactionOperations transactionOperations;

    public AccessAppService(AuthorizedFileAccessPort authorizedFileAccessPort,
                            FileAccessMutationPort fileAccessMutationPort,
                            ObjectStoragePort objectStoragePort,
                            AccessTicketPort accessTicketPort,
                            ClockPort clockPort,
                            TransactionOperations transactionOperations) {
        this.authorizedFileAccessPort = authorizedFileAccessPort;
        this.fileAccessMutationPort = fileAccessMutationPort;
        this.objectStoragePort = objectStoragePort;
        this.accessTicketPort = accessTicketPort;
        this.clockPort = clockPort;
        this.transactionOperations = transactionOperations;
    }

    public FileAsset getAccessibleFile(String tenantId, String fileId, String subjectId) {
        return authorizedFileAccessPort.loadAccessibleFile(tenantId, fileId, subjectId);
    }

    public AccessTicketGrant issueAccessTicket(String fileId, String tenantId, String subjectId, Duration ttl) {
        FileAsset fileAsset = getAccessibleFile(tenantId, fileId, subjectId);
        Instant now = clockPort.now();
        AccessTicketGrant accessTicketGrant = new AccessTicketGrant(
                UUID.randomUUID().toString(),
                fileAsset.fileId(),
                tenantId,
                subjectId,
                now,
                now.plus(ttl),
                null
        );
        return accessTicketPort.issueTicket(accessTicketGrant);
    }

    public void updateAccessLevel(String tenantId, String fileId, String subjectId, AccessLevel accessLevel) {
        FileAccessMutationContext mutationContext = fileAccessMutationPort.loadForChange(tenantId, fileId);
        FileAsset fileAsset = mutationContext.fileAsset();
        validateOwnerCanUpdateAccessLevel(fileAsset, fileId, subjectId);

        if (fileAsset.accessLevel() == accessLevel) {
            return;
        }

        if (!mutationContext.hasBoundBlobObject()) {
            transactionOperations.executeWithoutResult(status ->
                    fileAccessMutationPort.updateAccessLevel(fileId, accessLevel));
            return;
        }

        BlobObject sourceBlobObject = mutationContext.blobObject();
        String sourceBucketName = objectStoragePort.normalizeBucketName(sourceBlobObject.bucketName());
        String targetBucketName = objectStoragePort.resolveBucketName(accessLevel);

        if (Objects.equals(sourceBucketName, targetBucketName)) {
            transactionOperations.executeWithoutResult(status ->
                    fileAccessMutationPort.updateAccessLevel(fileId, accessLevel));
            return;
        }

        String objectKey = sourceBlobObject.objectKey();
        objectStoragePort.copyObject(sourceBucketName, objectKey, targetBucketName, objectKey);
        BlobObject copiedBlobObject = buildCopiedBlobObject(sourceBlobObject, targetBucketName);

        try {
            transactionOperations.executeWithoutResult(status -> {
                fileAccessMutationPort.createBlobObject(copiedBlobObject);
                fileAccessMutationPort.rebindBlobAndAccessLevel(fileId, copiedBlobObject, accessLevel);
                fileAccessMutationPort.decrementBlobReference(sourceBlobObject.blobObjectId());
            });
        } catch (RuntimeException ex) {
            deleteQuietly(targetBucketName, objectKey);
            throw ex;
        }

        if (sourceBlobObject.referenceCount() <= 1) {
            deleteQuietly(sourceBucketName, objectKey);
        }
    }

    private void validateOwnerCanUpdateAccessLevel(FileAsset fileAsset, String fileId, String subjectId) {
        if (fileAsset == null || fileAsset.status() == FileAssetStatus.DELETED) {
            throw new FileAssetNotFoundException("fileId deleted: " + fileId);
        }

        String normalizedSubjectId = normalizeSubjectId(subjectId);
        if (normalizedSubjectId == null || !normalizedSubjectId.equals(fileAsset.ownerId())) {
            throw new FileAccessDeniedException("access denied to update access level for fileId: " + fileId);
        }
    }

    private String normalizeSubjectId(String subjectId) {
        if (subjectId == null) {
            return null;
        }
        String trimmed = subjectId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BlobObject buildCopiedBlobObject(BlobObject sourceBlobObject, String targetBucketName) {
        Instant now = clockPort.now();
        return new BlobObject(
                UUID.randomUUID().toString(),
                sourceBlobObject.tenantId(),
                sourceBlobObject.storageProvider(),
                targetBucketName,
                sourceBlobObject.objectKey(),
                sourceBlobObject.hashValue(),
                sourceBlobObject.hashAlgorithm(),
                sourceBlobObject.fileSize(),
                sourceBlobObject.contentType(),
                1,
                now,
                now
        );
    }

    private void deleteQuietly(String bucketName, String objectKey) {
        try {
            objectStoragePort.deleteObject(bucketName, objectKey);
        } catch (RuntimeException ignored) {
            // 数据库事务已经提交，物理清理由对账/GC 兜底。
        }
    }
}
