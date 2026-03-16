package com.platform.fileservice.core.application.service;

import com.platform.fileservice.core.domain.exception.FileAccessDeniedException;
import com.platform.fileservice.core.domain.exception.FileAccessMutationException;
import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.BlobObject;
import com.platform.fileservice.core.domain.model.FileAccessMutationContext;
import com.platform.fileservice.core.domain.model.FileAsset;
import com.platform.fileservice.core.domain.model.FileAssetStatus;
import com.platform.fileservice.core.ports.access.AuthorizedFileAccessPort;
import com.platform.fileservice.core.ports.access.FileAccessMutationPort;
import com.platform.fileservice.core.ports.security.AccessTicketPort;
import com.platform.fileservice.core.ports.storage.ObjectStoragePort;
import com.platform.fileservice.core.ports.system.ClockPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccessAppServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-14T01:00:00Z");

    private AuthorizedFileAccessPort authorizedFileAccessPort;
    private FileAccessMutationPort fileAccessMutationPort;
    private ObjectStoragePort objectStoragePort;
    private AccessAppService accessAppService;

    @BeforeEach
    void setUp() {
        authorizedFileAccessPort = mock(AuthorizedFileAccessPort.class);
        fileAccessMutationPort = mock(FileAccessMutationPort.class);
        objectStoragePort = mock(ObjectStoragePort.class);
        AccessTicketPort accessTicketPort = mock(AccessTicketPort.class);
        ClockPort clockPort = () -> FIXED_NOW;

        accessAppService = new AccessAppService(
                authorizedFileAccessPort,
                fileAccessMutationPort,
                objectStoragePort,
                accessTicketPort,
                clockPort,
                new ImmediateTransactionOperations()
        );
    }

    @Test
    void shouldSkipWhenAccessLevelUnchanged() {
        when(fileAccessMutationPort.loadForChange("blog", "file-001"))
                .thenReturn(new FileAccessMutationContext(
                        fileAsset("file-001", "blog", "user-001", "blob-001", AccessLevel.PUBLIC),
                        blobObject("blob-001", "blog", "public-bucket", "images/file-001.png", 2)
                ));
        when(objectStoragePort.normalizeBucketName("public-bucket")).thenReturn("public-bucket");
        when(objectStoragePort.resolveBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");

        accessAppService.updateAccessLevel("blog", "file-001", "user-001", AccessLevel.PUBLIC);

        verify(fileAccessMutationPort, never()).updateAccessLevel(any(), any());
        verify(objectStoragePort, never()).copyObject(any(), any(), any(), any());
        verify(fileAccessMutationPort, never()).createBlobObject(any());
    }

    @Test
    void shouldCopyAndRebindBlobWhenBucketChanges() {
        when(fileAccessMutationPort.loadForChange("blog", "file-001"))
                .thenReturn(new FileAccessMutationContext(
                        fileAsset("file-001", "blog", "user-001", "blob-001", AccessLevel.PUBLIC),
                        blobObject("blob-001", "blog", "public-bucket", "images/file-001.png", 1)
                ));
        when(objectStoragePort.normalizeBucketName("public-bucket")).thenReturn("public-bucket");
        when(objectStoragePort.resolveBucketName(AccessLevel.PRIVATE)).thenReturn("private-bucket");

        accessAppService.updateAccessLevel("blog", "file-001", "user-001", AccessLevel.PRIVATE);

        ArgumentCaptor<BlobObject> blobCaptor = ArgumentCaptor.forClass(BlobObject.class);
        verify(objectStoragePort).copyObject("public-bucket", "images/file-001.png", "private-bucket", "images/file-001.png");
        verify(fileAccessMutationPort).createBlobObject(blobCaptor.capture());
        verify(fileAccessMutationPort).rebindBlobAndAccessLevel(eq("file-001"), eq(blobCaptor.getValue()), eq(AccessLevel.PRIVATE));
        verify(fileAccessMutationPort).decrementBlobReference("blob-001");
        verify(objectStoragePort).deleteObject("public-bucket", "images/file-001.png");

        BlobObject copiedBlob = blobCaptor.getValue();
        assertEquals("blog", copiedBlob.tenantId());
        assertEquals("private-bucket", copiedBlob.bucketName());
        assertEquals("images/file-001.png", copiedBlob.objectKey());
        assertEquals(1, copiedBlob.referenceCount());
        assertEquals(FIXED_NOW, copiedBlob.createdAt());
        assertEquals(FIXED_NOW, copiedBlob.updatedAt());
    }

    @Test
    void shouldCleanupCopiedObjectWhenMutationFails() {
        when(fileAccessMutationPort.loadForChange("blog", "file-001"))
                .thenReturn(new FileAccessMutationContext(
                        fileAsset("file-001", "blog", "user-001", "blob-001", AccessLevel.PUBLIC),
                        blobObject("blob-001", "blog", "public-bucket", "images/file-001.png", 2)
                ));
        when(objectStoragePort.normalizeBucketName("public-bucket")).thenReturn("public-bucket");
        when(objectStoragePort.resolveBucketName(AccessLevel.PRIVATE)).thenReturn("private-bucket");
        doThrow(new FileAccessMutationException("boom"))
                .when(fileAccessMutationPort)
                .rebindBlobAndAccessLevel(eq("file-001"), any(BlobObject.class), eq(AccessLevel.PRIVATE));

        assertThrows(FileAccessMutationException.class,
                () -> accessAppService.updateAccessLevel("blog", "file-001", "user-001", AccessLevel.PRIVATE));

        verify(objectStoragePort).copyObject("public-bucket", "images/file-001.png", "private-bucket", "images/file-001.png");
        verify(objectStoragePort).deleteObject("private-bucket", "images/file-001.png");
        verify(objectStoragePort, never()).deleteObject("public-bucket", "images/file-001.png");
    }

    @Test
    void shouldRejectNonOwnerAccessLevelChange() {
        when(fileAccessMutationPort.loadForChange("blog", "file-001"))
                .thenReturn(new FileAccessMutationContext(
                        fileAsset("file-001", "blog", "user-001", "blob-001", AccessLevel.PUBLIC),
                        blobObject("blob-001", "blog", "public-bucket", "images/file-001.png", 1)
                ));

        assertThrows(FileAccessDeniedException.class,
                () -> accessAppService.updateAccessLevel("blog", "file-001", "user-002", AccessLevel.PRIVATE));

        verify(objectStoragePort, never()).copyObject(any(), any(), any(), any());
        verify(fileAccessMutationPort, never()).updateAccessLevel(any(), any());
    }

    private FileAsset fileAsset(String fileId, String tenantId, String ownerId, String blobObjectId, AccessLevel accessLevel) {
        return new FileAsset(
                fileId,
                tenantId,
                ownerId,
                blobObjectId,
                "demo.png",
                "images/file-001.png",
                "image/png",
                1024L,
                accessLevel,
                FileAssetStatus.ACTIVE,
                FIXED_NOW,
                FIXED_NOW
        );
    }

    private BlobObject blobObject(String blobObjectId, String tenantId, String bucketName, String objectKey, int referenceCount) {
        return new BlobObject(
                blobObjectId,
                tenantId,
                "legacy-s3",
                bucketName,
                objectKey,
                "hash-001",
                "MD5",
                1024L,
                "image/png",
                referenceCount,
                FIXED_NOW,
                FIXED_NOW
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
