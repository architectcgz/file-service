package com.architectcgz.file.application.service.fileaccess.bridge;

import com.architectcgz.file.application.dto.FileUrlResponse;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.common.exception.FileNotFoundException;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.infrastructure.cache.FileUrlCacheManager;
import com.architectcgz.file.infrastructure.config.AccessProperties;
import com.architectcgz.file.infrastructure.storage.StorageService;
import com.platform.fileservice.core.application.service.AccessAppService;
import com.platform.fileservice.core.domain.exception.FileAccessDeniedException;
import com.platform.fileservice.core.domain.exception.FileAccessMutationException;
import com.platform.fileservice.core.domain.exception.FileAssetNotFoundException;
import com.platform.fileservice.core.domain.model.FileAsset;
import com.platform.fileservice.core.domain.model.FileAssetStatus;
import com.platform.fileservice.core.ports.storage.ObjectStoragePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileAccessCoreBridgeServiceTest {

    @Mock
    private AccessAppService accessAppService;

    @Mock
    private ObjectStoragePort objectStoragePort;

    @Mock
    private StorageService storageService;

    @Mock
    private AccessProperties accessProperties;

    @Mock
    private FileUrlCacheManager fileUrlCacheManager;

    @InjectMocks
    private FileAccessCoreBridgeService fileAccessCoreBridgeService;

    @Test
    void shouldReturnCachedPublicUrl() {
        FileAsset publicFile = publicFile();
        when(accessAppService.getAccessibleFile("blog", "file-001", "user-001")).thenReturn(publicFile);
        when(fileUrlCacheManager.get("file-001")).thenReturn("https://cdn.example.com/file-001");

        FileUrlResponse response = fileAccessCoreBridgeService.getFileUrl("blog", "file-001", "user-001");

        assertEquals("https://cdn.example.com/file-001", response.getUrl());
        assertTrue(response.getPermanent());
        assertNull(response.getExpiresAt());
        verify(storageService, never()).getPublicUrl(any(), any());
    }

    @Test
    void shouldGenerateAndCachePublicUrlWhenCacheMisses() {
        FileAsset publicFile = publicFile();
        when(accessAppService.getAccessibleFile("blog", "file-001", "user-001")).thenReturn(publicFile);
        when(fileUrlCacheManager.get("file-001")).thenReturn(null);
        when(objectStoragePort.resolveBucketName(com.platform.fileservice.core.domain.model.AccessLevel.PUBLIC))
                .thenReturn("public-bucket");
        when(storageService.getPublicUrl("public-bucket", "images/file-001.png"))
                .thenReturn("https://cdn.example.com/images/file-001.png");

        FileUrlResponse response = fileAccessCoreBridgeService.getFileUrl("blog", "file-001", "user-001");

        assertEquals("https://cdn.example.com/images/file-001.png", response.getUrl());
        assertTrue(response.getPermanent());
        verify(fileUrlCacheManager).put("file-001", "https://cdn.example.com/images/file-001.png");
    }

    @Test
    void shouldGeneratePrivatePresignedUrl() {
        FileAsset privateFile = privateFile();
        when(accessAppService.getAccessibleFile("blog", "file-002", "user-001")).thenReturn(privateFile);
        when(accessProperties.getPrivateUrlExpireSeconds()).thenReturn(1800);
        when(objectStoragePort.resolveBucketName(com.platform.fileservice.core.domain.model.AccessLevel.PRIVATE))
                .thenReturn("private-bucket");
        when(storageService.generatePresignedUrl(eq("private-bucket"), eq("docs/file-002.pdf"), any()))
                .thenReturn("https://s3.example.com/private-url");

        FileUrlResponse response = fileAccessCoreBridgeService.getFileUrl("blog", "file-002", "user-001");

        assertEquals("https://s3.example.com/private-url", response.getUrl());
        assertFalse(response.getPermanent());
        assertNotNull(response.getExpiresAt());
        verify(fileUrlCacheManager, never()).put(any(), any());
    }

    @Test
    void shouldEvictCacheAfterAccessLevelChanges() {
        FileAsset publicFile = publicFile();
        when(accessAppService.getAccessibleFile("blog", "file-001", "user-001")).thenReturn(publicFile);

        fileAccessCoreBridgeService.updateAccessLevel("blog", "file-001", "user-001", AccessLevel.PRIVATE);

        verify(accessAppService).updateAccessLevel(
                "blog",
                "file-001",
                "user-001",
                com.platform.fileservice.core.domain.model.AccessLevel.PRIVATE
        );
        verify(fileUrlCacheManager).evict("file-001");
    }

    @Test
    void shouldSkipMutationWhenAccessLevelUnchanged() {
        FileAsset publicFile = publicFile();
        when(accessAppService.getAccessibleFile("blog", "file-001", "user-001")).thenReturn(publicFile);

        fileAccessCoreBridgeService.updateAccessLevel("blog", "file-001", "user-001", AccessLevel.PUBLIC);

        verify(accessAppService, never()).updateAccessLevel(any(), any(), any(), any());
        verify(fileUrlCacheManager, never()).evict(any());
    }

    @Test
    void shouldTranslateNotFoundException() {
        when(accessAppService.getAccessibleFile("blog", "file-404", null))
                .thenThrow(new FileAssetNotFoundException("fileId not found: file-404"));

        FileNotFoundException exception = assertThrows(
                FileNotFoundException.class,
                () -> fileAccessCoreBridgeService.getFileUrl("blog", "file-404", null)
        );

        assertEquals(FileServiceErrorCodes.FILE_NOT_FOUND, exception.getCode());
    }

    @Test
    void shouldTranslateAccessDeniedException() {
        when(accessAppService.getAccessibleFile("blog", "file-002", "user-999"))
                .thenThrow(new FileAccessDeniedException("access denied for fileId: file-002"));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> fileAccessCoreBridgeService.getFileUrl("blog", "file-002", "user-999")
        );

        assertEquals(FileServiceErrorCodes.ACCESS_DENIED, exception.getCode());
    }

    @Test
    void shouldTranslateMutationException() {
        FileAsset publicFile = publicFile();
        when(accessAppService.getAccessibleFile("blog", "file-001", "user-001")).thenReturn(publicFile);
        doThrow(new FileAccessMutationException("mutation failed"))
                .when(accessAppService)
                .updateAccessLevel(
                "blog",
                "file-001",
                "user-001",
                com.platform.fileservice.core.domain.model.AccessLevel.PRIVATE
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> fileAccessCoreBridgeService.updateAccessLevel("blog", "file-001", "user-001", AccessLevel.PRIVATE)
        );

        assertEquals(FileServiceErrorCodes.UPDATE_ACCESS_LEVEL_FAILED, exception.getCode());
    }

    private FileAsset publicFile() {
        return new FileAsset(
                "file-001",
                "blog",
                "user-001",
                "blob-001",
                "cover.png",
                "images/file-001.png",
                "image/png",
                1024L,
                com.platform.fileservice.core.domain.model.AccessLevel.PUBLIC,
                FileAssetStatus.ACTIVE,
                Instant.parse("2026-03-14T00:00:00Z"),
                Instant.parse("2026-03-14T01:00:00Z")
        );
    }

    private FileAsset privateFile() {
        return new FileAsset(
                "file-002",
                "blog",
                "user-001",
                "blob-002",
                "manual.pdf",
                "docs/file-002.pdf",
                "application/pdf",
                4096L,
                com.platform.fileservice.core.domain.model.AccessLevel.PRIVATE,
                FileAssetStatus.ACTIVE,
                Instant.parse("2026-03-14T00:00:00Z"),
                Instant.parse("2026-03-14T01:00:00Z")
        );
    }
}
