package com.platform.fileservice.core.application.service;

import com.platform.fileservice.core.domain.exception.UploadSessionAccessDeniedException;
import com.platform.fileservice.core.domain.exception.UploadSessionInvalidRequestException;
import com.platform.fileservice.core.domain.exception.UploadSessionMutationException;
import com.platform.fileservice.core.domain.exception.UploadSessionNotFoundException;
import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.BlobObject;
import com.platform.fileservice.core.domain.model.CompletedUploadPart;
import com.platform.fileservice.core.domain.model.FileAsset;
import com.platform.fileservice.core.domain.model.FileAssetStatus;
import com.platform.fileservice.core.domain.model.PartUploadUrl;
import com.platform.fileservice.core.domain.model.PartUploadUrlGrant;
import com.platform.fileservice.core.domain.model.SingleUploadUrlGrant;
import com.platform.fileservice.core.domain.model.UploadCompletion;
import com.platform.fileservice.core.domain.model.UploadMode;
import com.platform.fileservice.core.domain.model.UploadProgress;
import com.platform.fileservice.core.domain.model.StoredObjectMetadata;
import com.platform.fileservice.core.domain.model.UploadSessionCreationResult;
import com.platform.fileservice.core.domain.model.UploadedPart;
import com.platform.fileservice.core.domain.model.UploadSession;
import com.platform.fileservice.core.domain.model.UploadSessionStatus;
import com.platform.fileservice.core.ports.repository.BlobObjectRepository;
import com.platform.fileservice.core.ports.repository.FileAssetRepository;
import com.platform.fileservice.core.ports.repository.UploadSessionRepository;
import com.platform.fileservice.core.ports.storage.ObjectStoragePort;
import com.platform.fileservice.core.ports.system.ClockPort;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Application service entry for creating and tracking upload sessions.
 */
public final class UploadAppService {

    private static final Pattern INVALID_FILENAME_CHARS = Pattern.compile("[^A-Za-z0-9._-]+");
    private static final Pattern EDGE_FILENAME_CHARS = Pattern.compile("^[.-]+|[.-]+$");
    private static final int MAX_FILENAME_LENGTH = 120;
    private static final String DEFAULT_HASH_ALGORITHM = "MD5";
    private static final String LEGACY_STORAGE_PROVIDER = "legacy-s3";
    private static final String ACTIVE_UPLOAD_SESSION_UNIQUE_INDEX = "uk_upload_tasks_active_hash";
    private static final Duration DEFAULT_COMPLETION_WAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_COMPLETION_POLL_INTERVAL = Duration.ofMillis(100);

    private final UploadSessionRepository uploadSessionRepository;
    private final BlobObjectRepository blobObjectRepository;
    private final FileAssetRepository fileAssetRepository;
    private final ObjectStoragePort objectStoragePort;
    private final ClockPort clockPort;
    private final TransactionOperations transactionOperations;
    private final Duration completionWaitTimeout;
    private final Duration completionPollInterval;

    public UploadAppService(UploadSessionRepository uploadSessionRepository,
                            BlobObjectRepository blobObjectRepository,
                            FileAssetRepository fileAssetRepository,
                            ObjectStoragePort objectStoragePort,
                            ClockPort clockPort,
                            TransactionOperations transactionOperations) {
        this(
                uploadSessionRepository,
                blobObjectRepository,
                fileAssetRepository,
                objectStoragePort,
                clockPort,
                transactionOperations,
                DEFAULT_COMPLETION_WAIT_TIMEOUT,
                DEFAULT_COMPLETION_POLL_INTERVAL
        );
    }

    public UploadAppService(UploadSessionRepository uploadSessionRepository,
                            BlobObjectRepository blobObjectRepository,
                            FileAssetRepository fileAssetRepository,
                            ObjectStoragePort objectStoragePort,
                            ClockPort clockPort,
                            TransactionOperations transactionOperations,
                            Duration completionWaitTimeout,
                            Duration completionPollInterval) {
        this.uploadSessionRepository = uploadSessionRepository;
        this.blobObjectRepository = blobObjectRepository;
        this.fileAssetRepository = fileAssetRepository;
        this.objectStoragePort = objectStoragePort;
        this.clockPort = clockPort;
        this.transactionOperations = transactionOperations;
        this.completionWaitTimeout = requirePositiveDuration(completionWaitTimeout, "completionWaitTimeout");
        this.completionPollInterval = requirePositiveDuration(completionPollInterval, "completionPollInterval");
    }

    public UploadSessionCreationResult createSession(String tenantId,
                                                     String ownerId,
                                                     UploadMode uploadMode,
                                                     AccessLevel accessLevel,
                                                     String originalFilename,
                                                     String contentType,
                                                     long expectedSize,
                                                     String fileHash,
                                                     Duration ttl,
                                                     int chunkSizeBytes,
                                                     int maxParts) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        Instant now = clockPort.now();
        Optional<UploadSession> instantUploadSession = createInstantUploadSessionIfPossible(
                tenantId,
                ownerId,
                uploadMode,
                accessLevel,
                originalFilename,
                contentType,
                expectedSize,
                fileHash,
                ttl,
                now
        );
        if (instantUploadSession.isPresent()) {
            return new UploadSessionCreationResult(instantUploadSession.get(), false, true, List.of());
        }
        Optional<UploadSession> resumableSession = findReusableSession(
                tenantId,
                ownerId,
                uploadMode,
                accessLevel,
                expectedSize,
                fileHash,
                now
        );
        if (resumableSession.isPresent()) {
            UploadSession uploadSession = resumableSession.get();
            return new UploadSessionCreationResult(uploadSession, true, false, loadUploadedParts(uploadSession));
        }

        String objectKey = null;
        int effectiveChunkSizeBytes = 0;
        int totalParts = 0;
        String providerUploadId = null;
        UploadSessionStatus status = UploadSessionStatus.INITIATED;

        if (requiresMultipartUpload(uploadMode)) {
            validateMultipartConfiguration(chunkSizeBytes, maxParts);
            effectiveChunkSizeBytes = chunkSizeBytes;
            totalParts = calculateTotalParts(expectedSize, chunkSizeBytes, maxParts);
            objectKey = buildObjectKey(tenantId, ownerId, originalFilename, now);
            String bucketName = objectStoragePort.resolveBucketName(accessLevel);
            providerUploadId = objectStoragePort.createMultipartUpload(bucketName, objectKey, contentType);
            status = UploadSessionStatus.UPLOADING;
        } else if (requiresSingleObjectUpload(uploadMode)) {
            objectKey = buildObjectKey(tenantId, ownerId, originalFilename, now);
            totalParts = 1;
            status = UploadSessionStatus.INITIATED;
        }

        UploadSession uploadSession = new UploadSession(
                UUID.randomUUID().toString(),
                tenantId,
                ownerId,
                uploadMode,
                accessLevel,
                originalFilename,
                contentType,
                expectedSize,
                fileHash,
                objectKey,
                effectiveChunkSizeBytes,
                totalParts,
                providerUploadId,
                null,
                status,
                now,
                now,
                now.plus(ttl)
        );
        try {
            return new UploadSessionCreationResult(uploadSessionRepository.save(uploadSession), false, false, List.of());
        } catch (UploadSessionMutationException ex) {
            UploadSessionCreationResult recovered = recoverFromActiveSessionConflict(
                    tenantId,
                    ownerId,
                    uploadMode,
                    accessLevel,
                    expectedSize,
                    fileHash,
                    now,
                    ex
            );
            if (recovered != null) {
                return recovered;
            }
            throw ex;
        }
    }

    public SingleUploadUrlGrant issueSingleUploadUrl(String tenantId,
                                                     String uploadSessionId,
                                                     String subjectId,
                                                     Duration ttl) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        UploadSession uploadSession = loadOwnedSession(tenantId, uploadSessionId, subjectId);
        if (isExpired(uploadSession)) {
            throw new UploadSessionInvalidRequestException("upload session expired: " + uploadSessionId);
        }
        if (!requiresSingleObjectUpload(uploadSession.uploadMode())) {
            throw new UploadSessionInvalidRequestException("upload mode does not support single upload url: " + uploadSession.uploadMode());
        }
        if (uploadSession.status() != UploadSessionStatus.INITIATED
                && uploadSession.status() != UploadSessionStatus.UPLOADING) {
            throw new UploadSessionInvalidRequestException("upload session is not accepting uploads: " + uploadSession.status());
        }

        String bucketName = objectStoragePort.resolveBucketName(uploadSession.targetAccessLevel());
        int expiresInSeconds = Math.toIntExact(Math.max(ttl.getSeconds(), 1L));
        return new SingleUploadUrlGrant(
                uploadSession.uploadSessionId(),
                objectStoragePort.generatePresignedPutObjectUrl(
                        bucketName,
                        uploadSession.objectKey(),
                        uploadSession.contentType(),
                        ttl
                ),
                expiresInSeconds
        );
    }

    public UploadSession getVisibleSession(String tenantId, String uploadSessionId, String subjectId) {
        return decorateExpiredStatusIfNeeded(loadOwnedSession(tenantId, uploadSessionId, subjectId));
    }

    public List<UploadSession> listVisibleSessions(String tenantId, String subjectId, int limit) {
        return uploadSessionRepository.findByOwner(tenantId, subjectId, limit).stream()
                .map(this::decorateExpiredStatusIfNeeded)
                .toList();
    }

    public PartUploadUrlGrant issuePartUploadUrls(String tenantId,
                                                  String uploadSessionId,
                                                  String subjectId,
                                                  List<Integer> partNumbers,
                                                  Duration ttl) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        UploadSession uploadSession = loadOwnedSession(tenantId, uploadSessionId, subjectId);
        if (isExpired(uploadSession)) {
            throw new UploadSessionInvalidRequestException("upload session expired: " + uploadSessionId);
        }
        if (uploadSession.uploadMode() == UploadMode.INLINE) {
            throw new UploadSessionInvalidRequestException("upload mode does not support part upload urls: " + uploadSession.uploadMode());
        }
        if (!uploadSession.hasMultipartUpload()) {
            throw new UploadSessionInvalidRequestException("upload session is missing multipart upload context: " + uploadSessionId);
        }
        if (uploadSession.status() != UploadSessionStatus.INITIATED
                && uploadSession.status() != UploadSessionStatus.UPLOADING) {
            throw new UploadSessionInvalidRequestException("upload session is not accepting part uploads: " + uploadSession.status());
        }

        validatePartNumbers(partNumbers, uploadSession.totalParts());
        String bucketName = objectStoragePort.resolveBucketName(uploadSession.targetAccessLevel());
        int expiresInSeconds = Math.toIntExact(Math.max(ttl.getSeconds(), 1L));
        List<PartUploadUrl> partUploadUrls = new ArrayList<>(partNumbers.size());
        for (Integer partNumber : partNumbers) {
            partUploadUrls.add(new PartUploadUrl(
                    partNumber,
                    objectStoragePort.generatePresignedUploadPartUrl(
                            bucketName,
                            uploadSession.objectKey(),
                            uploadSession.providerUploadId(),
                            partNumber,
                            ttl
                    ),
                    expiresInSeconds
            ));
        }
        return new PartUploadUrlGrant(uploadSession.uploadSessionId(), partUploadUrls);
    }

    public String uploadPart(String tenantId,
                             String uploadSessionId,
                             String subjectId,
                             int partNumber,
                             byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        UploadSession uploadSession = loadOwnedSession(tenantId, uploadSessionId, subjectId);
        ensureSupportsMultipart(uploadSession);
        if (isExpired(uploadSession)) {
            throw new UploadSessionInvalidRequestException("upload session expired: " + uploadSessionId);
        }
        if (uploadSession.status() != UploadSessionStatus.UPLOADING
                && uploadSession.status() != UploadSessionStatus.INITIATED) {
            throw new UploadSessionInvalidRequestException("upload session is not accepting part uploads: " + uploadSession.status());
        }
        validatePartNumbers(List.of(partNumber), uploadSession.totalParts());

        String bucketName = objectStoragePort.resolveBucketName(uploadSession.targetAccessLevel());
        return objectStoragePort.uploadPart(
                bucketName,
                uploadSession.objectKey(),
                uploadSession.providerUploadId(),
                partNumber,
                data
        );
    }

    public void abortSession(String tenantId, String uploadSessionId, String subjectId) {
        UploadSession uploadSession = loadOwnedSession(tenantId, uploadSessionId, subjectId);
        if (uploadSession.status() == UploadSessionStatus.COMPLETED) {
            throw new UploadSessionInvalidRequestException("completed upload session cannot be aborted: " + uploadSessionId);
        }
        if (uploadSession.status() == UploadSessionStatus.ABORTED
                || uploadSession.status() == UploadSessionStatus.EXPIRED
                || uploadSession.status() == UploadSessionStatus.FAILED) {
            return;
        }

        if (uploadSession.hasMultipartUpload()) {
            String bucketName = objectStoragePort.resolveBucketName(uploadSession.targetAccessLevel());
            objectStoragePort.abortMultipartUpload(bucketName, uploadSession.objectKey(), uploadSession.providerUploadId());
        } else if (requiresSingleObjectUpload(uploadSession.uploadMode()) && uploadSession.objectKey() != null) {
            String bucketName = objectStoragePort.resolveBucketName(uploadSession.targetAccessLevel());
            deleteQuietly(bucketName, uploadSession.objectKey());
        }
        if (!uploadSessionRepository.updateStatus(uploadSessionId, UploadSessionStatus.ABORTED)) {
            throw new UploadSessionMutationException("failed to abort upload session: " + uploadSessionId);
        }
    }

    public UploadProgress getUploadProgress(String tenantId, String uploadSessionId, String subjectId) {
        UploadSession uploadSession = loadOwnedSession(tenantId, uploadSessionId, subjectId);
        ensureSupportsMultipart(uploadSession);
        if (isExpired(uploadSession) && uploadSession.status() != UploadSessionStatus.COMPLETED) {
            throw new UploadSessionInvalidRequestException("upload session expired: " + uploadSessionId);
        }
        List<UploadedPart> uploadedParts = loadUploadedParts(uploadSession);
        long uploadedBytes = uploadedParts.stream().mapToLong(UploadedPart::sizeBytes).sum();
        long boundedUploadedBytes = Math.min(uploadedBytes, uploadSession.expectedSize());
        int percentage = uploadSession.expectedSize() <= 0
                ? 0
                : Math.toIntExact(Math.min((boundedUploadedBytes * 100L) / uploadSession.expectedSize(), 100L));
        return new UploadProgress(
                uploadSession.uploadSessionId(),
                uploadSession.totalParts(),
                uploadedParts.size(),
                boundedUploadedBytes,
                uploadSession.expectedSize(),
                percentage,
                uploadedParts
        );
    }

    public UploadCompletion completeSession(String tenantId,
                                            String uploadSessionId,
                                            String subjectId,
                                            String contentType,
                                            List<CompletedUploadPart> completedUploadParts) {
        UploadSession uploadSession = loadOwnedSession(tenantId, uploadSessionId, subjectId);
        ensureSupportsMultipart(uploadSession);
        UploadCompletion existingCompletion = existingCompletion(uploadSession);
        if (existingCompletion != null) {
            return existingCompletion;
        }
        if (isExpired(uploadSession)) {
            throw new UploadSessionInvalidRequestException("upload session expired: " + uploadSessionId);
        }
        if (uploadSession.status() == UploadSessionStatus.COMPLETING) {
            return waitForCompletion(uploadSessionId);
        }
        if (uploadSession.status() != UploadSessionStatus.UPLOADING
                && uploadSession.status() != UploadSessionStatus.INITIATED) {
            throw new UploadSessionInvalidRequestException("upload session is not ready to complete: " + uploadSession.status());
        }
        if (!uploadSessionRepository.markCompleting(uploadSessionId)) {
            return waitForCompletion(uploadSessionId);
        }

        List<UploadedPart> authoritativeParts = loadUploadedParts(uploadSession);
        validateCompletionParts(uploadSession, completedUploadParts, authoritativeParts);
        if (authoritativeParts.size() != uploadSession.totalParts()) {
            throw new UploadSessionInvalidRequestException(
                    "uploaded parts incomplete: " + authoritativeParts.size() + "/" + uploadSession.totalParts()
            );
        }

        String bucketName = objectStoragePort.resolveBucketName(uploadSession.targetAccessLevel());
        objectStoragePort.completeMultipartUpload(
                bucketName,
                uploadSession.objectKey(),
                uploadSession.providerUploadId(),
                authoritativeParts
        );

        Instant now = clockPort.now();
        String hashValue = requireFileHash(uploadSession);
        AtomicReference<String> fileIdRef = new AtomicReference<>();
        AtomicReference<BlobObject> deduplicatedBlobRef = new AtomicReference<>();
        transactionOperations.executeWithoutResult(status -> {
            BlobObject blobObject = blobObjectRepository.findByHash(uploadSession.tenantId(), hashValue, bucketName)
                    .filter(existingBlob -> !existingBlob.objectKey().equals(uploadSession.objectKey()))
                    .map(existingBlob -> {
                        if (!blobObjectRepository.incrementReferenceCount(existingBlob.blobObjectId())) {
                            throw new UploadSessionMutationException("failed to increment blob reference: " + existingBlob.blobObjectId());
                        }
                        deduplicatedBlobRef.set(existingBlob);
                        return existingBlob;
                    })
                    .orElseGet(() -> blobObjectRepository.save(buildBlobObject(uploadSession, contentType, hashValue, bucketName, now)));

            FileAsset fileAsset = fileAssetRepository.save(buildFileAsset(uploadSession, subjectId, blobObject, contentType, now));
            if (!uploadSessionRepository.markCompleted(uploadSession.uploadSessionId(), fileAsset.fileId())) {
                throw new UploadSessionMutationException("failed to mark upload session completed: " + uploadSession.uploadSessionId());
            }
            fileIdRef.set(fileAsset.fileId());
        });

        if (deduplicatedBlobRef.get() != null) {
            deleteQuietly(bucketName, uploadSession.objectKey());
        }
        return new UploadCompletion(uploadSession.uploadSessionId(), fileIdRef.get(), UploadSessionStatus.COMPLETED);
    }

    public UploadCompletion completeSingleUpload(String tenantId,
                                                 String uploadSessionId,
                                                 String subjectId,
                                                 String contentType) {
        UploadSession uploadSession = loadOwnedSession(tenantId, uploadSessionId, subjectId);
        UploadCompletion existingCompletion = existingCompletion(uploadSession);
        if (existingCompletion != null) {
            return existingCompletion;
        }
        if (!requiresSingleObjectUpload(uploadSession.uploadMode())) {
            throw new UploadSessionInvalidRequestException("upload mode does not support single upload completion: " + uploadSession.uploadMode());
        }
        if (isExpired(uploadSession)) {
            throw new UploadSessionInvalidRequestException("upload session expired: " + uploadSessionId);
        }
        if (uploadSession.status() == UploadSessionStatus.COMPLETING) {
            return waitForCompletion(uploadSessionId);
        }
        if (uploadSession.status() != UploadSessionStatus.INITIATED
                && uploadSession.status() != UploadSessionStatus.UPLOADING) {
            throw new UploadSessionInvalidRequestException("upload session is not ready to complete: " + uploadSession.status());
        }
        if (!uploadSessionRepository.markCompleting(uploadSessionId)) {
            return waitForCompletion(uploadSessionId);
        }

        String bucketName = objectStoragePort.resolveBucketName(uploadSession.targetAccessLevel());
        StoredObjectMetadata objectMetadata = objectStoragePort.getObjectMetadata(bucketName, uploadSession.objectKey());
        if (objectMetadata.sizeBytes() != uploadSession.expectedSize()) {
            throw new UploadSessionInvalidRequestException("existing blob size mismatch for fileHash");
        }

        Instant now = clockPort.now();
        String hashValue = requireFileHash(uploadSession);
        String effectiveContentType = objectMetadata.contentType() != null && !objectMetadata.contentType().isBlank()
                ? objectMetadata.contentType()
                : contentType;
        AtomicReference<String> fileIdRef = new AtomicReference<>();
        AtomicReference<BlobObject> deduplicatedBlobRef = new AtomicReference<>();
        transactionOperations.executeWithoutResult(status -> {
            BlobObject blobObject = blobObjectRepository.findByHash(uploadSession.tenantId(), hashValue, bucketName)
                    .filter(existingBlob -> !existingBlob.objectKey().equals(uploadSession.objectKey()))
                    .map(existingBlob -> {
                        if (!blobObjectRepository.incrementReferenceCount(existingBlob.blobObjectId())) {
                            throw new UploadSessionMutationException("failed to increment blob reference: " + existingBlob.blobObjectId());
                        }
                        deduplicatedBlobRef.set(existingBlob);
                        return existingBlob;
                    })
                    .orElseGet(() -> blobObjectRepository.save(buildBlobObject(
                            uploadSession,
                            effectiveContentType,
                            hashValue,
                            bucketName,
                            now
                    )));

            FileAsset fileAsset = fileAssetRepository.save(buildFileAsset(uploadSession, subjectId, blobObject, effectiveContentType, now));
            if (!uploadSessionRepository.markCompleted(uploadSession.uploadSessionId(), fileAsset.fileId())) {
                throw new UploadSessionMutationException("failed to mark upload session completed: " + uploadSession.uploadSessionId());
            }
            fileIdRef.set(fileAsset.fileId());
        });

        if (deduplicatedBlobRef.get() != null) {
            deleteQuietly(bucketName, uploadSession.objectKey());
        }
        return new UploadCompletion(uploadSession.uploadSessionId(), fileIdRef.get(), UploadSessionStatus.COMPLETED);
    }

    private UploadSession loadOwnedSession(String tenantId, String uploadSessionId, String subjectId) {
        UploadSession uploadSession = uploadSessionRepository.findById(uploadSessionId)
                .orElseThrow(() -> new UploadSessionNotFoundException("uploadSessionId not found: " + uploadSessionId));

        if (!tenantId.equals(uploadSession.tenantId())) {
            throw new UploadSessionNotFoundException("uploadSessionId not found: " + uploadSessionId);
        }
        if (subjectId == null || !subjectId.equals(uploadSession.ownerId())) {
            throw new UploadSessionAccessDeniedException("access denied for uploadSessionId: " + uploadSessionId);
        }
        return uploadSession;
    }

    private UploadSession decorateExpiredStatusIfNeeded(UploadSession uploadSession) {
        if (isExpired(uploadSession) && !uploadSession.isTerminal()) {
            return withStatus(uploadSession, UploadSessionStatus.EXPIRED);
        }
        return uploadSession;
    }

    private boolean isExpired(UploadSession uploadSession) {
        return uploadSession.expiresAt() != null && clockPort.now().isAfter(uploadSession.expiresAt());
    }

    private boolean requiresMultipartUpload(UploadMode uploadMode) {
        return uploadMode == UploadMode.DIRECT;
    }

    private boolean requiresSingleObjectUpload(UploadMode uploadMode) {
        return uploadMode == UploadMode.PRESIGNED_SINGLE;
    }

    private Optional<UploadSession> findReusableSession(String tenantId,
                                                        String ownerId,
                                                        UploadMode uploadMode,
                                                        AccessLevel accessLevel,
                                                        long expectedSize,
                                                        String fileHash,
                                                        Instant now) {
        if ((!requiresMultipartUpload(uploadMode) && !requiresSingleObjectUpload(uploadMode))
                || fileHash == null || fileHash.isBlank()) {
            return Optional.empty();
        }

        Optional<UploadSession> existingSession = uploadSessionRepository.findActiveByHash(tenantId, ownerId, fileHash);
        if (existingSession.isEmpty()) {
            return Optional.empty();
        }

        UploadSession uploadSession = existingSession.get();
        if (uploadSession.expiresAt() != null && now.isAfter(uploadSession.expiresAt())) {
            expireStaleSession(uploadSession);
            return Optional.empty();
        }
        if (uploadSession.expectedSize() != expectedSize) {
            throw new UploadSessionInvalidRequestException("existing upload session size mismatch for fileHash");
        }
        if (uploadSession.uploadMode() != uploadMode) {
            throw new UploadSessionInvalidRequestException("existing upload session mode mismatch for fileHash");
        }
        if (uploadSession.targetAccessLevel() != accessLevel) {
            throw new UploadSessionInvalidRequestException("existing upload session access level mismatch for fileHash");
        }
        return Optional.of(uploadSession);
    }

    private Optional<UploadSession> createInstantUploadSessionIfPossible(String tenantId,
                                                                         String ownerId,
                                                                         UploadMode uploadMode,
                                                                         AccessLevel accessLevel,
                                                                         String originalFilename,
                                                                         String contentType,
                                                                         long expectedSize,
                                                                         String fileHash,
                                                                         Duration ttl,
                                                                         Instant now) {
        if (!requiresMultipartUpload(uploadMode) || fileHash == null || fileHash.isBlank()) {
            return Optional.empty();
        }
        String bucketName = objectStoragePort.resolveBucketName(accessLevel);
        Optional<BlobObject> existingBlob = blobObjectRepository.findByHash(tenantId, fileHash, bucketName);
        if (existingBlob.isEmpty()) {
            return Optional.empty();
        }

        BlobObject blobObject = existingBlob.get();
        if (blobObject.fileSize() != expectedSize) {
            throw new UploadSessionInvalidRequestException("existing blob size mismatch for fileHash");
        }

        AtomicReference<UploadSession> uploadSessionRef = new AtomicReference<>();
        transactionOperations.executeWithoutResult(status -> {
            if (!blobObjectRepository.incrementReferenceCount(blobObject.blobObjectId())) {
                throw new UploadSessionMutationException("failed to increment blob reference: " + blobObject.blobObjectId());
            }
            FileAsset fileAsset = fileAssetRepository.save(buildFileAsset(
                    tenantId,
                    ownerId,
                    originalFilename,
                    blobObject,
                    blobObject.contentType() != null ? blobObject.contentType() : contentType,
                    expectedSize,
                    accessLevel,
                    now
            ));
            UploadSession uploadSession = uploadSessionRepository.save(new UploadSession(
                    UUID.randomUUID().toString(),
                    tenantId,
                    ownerId,
                    uploadMode,
                    accessLevel,
                    originalFilename,
                    blobObject.contentType() != null ? blobObject.contentType() : contentType,
                    expectedSize,
                    fileHash,
                    blobObject.objectKey(),
                    0,
                    0,
                    null,
                    fileAsset.fileId(),
                    UploadSessionStatus.COMPLETED,
                    now,
                    now,
                    now.plus(ttl)
            ));
            uploadSessionRef.set(uploadSession);
        });
        return Optional.of(uploadSessionRef.get());
    }

    private UploadSessionCreationResult recoverFromActiveSessionConflict(String tenantId,
                                                                         String ownerId,
                                                                         UploadMode uploadMode,
                                                                         AccessLevel accessLevel,
                                                                         long expectedSize,
                                                                         String fileHash,
                                                                         Instant now,
                                                                         UploadSessionMutationException ex) {
        if (!isActiveSessionConflict(ex, fileHash)) {
            return null;
        }

        Optional<UploadSession> resumableSession = findReusableSession(
                tenantId,
                ownerId,
                uploadMode,
                accessLevel,
                expectedSize,
                fileHash,
                now
        );
        if (resumableSession.isEmpty()) {
            return null;
        }

        UploadSession uploadSession = resumableSession.get();
        return new UploadSessionCreationResult(uploadSession, true, false, loadUploadedParts(uploadSession));
    }

    private boolean isActiveSessionConflict(UploadSessionMutationException ex, String fileHash) {
        if (fileHash == null || fileHash.isBlank()) {
            return false;
        }
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && (message.contains(ACTIVE_UPLOAD_SESSION_UNIQUE_INDEX)
                    || message.contains("duplicate key value violates unique constraint"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void expireStaleSession(UploadSession uploadSession) {
        if (uploadSession.hasMultipartUpload()) {
            String bucketName = objectStoragePort.resolveBucketName(uploadSession.targetAccessLevel());
            objectStoragePort.abortMultipartUpload(bucketName, uploadSession.objectKey(), uploadSession.providerUploadId());
        }
        if (!uploadSessionRepository.updateStatus(uploadSession.uploadSessionId(), UploadSessionStatus.EXPIRED)) {
            throw new UploadSessionMutationException("failed to expire stale upload session: " + uploadSession.uploadSessionId());
        }
    }

    private void ensureSupportsMultipart(UploadSession uploadSession) {
        if (!uploadSession.hasMultipartUpload()) {
            throw new UploadSessionInvalidRequestException("upload session does not have multipart upload context: " + uploadSession.uploadSessionId());
        }
    }

    private void validateMultipartConfiguration(int chunkSizeBytes, int maxParts) {
        if (chunkSizeBytes <= 0) {
            throw new IllegalArgumentException("chunkSizeBytes must be greater than zero");
        }
        if (maxParts <= 0) {
            throw new IllegalArgumentException("maxParts must be greater than zero");
        }
    }

    private int calculateTotalParts(long expectedSize, int chunkSizeBytes, int maxParts) {
        long totalParts = (expectedSize + chunkSizeBytes - 1L) / chunkSizeBytes;
        if (totalParts <= 0) {
            throw new UploadSessionInvalidRequestException("expectedSize must be greater than zero");
        }
        if (totalParts > maxParts) {
            throw new UploadSessionInvalidRequestException("expected part count exceeds maxParts: " + totalParts);
        }
        return Math.toIntExact(totalParts);
    }

    private void validatePartNumbers(List<Integer> partNumbers, int totalParts) {
        if (partNumbers == null || partNumbers.isEmpty()) {
            throw new UploadSessionInvalidRequestException("partNumbers must not be empty");
        }
        if (totalParts <= 0) {
            throw new UploadSessionInvalidRequestException("upload session does not have multipart parts");
        }

        HashSet<Integer> uniquePartNumbers = new HashSet<>();
        for (Integer partNumber : partNumbers) {
            if (partNumber == null) {
                throw new UploadSessionInvalidRequestException("partNumber must not be null");
            }
            if (!uniquePartNumbers.add(partNumber)) {
                throw new UploadSessionInvalidRequestException("duplicate partNumber: " + partNumber);
            }
            if (partNumber < 1 || partNumber > totalParts) {
                throw new UploadSessionInvalidRequestException("partNumber out of range: " + partNumber);
            }
        }
    }

    private void validateCompletionParts(UploadSession uploadSession,
                                         List<CompletedUploadPart> completedUploadParts,
                                         List<UploadedPart> authoritativeParts) {
        if (completedUploadParts == null || completedUploadParts.isEmpty()) {
            return;
        }
        HashSet<Integer> visited = new HashSet<>();
        for (CompletedUploadPart completedUploadPart : completedUploadParts) {
            int partNumber = completedUploadPart.partNumber();
            if (!visited.add(partNumber)) {
                throw new UploadSessionInvalidRequestException("duplicate partNumber: " + partNumber);
            }
            if (partNumber < 1 || partNumber > uploadSession.totalParts()) {
                throw new UploadSessionInvalidRequestException("partNumber out of range: " + partNumber);
            }
            UploadedPart authoritativePart = authoritativeParts.stream()
                    .filter(part -> part.partNumber() == partNumber)
                    .findFirst()
                    .orElseThrow(() -> new UploadSessionInvalidRequestException("part not found in storage: " + partNumber));
            if (completedUploadPart.etag() != null && !completedUploadPart.etag().equals(authoritativePart.etag())) {
                throw new UploadSessionInvalidRequestException("part etag mismatch: " + partNumber);
            }
        }
    }

    private List<UploadedPart> loadUploadedParts(UploadSession uploadSession) {
        String bucketName = objectStoragePort.resolveBucketName(uploadSession.targetAccessLevel());
        return objectStoragePort.listUploadedParts(
                bucketName,
                uploadSession.objectKey(),
                uploadSession.providerUploadId()
        ).stream().sorted(Comparator.comparingInt(UploadedPart::partNumber)).toList();
    }

    private String requireFileHash(UploadSession uploadSession) {
        if (uploadSession.fileHash() == null || uploadSession.fileHash().isBlank()) {
            throw new UploadSessionInvalidRequestException("upload session fileHash is required to complete upload");
        }
        return uploadSession.fileHash();
    }

    private BlobObject buildBlobObject(UploadSession uploadSession,
                                       String contentType,
                                       String hashValue,
                                       String bucketName,
                                       Instant now) {
        return new BlobObject(
                UUID.randomUUID().toString(),
                uploadSession.tenantId(),
                LEGACY_STORAGE_PROVIDER,
                bucketName,
                uploadSession.objectKey(),
                hashValue,
                DEFAULT_HASH_ALGORITHM,
                uploadSession.expectedSize(),
                contentType,
                1,
                now,
                now
        );
    }

    private FileAsset buildFileAsset(UploadSession uploadSession,
                                     String subjectId,
                                     BlobObject blobObject,
                                     String contentType,
                                     Instant now) {
        return buildFileAsset(
                uploadSession.tenantId(),
                subjectId,
                uploadSession.originalFilename(),
                blobObject,
                contentType,
                uploadSession.expectedSize(),
                uploadSession.targetAccessLevel(),
                now
        );
    }

    private FileAsset buildFileAsset(String tenantId,
                                     String ownerId,
                                     String originalFilename,
                                     BlobObject blobObject,
                                     String contentType,
                                     long fileSize,
                                     AccessLevel accessLevel,
                                     Instant now) {
        return new FileAsset(
                UUID.randomUUID().toString(),
                tenantId,
                ownerId,
                blobObject.blobObjectId(),
                originalFilename,
                blobObject.objectKey(),
                contentType,
                fileSize,
                accessLevel,
                FileAssetStatus.ACTIVE,
                now,
                now
        );
    }

    private String buildObjectKey(String tenantId, String ownerId, String originalFilename, Instant now) {
        ZonedDateTime dateTime = ZonedDateTime.ofInstant(now, ZoneOffset.UTC);
        return String.format(
                "%s/%04d/%02d/%02d/%s/uploads/%s-%s",
                tenantId,
                dateTime.getYear(),
                dateTime.getMonthValue(),
                dateTime.getDayOfMonth(),
                ownerId,
                UUID.randomUUID(),
                sanitizeFilename(originalFilename)
        );
    }

    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "file.bin";
        }
        String sanitized = INVALID_FILENAME_CHARS.matcher(originalFilename.trim()).replaceAll("-");
        sanitized = EDGE_FILENAME_CHARS.matcher(sanitized).replaceAll("");
        if (sanitized.isBlank()) {
            return "file.bin";
        }
        if (sanitized.length() > MAX_FILENAME_LENGTH) {
            return sanitized.substring(0, MAX_FILENAME_LENGTH);
        }
        return sanitized;
    }

    private void deleteQuietly(String bucketName, String objectKey) {
        try {
            objectStoragePort.deleteObject(bucketName, objectKey);
        } catch (RuntimeException ignored) {
            // 合并已完成后的清理由后台对账/GC 兜底。
        }
    }

    private UploadCompletion existingCompletion(UploadSession uploadSession) {
        if (uploadSession.status() != UploadSessionStatus.COMPLETED) {
            return null;
        }
        if (uploadSession.fileId() == null || uploadSession.fileId().isBlank()) {
            throw new UploadSessionMutationException(
                    "completed upload session is missing fileId: " + uploadSession.uploadSessionId()
            );
        }
        return new UploadCompletion(
                uploadSession.uploadSessionId(),
                uploadSession.fileId(),
                UploadSessionStatus.COMPLETED
        );
    }

    private UploadCompletion waitForCompletion(String uploadSessionId) {
        long deadlineNanos = System.nanoTime() + completionWaitTimeout.toNanos();
        while (System.nanoTime() <= deadlineNanos) {
            UploadSession latestSession = uploadSessionRepository.findById(uploadSessionId)
                    .orElseThrow(() -> new UploadSessionMutationException(
                            "upload session disappeared while waiting for completion: " + uploadSessionId
                    ));
            UploadCompletion existingCompletion = existingCompletion(latestSession);
            if (existingCompletion != null) {
                return existingCompletion;
            }
            if (latestSession.status() == UploadSessionStatus.ABORTED
                    || latestSession.status() == UploadSessionStatus.EXPIRED
                    || latestSession.status() == UploadSessionStatus.FAILED) {
                throw new UploadSessionInvalidRequestException(
                        "upload session is not ready to complete: " + latestSession.status()
                );
            }
            if (latestSession.status() != UploadSessionStatus.COMPLETING) {
                throw new UploadSessionMutationException(
                        "upload session completion ownership lost: " + uploadSessionId + ", status=" + latestSession.status()
                );
            }
            sleepCompletionPollInterval(uploadSessionId);
        }
        throw new UploadSessionInvalidRequestException(
                "upload session completion still in progress, retry later: " + uploadSessionId
        );
    }

    private void sleepCompletionPollInterval(String uploadSessionId) {
        try {
            Thread.sleep(completionPollInterval.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new UploadSessionMutationException(
                    "interrupted while waiting for upload session completion: " + uploadSessionId,
                    ex
            );
        }
    }

    private UploadSession withStatus(UploadSession uploadSession, UploadSessionStatus status) {
        return new UploadSession(
                uploadSession.uploadSessionId(),
                uploadSession.tenantId(),
                uploadSession.ownerId(),
                uploadSession.uploadMode(),
                uploadSession.targetAccessLevel(),
                uploadSession.originalFilename(),
                uploadSession.contentType(),
                uploadSession.expectedSize(),
                uploadSession.fileHash(),
                uploadSession.objectKey(),
                uploadSession.chunkSizeBytes(),
                uploadSession.totalParts(),
                uploadSession.providerUploadId(),
                uploadSession.fileId(),
                status,
                uploadSession.createdAt(),
                uploadSession.updatedAt(),
                uploadSession.expiresAt()
        );
    }

    private Duration requirePositiveDuration(Duration duration, String fieldName) {
        Objects.requireNonNull(duration, fieldName + " must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }
        return duration;
    }
}
