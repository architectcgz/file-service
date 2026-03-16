package com.platform.fileservice.core.application.service;

import com.platform.fileservice.core.domain.exception.UploadSessionAccessDeniedException;
import com.platform.fileservice.core.domain.exception.UploadSessionInvalidRequestException;
import com.platform.fileservice.core.domain.exception.UploadSessionNotFoundException;
import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.BlobObject;
import com.platform.fileservice.core.domain.model.CompletedUploadPart;
import com.platform.fileservice.core.domain.model.FileAsset;
import com.platform.fileservice.core.domain.model.FileAssetStatus;
import com.platform.fileservice.core.domain.model.PartUploadUrlGrant;
import com.platform.fileservice.core.domain.model.SingleUploadUrlGrant;
import com.platform.fileservice.core.domain.model.UploadCompletion;
import com.platform.fileservice.core.domain.model.UploadMode;
import com.platform.fileservice.core.domain.model.UploadProgress;
import com.platform.fileservice.core.domain.model.StoredObjectMetadata;
import com.platform.fileservice.core.domain.model.UploadSession;
import com.platform.fileservice.core.domain.model.UploadSessionCreationResult;
import com.platform.fileservice.core.domain.model.UploadSessionStatus;
import com.platform.fileservice.core.domain.model.UploadedPart;
import com.platform.fileservice.core.ports.repository.BlobObjectRepository;
import com.platform.fileservice.core.ports.repository.FileAssetRepository;
import com.platform.fileservice.core.ports.repository.UploadSessionRepository;
import com.platform.fileservice.core.ports.storage.ObjectStoragePort;
import com.platform.fileservice.core.ports.system.ClockPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UploadAppServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-14T03:00:00Z");
    private static final int CHUNK_SIZE_BYTES = 5 * 1024 * 1024;

    private UploadSessionRepository uploadSessionRepository;
    private BlobObjectRepository blobObjectRepository;
    private FileAssetRepository fileAssetRepository;
    private ObjectStoragePort objectStoragePort;
    private UploadAppService uploadAppService;

    @BeforeEach
    void setUp() {
        uploadSessionRepository = mock(UploadSessionRepository.class);
        blobObjectRepository = mock(BlobObjectRepository.class);
        fileAssetRepository = mock(FileAssetRepository.class);
        objectStoragePort = mock(ObjectStoragePort.class);
        when(uploadSessionRepository.findActiveByHash(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(blobObjectRepository.findByHash(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(objectStoragePort.resolveBucketName(any(AccessLevel.class))).thenReturn("bucket");
        ClockPort clockPort = () -> FIXED_NOW;
        uploadAppService = new UploadAppService(
                uploadSessionRepository,
                blobObjectRepository,
                fileAssetRepository,
                objectStoragePort,
                clockPort,
                new ImmediateTransactionOperations()
        );
    }

    @Test
    void shouldCreateUploadingMultipartSessionForDirectMode() {
        when(objectStoragePort.resolveBucketName(AccessLevel.PRIVATE)).thenReturn("private-bucket");
        when(objectStoragePort.createMultipartUpload(
                eq("private-bucket"),
                any(String.class),
                eq("video/mp4")
        )).thenReturn("upload-001");
        when(uploadSessionRepository.save(any(UploadSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UploadSessionCreationResult creationResult = uploadAppService.createSession(
                "blog",
                "user-001",
                UploadMode.DIRECT,
                AccessLevel.PRIVATE,
                "demo.mp4",
                "video/mp4",
                11L * 1024 * 1024,
                "hash-001",
                Duration.ofHours(24),
                CHUNK_SIZE_BYTES,
                10_000
        );
        UploadSession uploadSession = creationResult.uploadSession();

        assertEquals(UploadSessionStatus.UPLOADING, uploadSession.status());
        assertEquals(false, creationResult.resumed());
        assertEquals(false, creationResult.instantUpload());
        assertEquals("upload-001", uploadSession.providerUploadId());
        assertEquals(CHUNK_SIZE_BYTES, uploadSession.chunkSizeBytes());
        assertEquals(3, uploadSession.totalParts());
        assertTrue(uploadSession.objectKey().startsWith("blog/2026/03/14/user-001/uploads/"));
        assertTrue(uploadSession.objectKey().endsWith("-demo.mp4"));
        verify(uploadSessionRepository).save(any(UploadSession.class));
    }

    @Test
    void shouldCreateInitiatedSessionForInlineMode() {
        when(uploadSessionRepository.save(any(UploadSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UploadSessionCreationResult creationResult = uploadAppService.createSession(
                "blog",
                "user-001",
                UploadMode.INLINE,
                AccessLevel.PUBLIC,
                "note.txt",
                "text/plain",
                128L,
                null,
                Duration.ofHours(1),
                CHUNK_SIZE_BYTES,
                10_000
        );
        UploadSession uploadSession = creationResult.uploadSession();

        assertEquals(UploadSessionStatus.INITIATED, uploadSession.status());
        assertEquals(0, uploadSession.chunkSizeBytes());
        assertEquals(0, uploadSession.totalParts());
        assertEquals(null, uploadSession.objectKey());
        assertEquals(null, uploadSession.providerUploadId());
        verifyNoInteractions(objectStoragePort);
    }

    @Test
    void shouldCreateSingleObjectSessionForPresignedMode() {
        when(uploadSessionRepository.save(any(UploadSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UploadSessionCreationResult creationResult = uploadAppService.createSession(
                "blog",
                "user-001",
                UploadMode.PRESIGNED_SINGLE,
                AccessLevel.PUBLIC,
                "avatar.png",
                "image/png",
                2048L,
                "hash-002",
                Duration.ofHours(1),
                CHUNK_SIZE_BYTES,
                10_000
        );
        UploadSession uploadSession = creationResult.uploadSession();

        assertEquals(UploadSessionStatus.INITIATED, uploadSession.status());
        assertEquals(1, uploadSession.totalParts());
        assertEquals(null, uploadSession.providerUploadId());
        assertTrue(uploadSession.objectKey().startsWith("blog/2026/03/14/user-001/uploads/"));
        verify(objectStoragePort, never()).createMultipartUpload(anyString(), anyString(), anyString());
    }

    @Test
    void shouldRejectPartCountAboveConfiguredLimit() {
        assertThrows(UploadSessionInvalidRequestException.class, () -> uploadAppService.createSession(
                "blog",
                "user-001",
                UploadMode.DIRECT,
                AccessLevel.PRIVATE,
                "demo.mp4",
                "video/mp4",
                (long) CHUNK_SIZE_BYTES * 2 + 1,
                "hash-001",
                Duration.ofHours(24),
                CHUNK_SIZE_BYTES,
                2
        ));

        verify(objectStoragePort, never()).createMultipartUpload(any(String.class), any(String.class), any(String.class));
    }

    @Test
    void shouldReuseExistingMultipartSessionByFileHash() {
        UploadSession existingSession = multipartSession(
                "session-001",
                UploadSessionStatus.UPLOADING,
                FIXED_NOW.plus(Duration.ofHours(1))
        );
        when(uploadSessionRepository.findActiveByHash("blog", "user-001", "hash-001"))
                .thenReturn(Optional.of(existingSession));
        when(objectStoragePort.listUploadedParts("bucket", "blog/2026/03/14/user-001/uploads/session-001-demo.mp4", "provider-001"))
                .thenReturn(List.of());

        UploadSessionCreationResult creationResult = uploadAppService.createSession(
                "blog",
                "user-001",
                UploadMode.DIRECT,
                AccessLevel.PRIVATE,
                "renamed.mp4",
                "video/mp4",
                11L * 1024 * 1024,
                "hash-001",
                Duration.ofHours(24),
                CHUNK_SIZE_BYTES,
                10_000
        );
        UploadSession uploadSession = creationResult.uploadSession();

        assertEquals("session-001", uploadSession.uploadSessionId());
        assertEquals(true, creationResult.resumed());
        assertEquals(0, creationResult.uploadedParts().size());
        verify(objectStoragePort, never()).createMultipartUpload(any(String.class), any(String.class), any(String.class));
        verify(uploadSessionRepository, never()).save(any(UploadSession.class));
    }

    @Test
    void shouldCreateCompletedSessionWhenBlobAlreadyExists() {
        BlobObject blobObject = new BlobObject(
                "blob-001",
                "blog",
                "legacy-s3",
                "private-bucket",
                "blog/2026/03/14/user-001/uploads/existing-demo.mp4",
                "hash-001",
                "MD5",
                11L * 1024 * 1024,
                "video/mp4",
                2,
                FIXED_NOW,
                FIXED_NOW
        );
        when(objectStoragePort.resolveBucketName(AccessLevel.PRIVATE)).thenReturn("private-bucket");
        when(blobObjectRepository.findByHash("blog", "hash-001", "private-bucket")).thenReturn(Optional.of(blobObject));
        when(blobObjectRepository.incrementReferenceCount("blob-001")).thenReturn(true);
        when(fileAssetRepository.save(any(FileAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadSessionCreationResult creationResult = uploadAppService.createSession(
                "blog",
                "user-001",
                UploadMode.DIRECT,
                AccessLevel.PRIVATE,
                "demo.mp4",
                "video/mp4",
                11L * 1024 * 1024,
                "hash-001",
                Duration.ofHours(24),
                CHUNK_SIZE_BYTES,
                10_000
        );
        UploadSession uploadSession = creationResult.uploadSession();

        assertEquals(UploadSessionStatus.COMPLETED, uploadSession.status());
        assertEquals(true, creationResult.instantUpload());
        assertTrue(uploadSession.hasCompletedFile());
        assertEquals("blog/2026/03/14/user-001/uploads/existing-demo.mp4", uploadSession.objectKey());
        verify(blobObjectRepository).incrementReferenceCount("blob-001");
        verify(fileAssetRepository).save(any(FileAsset.class));
        verify(objectStoragePort, never()).createMultipartUpload(anyString(), anyString(), anyString());
    }

    @Test
    void shouldExpireStaleSessionAndCreateNewOne() {
        when(uploadSessionRepository.findActiveByHash("blog", "user-001", "hash-001"))
                .thenReturn(Optional.of(multipartSession(
                        "session-old",
                        UploadSessionStatus.UPLOADING,
                        FIXED_NOW.minusSeconds(10)
                )));
        when(objectStoragePort.resolveBucketName(AccessLevel.PRIVATE)).thenReturn("private-bucket");
        when(uploadSessionRepository.updateStatus("session-old", UploadSessionStatus.EXPIRED)).thenReturn(true);
        when(objectStoragePort.createMultipartUpload(eq("private-bucket"), any(String.class), eq("video/mp4"))).thenReturn("upload-001");
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadSessionCreationResult creationResult = uploadAppService.createSession(
                "blog",
                "user-001",
                UploadMode.DIRECT,
                AccessLevel.PRIVATE,
                "demo.mp4",
                "video/mp4",
                11L * 1024 * 1024,
                "hash-001",
                Duration.ofHours(24),
                CHUNK_SIZE_BYTES,
                10_000
        );
        UploadSession uploadSession = creationResult.uploadSession();

        assertEquals(UploadSessionStatus.UPLOADING, uploadSession.status());
        verify(objectStoragePort).abortMultipartUpload(
                "private-bucket",
                "blog/2026/03/14/user-001/uploads/session-001-demo.mp4",
                "provider-001"
        );
        verify(uploadSessionRepository).updateStatus("session-old", UploadSessionStatus.EXPIRED);
        verify(uploadSessionRepository).save(any(UploadSession.class));
    }

    @Test
    void shouldRejectResumeWhenExistingSessionSizeDiffers() {
        when(uploadSessionRepository.findActiveByHash("blog", "user-001", "hash-001"))
                .thenReturn(Optional.of(multipartSession(
                        "session-001",
                        UploadSessionStatus.UPLOADING,
                        FIXED_NOW.plus(Duration.ofHours(1))
                )));

        assertThrows(UploadSessionInvalidRequestException.class, () -> uploadAppService.createSession(
                "blog",
                "user-001",
                UploadMode.DIRECT,
                AccessLevel.PRIVATE,
                "demo.mp4",
                "video/mp4",
                10L,
                "hash-001",
                Duration.ofHours(24),
                CHUNK_SIZE_BYTES,
                10_000
        ));
    }

    @Test
    void shouldIssuePresignedPartUrls() {
        when(uploadSessionRepository.findById("session-001"))
                .thenReturn(Optional.of(multipartSession(
                        "session-001",
                        UploadSessionStatus.UPLOADING,
                        FIXED_NOW.plus(Duration.ofHours(1))
                )));
        when(objectStoragePort.resolveBucketName(AccessLevel.PRIVATE)).thenReturn("private-bucket");
        when(objectStoragePort.generatePresignedUploadPartUrl(
                eq("private-bucket"),
                eq("blog/2026/03/14/user-001/uploads/session-001-demo.mp4"),
                eq("provider-001"),
                anyInt(),
                eq(Duration.ofMinutes(15))
        )).thenAnswer(invocation -> "https://upload.example.com/part/" + invocation.getArgument(3, Integer.class));

        PartUploadUrlGrant grant = uploadAppService.issuePartUploadUrls(
                "blog",
                "session-001",
                "user-001",
                List.of(1, 3),
                Duration.ofMinutes(15)
        );

        assertEquals("session-001", grant.uploadSessionId());
        assertEquals(2, grant.partUrls().size());
        assertEquals(1, grant.partUrls().get(0).partNumber());
        assertEquals("https://upload.example.com/part/1", grant.partUrls().get(0).uploadUrl());
        assertEquals(900, grant.partUrls().get(0).expiresInSeconds());
        assertEquals(3, grant.partUrls().get(1).partNumber());
    }

    @Test
    void shouldIssueSingleUploadUrlForPresignedSession() {
        when(uploadSessionRepository.findById("session-ps-001"))
                .thenReturn(Optional.of(singlePresignedSession(
                        "session-ps-001",
                        UploadSessionStatus.INITIATED,
                        FIXED_NOW.plus(Duration.ofHours(1))
                )));
        when(objectStoragePort.resolveBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        when(objectStoragePort.generatePresignedPutObjectUrl(
                eq("public-bucket"),
                eq("blog/2026/03/14/user-001/uploads/session-ps-001-avatar.png"),
                eq("image/png"),
                eq(Duration.ofMinutes(15))
        )).thenReturn("https://upload.example.com/object");

        SingleUploadUrlGrant grant = uploadAppService.issueSingleUploadUrl(
                "blog",
                "session-ps-001",
                "user-001",
                Duration.ofMinutes(15)
        );

        assertEquals("session-ps-001", grant.uploadSessionId());
        assertEquals("https://upload.example.com/object", grant.uploadUrl());
        assertEquals(900, grant.expiresInSeconds());
    }

    @Test
    void shouldRejectDuplicatePartNumbers() {
        when(uploadSessionRepository.findById("session-001"))
                .thenReturn(Optional.of(multipartSession(
                        "session-001",
                        UploadSessionStatus.UPLOADING,
                        FIXED_NOW.plus(Duration.ofHours(1))
                )));

        assertThrows(UploadSessionInvalidRequestException.class, () -> uploadAppService.issuePartUploadUrls(
                "blog",
                "session-001",
                "user-001",
                List.of(1, 1),
                Duration.ofMinutes(15)
        ));
    }

    @Test
    void shouldAbortMultipartUploadSession() {
        when(uploadSessionRepository.findById("session-001"))
                .thenReturn(Optional.of(multipartSession(
                        "session-001",
                        UploadSessionStatus.UPLOADING,
                        FIXED_NOW.plus(Duration.ofHours(1))
                )));
        when(objectStoragePort.resolveBucketName(AccessLevel.PRIVATE)).thenReturn("private-bucket");
        when(uploadSessionRepository.updateStatus("session-001", UploadSessionStatus.ABORTED)).thenReturn(true);

        uploadAppService.abortSession("blog", "session-001", "user-001");

        verify(objectStoragePort).abortMultipartUpload(
                "private-bucket",
                "blog/2026/03/14/user-001/uploads/session-001-demo.mp4",
                "provider-001"
        );
        verify(uploadSessionRepository).updateStatus("session-001", UploadSessionStatus.ABORTED);
    }

    @Test
    void shouldDeleteSingleUploadObjectWhenAbortingPresignedSession() {
        when(uploadSessionRepository.findById("session-ps-001"))
                .thenReturn(Optional.of(singlePresignedSession(
                        "session-ps-001",
                        UploadSessionStatus.UPLOADING,
                        FIXED_NOW.plus(Duration.ofHours(1))
                )));
        when(objectStoragePort.resolveBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        when(uploadSessionRepository.updateStatus("session-ps-001", UploadSessionStatus.ABORTED)).thenReturn(true);

        uploadAppService.abortSession("blog", "session-ps-001", "user-001");

        verify(objectStoragePort).deleteObject(
                "public-bucket",
                "blog/2026/03/14/user-001/uploads/session-ps-001-avatar.png"
        );
    }

    @Test
    void shouldReturnProgressFromAuthoritativeUploadedParts() {
        when(uploadSessionRepository.findById("session-001"))
                .thenReturn(Optional.of(multipartSession(
                        "session-001",
                        UploadSessionStatus.UPLOADING,
                        FIXED_NOW.plus(Duration.ofHours(1))
                )));
        when(objectStoragePort.resolveBucketName(AccessLevel.PRIVATE)).thenReturn("private-bucket");
        when(objectStoragePort.listUploadedParts(
                "private-bucket",
                "blog/2026/03/14/user-001/uploads/session-001-demo.mp4",
                "provider-001"
        )).thenReturn(List.of(
                new UploadedPart(1, "etag-1", CHUNK_SIZE_BYTES),
                new UploadedPart(2, "etag-2", CHUNK_SIZE_BYTES)
        ));

        UploadProgress uploadProgress = uploadAppService.getUploadProgress("blog", "session-001", "user-001");

        assertEquals("session-001", uploadProgress.uploadSessionId());
        assertEquals(3, uploadProgress.totalParts());
        assertEquals(2, uploadProgress.completedParts());
        assertEquals(10L * 1024 * 1024, uploadProgress.uploadedBytes());
        assertEquals(11L * 1024 * 1024, uploadProgress.totalBytes());
        assertEquals(90, uploadProgress.percentage());
    }

    @Test
    void shouldCompleteUploadSessionAndPersistBlobAndFileAsset() {
        when(uploadSessionRepository.findById("session-001"))
                .thenReturn(Optional.of(multipartSession(
                        "session-001",
                        UploadSessionStatus.UPLOADING,
                        FIXED_NOW.plus(Duration.ofHours(1))
                )));
        when(objectStoragePort.resolveBucketName(AccessLevel.PRIVATE)).thenReturn("private-bucket");
        List<UploadedPart> authoritativeParts = List.of(
                new UploadedPart(1, "etag-1", CHUNK_SIZE_BYTES),
                new UploadedPart(2, "etag-2", CHUNK_SIZE_BYTES),
                new UploadedPart(3, "etag-3", 1024 * 1024)
        );
        when(objectStoragePort.listUploadedParts(
                "private-bucket",
                "blog/2026/03/14/user-001/uploads/session-001-demo.mp4",
                "provider-001"
        )).thenReturn(authoritativeParts);
        when(uploadSessionRepository.markCompleting("session-001")).thenReturn(true);
        when(blobObjectRepository.findByHash("blog", "hash-001", "private-bucket")).thenReturn(Optional.empty());
        when(blobObjectRepository.save(any(BlobObject.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileAssetRepository.save(any(FileAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(uploadSessionRepository.markCompleted(eq("session-001"), anyString())).thenReturn(true);

        UploadCompletion uploadCompletion = uploadAppService.completeSession(
                "blog",
                "session-001",
                "user-001",
                "video/mp4",
                List.of(
                        new CompletedUploadPart(1, "etag-1"),
                        new CompletedUploadPart(2, "etag-2"),
                        new CompletedUploadPart(3, "etag-3")
                )
        );

        assertEquals("session-001", uploadCompletion.uploadSessionId());
        assertEquals(UploadSessionStatus.COMPLETED, uploadCompletion.status());
        verify(objectStoragePort).completeMultipartUpload(
                "private-bucket",
                "blog/2026/03/14/user-001/uploads/session-001-demo.mp4",
                "provider-001",
                authoritativeParts
        );
        verify(uploadSessionRepository).markCompleting("session-001");
        verify(blobObjectRepository).save(any(BlobObject.class));
        verify(fileAssetRepository).save(any(FileAsset.class));
        verify(uploadSessionRepository).markCompleted(eq("session-001"), anyString());
    }

    @Test
    void shouldCompleteSingleUploadAndPersistBlobAndFileAsset() {
        when(uploadSessionRepository.findById("session-ps-001"))
                .thenReturn(Optional.of(singlePresignedSession(
                        "session-ps-001",
                        UploadSessionStatus.UPLOADING,
                        FIXED_NOW.plus(Duration.ofHours(1))
                )));
        when(objectStoragePort.resolveBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        when(objectStoragePort.getObjectMetadata(
                "public-bucket",
                "blog/2026/03/14/user-001/uploads/session-ps-001-avatar.png"
        )).thenReturn(new StoredObjectMetadata(2048L, "image/png"));
        when(uploadSessionRepository.markCompleting("session-ps-001")).thenReturn(true);
        when(blobObjectRepository.findByHash("blog", "hash-ps-001", "public-bucket")).thenReturn(Optional.empty());
        when(blobObjectRepository.save(any(BlobObject.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileAssetRepository.save(any(FileAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(uploadSessionRepository.markCompleted(eq("session-ps-001"), anyString())).thenReturn(true);

        UploadCompletion uploadCompletion = uploadAppService.completeSingleUpload(
                "blog",
                "session-ps-001",
                "user-001",
                "image/png"
        );

        assertEquals("session-ps-001", uploadCompletion.uploadSessionId());
        assertEquals(UploadSessionStatus.COMPLETED, uploadCompletion.status());
        verify(uploadSessionRepository).markCompleting("session-ps-001");
        verify(blobObjectRepository).save(any(BlobObject.class));
        verify(fileAssetRepository).save(any(FileAsset.class));
        verify(uploadSessionRepository).markCompleted(eq("session-ps-001"), anyString());
        verify(objectStoragePort, never()).completeMultipartUpload(anyString(), anyString(), anyString(), any());
    }

    @Test
    void shouldReuseExistingBlobWhenHashAlreadyExists() {
        when(uploadSessionRepository.findById("session-001"))
                .thenReturn(Optional.of(multipartSession(
                        "session-001",
                        UploadSessionStatus.UPLOADING,
                        FIXED_NOW.plus(Duration.ofHours(1))
                )));
        when(objectStoragePort.resolveBucketName(AccessLevel.PRIVATE)).thenReturn("private-bucket");
        List<UploadedPart> authoritativeParts = List.of(
                new UploadedPart(1, "etag-1", CHUNK_SIZE_BYTES),
                new UploadedPart(2, "etag-2", CHUNK_SIZE_BYTES),
                new UploadedPart(3, "etag-3", 1024 * 1024)
        );
        when(objectStoragePort.listUploadedParts(
                "private-bucket",
                "blog/2026/03/14/user-001/uploads/session-001-demo.mp4",
                "provider-001"
        )).thenReturn(authoritativeParts);
        when(uploadSessionRepository.markCompleting("session-001")).thenReturn(true);
        when(blobObjectRepository.findByHash("blog", "hash-001", "private-bucket"))
                .thenReturn(Optional.of(new BlobObject(
                        "blob-001",
                        "blog",
                        "legacy-s3",
                        "private-bucket",
                        "blog/2026/03/14/user-001/uploads/existing-demo.mp4",
                        "hash-001",
                        "MD5",
                        11L * 1024 * 1024,
                        "video/mp4",
                        3,
                        FIXED_NOW,
                        FIXED_NOW
                )));
        when(blobObjectRepository.incrementReferenceCount("blob-001")).thenReturn(true);
        when(fileAssetRepository.save(any(FileAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(uploadSessionRepository.markCompleted(eq("session-001"), anyString())).thenReturn(true);

        uploadAppService.completeSession(
                "blog",
                "session-001",
                "user-001",
                "video/mp4",
                List.of()
        );

        verify(blobObjectRepository).incrementReferenceCount("blob-001");
        verify(objectStoragePort).deleteObject("private-bucket", "blog/2026/03/14/user-001/uploads/session-001-demo.mp4");
        verify(blobObjectRepository, never()).save(any(BlobObject.class));
    }

    @Test
    void shouldReturnExistingCompletionWhenMultipartSessionAlreadyCompleted() {
        when(uploadSessionRepository.findById("session-001"))
                .thenReturn(Optional.of(completedMultipartSession("session-001", "file-001")));

        UploadCompletion uploadCompletion = uploadAppService.completeSession(
                "blog",
                "session-001",
                "user-001",
                "video/mp4",
                List.of()
        );

        assertEquals("file-001", uploadCompletion.fileId());
        verify(uploadSessionRepository, never()).markCompleting(anyString());
        verify(objectStoragePort, never()).completeMultipartUpload(anyString(), anyString(), anyString(), any());
    }

    @Test
    void shouldReturnExistingCompletionWhenAnotherInstanceIsFinishingMultipartSession() {
        when(uploadSessionRepository.findById("session-001"))
                .thenReturn(
                        Optional.of(multipartSession("session-001", UploadSessionStatus.UPLOADING, FIXED_NOW.plusSeconds(60))),
                        Optional.of(completedMultipartSession("session-001", "file-002"))
                );
        when(uploadSessionRepository.markCompleting("session-001")).thenReturn(false);

        UploadCompletion uploadCompletion = uploadAppService.completeSession(
                "blog",
                "session-001",
                "user-001",
                "video/mp4",
                List.of()
        );

        assertEquals("file-002", uploadCompletion.fileId());
        verify(objectStoragePort, never()).completeMultipartUpload(anyString(), anyString(), anyString(), any());
    }

    @Test
    void shouldAskCallerToRetryWhenAnotherInstanceKeepsCompletingMultipartSession() {
        UploadAppService shortWaitService = new UploadAppService(
                uploadSessionRepository,
                blobObjectRepository,
                fileAssetRepository,
                objectStoragePort,
                () -> FIXED_NOW,
                new ImmediateTransactionOperations(),
                Duration.ofMillis(1),
                Duration.ofMillis(1)
        );
        when(uploadSessionRepository.findById("session-001"))
                .thenReturn(Optional.of(multipartSession(
                        "session-001",
                        UploadSessionStatus.UPLOADING,
                        FIXED_NOW.plusSeconds(60)
                )))
                .thenReturn(Optional.of(multipartSession(
                        "session-001",
                        UploadSessionStatus.COMPLETING,
                        FIXED_NOW.plusSeconds(60)
                )));
        when(uploadSessionRepository.markCompleting("session-001")).thenReturn(false);

        UploadSessionInvalidRequestException exception = assertThrows(
                UploadSessionInvalidRequestException.class,
                () -> shortWaitService.completeSession("blog", "session-001", "user-001", "video/mp4", List.of())
        );

        assertTrue(exception.getMessage().contains("retry later"));
        verify(objectStoragePort, never()).completeMultipartUpload(anyString(), anyString(), anyString(), any());
    }

    @Test
    void shouldReturnDerivedExpiredSessionWhenSessionTimedOut() {
        when(uploadSessionRepository.findById("session-001"))
                .thenReturn(Optional.of(multipartSession(
                        "session-001",
                        UploadSessionStatus.UPLOADING,
                        FIXED_NOW.minusSeconds(60)
                )));

        UploadSession uploadSession = uploadAppService.getVisibleSession("blog", "session-001", "user-001");

        assertEquals(UploadSessionStatus.EXPIRED, uploadSession.status());
    }

    @Test
    void shouldHideSessionFromAnotherTenant() {
        when(uploadSessionRepository.findById("session-001"))
                .thenReturn(Optional.of(new UploadSession(
                        "session-001",
                        "im",
                        "user-001",
                        UploadMode.DIRECT,
                        AccessLevel.PRIVATE,
                        "demo.mp4",
                        "video/mp4",
                        1024L,
                        "hash-001",
                        "blog/2026/03/14/user-001/uploads/session-001-demo.mp4",
                        CHUNK_SIZE_BYTES,
                        1,
                        "provider-001",
                        null,
                        UploadSessionStatus.UPLOADING,
                        FIXED_NOW,
                        FIXED_NOW,
                        FIXED_NOW.plusSeconds(60)
                )));

        assertThrows(UploadSessionNotFoundException.class,
                () -> uploadAppService.getVisibleSession("blog", "session-001", "user-001"));
    }

    @Test
    void shouldRejectAnotherOwner() {
        when(uploadSessionRepository.findById("session-001"))
                .thenReturn(Optional.of(multipartSession(
                        "session-001",
                        UploadSessionStatus.UPLOADING,
                        FIXED_NOW.plusSeconds(60)
                )));

        assertThrows(UploadSessionAccessDeniedException.class,
                () -> uploadAppService.getVisibleSession("blog", "session-001", "user-002"));
    }

    private UploadSession multipartSession(String uploadSessionId,
                                          UploadSessionStatus status,
                                          Instant expiresAt) {
        return new UploadSession(
                uploadSessionId,
                "blog",
                "user-001",
                UploadMode.DIRECT,
                AccessLevel.PRIVATE,
                "demo.mp4",
                "video/mp4",
                11L * 1024 * 1024,
                "hash-001",
                "blog/2026/03/14/user-001/uploads/session-001-demo.mp4",
                CHUNK_SIZE_BYTES,
                3,
                "provider-001",
                null,
                status,
                FIXED_NOW,
                FIXED_NOW,
                expiresAt
        );
    }

    private UploadSession singlePresignedSession(String uploadSessionId,
                                                 UploadSessionStatus status,
                                                 Instant expiresAt) {
        return new UploadSession(
                uploadSessionId,
                "blog",
                "user-001",
                UploadMode.PRESIGNED_SINGLE,
                AccessLevel.PUBLIC,
                "avatar.png",
                "image/png",
                2048L,
                "hash-ps-001",
                "blog/2026/03/14/user-001/uploads/" + uploadSessionId + "-avatar.png",
                0,
                1,
                null,
                null,
                status,
                FIXED_NOW,
                FIXED_NOW,
                expiresAt
        );
    }

    private UploadSession completedMultipartSession(String uploadSessionId, String fileId) {
        return new UploadSession(
                uploadSessionId,
                "blog",
                "user-001",
                UploadMode.DIRECT,
                AccessLevel.PRIVATE,
                "demo.mp4",
                "video/mp4",
                11L * 1024 * 1024,
                "hash-001",
                "blog/2026/03/14/user-001/uploads/session-001-demo.mp4",
                CHUNK_SIZE_BYTES,
                3,
                "provider-001",
                fileId,
                UploadSessionStatus.COMPLETED,
                FIXED_NOW,
                FIXED_NOW,
                FIXED_NOW.plusSeconds(60)
        );
    }

    private static final class ImmediateTransactionOperations implements TransactionOperations {

        @Override
        public <T> T execute(TransactionCallback<T> action) throws TransactionException {
            TransactionStatus status = new SimpleTransactionStatus();
            return action.doInTransaction(status);
        }
    }
}
