package com.architectcgz.file.application.service;

import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.common.exception.FileNotFoundException;
import com.architectcgz.file.application.dto.FileDetailResponse;
import com.architectcgz.file.application.dto.FileUrlResponse;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.infrastructure.cache.FileUrlCacheManager;
import com.architectcgz.file.infrastructure.config.S3Properties;
import com.architectcgz.file.infrastructure.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * FileAccessService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class FileAccessServiceTest {
    
    @Mock
    private FileRecordRepository fileRecordRepository;
    
    @Mock
    private StorageObjectRepository storageObjectRepository;
    
    @Mock
    private StorageService storageService;
    
    @Mock
    private S3Properties s3Properties;
    
    @Mock
    private FileUrlCacheManager fileUrlCacheManager;
    
    @InjectMocks
    private FileAccessService fileAccessService;
    
    private FileRecord publicFileRecord;
    private FileRecord privateFileRecord;
    private StorageObject storageObject;
    
    @BeforeEach
    void setUp() throws Exception {
        // 默认缓存返回 null（未命中），使用 lenient 避免 UnnecessaryStubbingException
        lenient().when(fileUrlCacheManager.get(anyString())).thenReturn(null);

        // 设置 privateUrlExpireSeconds 字段值（使用反射）
        var field = FileAccessService.class.getDeclaredField("privateUrlExpireSeconds");
        field.setAccessible(true);
        field.set(fileAccessService, 3600);
        
        // 准备测试数据
        storageObject = StorageObject.builder()
                .id("storage-001")
                .appId("blog")
                .fileHash("abc123")
                .hashAlgorithm("MD5")
                .storagePath("2026/01/19/123/test-file.jpg")
                .bucketName("public-bucket")
                .fileSize(1024L)
                .contentType("image/jpeg")
                .referenceCount(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        publicFileRecord = FileRecord.builder()
                .id("file-001")
                .appId("blog")
                .userId("123")
                .storageObjectId("storage-001")
                .originalFilename("test.jpg")
                .storagePath("2026/01/19/123/test-file.jpg")
                .fileSize(1024L)
                .contentType("image/jpeg")
                .fileHash("abc123")
                .hashAlgorithm("MD5")
                .status(FileStatus.COMPLETED)
                .accessLevel(AccessLevel.PUBLIC)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        privateFileRecord = FileRecord.builder()
                .id("file-002")
                .appId("blog")
                .userId("123")
                .storageObjectId("storage-001")
                .originalFilename("private.jpg")
                .storagePath("2026/01/19/123/test-file.jpg")
                .fileSize(1024L)
                .contentType("image/jpeg")
                .fileHash("abc123")
                .hashAlgorithm("MD5")
                .status(FileStatus.COMPLETED)
                .accessLevel(AccessLevel.PRIVATE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
    
    @Test
    void testGetFileUrl_PublicFile_returnsPermanentUrl() {
        // Given
        when(fileRecordRepository.findById("file-001")).thenReturn(Optional.of(publicFileRecord));
        when(storageService.getBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        when(storageObjectRepository.findById("storage-001")).thenReturn(Optional.of(storageObject));
        when(storageService.getPublicUrl("public-bucket", publicFileRecord.getStoragePath()))
                .thenReturn("https://cdn.example.com/2026/01/19/123/test-file.jpg");
        
        // When
        FileUrlResponse response = fileAccessService.getFileUrl("blog", "file-001", "123");
        
        // Then
        assertNotNull(response);
        assertEquals("https://cdn.example.com/2026/01/19/123/test-file.jpg", response.getUrl());
        assertTrue(response.getPermanent());
        assertNull(response.getExpiresAt());
    }
    
    @Test
    void testGetFileUrl_PrivateFile_Owner_returnsTemporaryUrl() {
        // Given
        when(fileRecordRepository.findById("file-002")).thenReturn(Optional.of(privateFileRecord));
        when(storageService.getBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        when(storageObjectRepository.findById("storage-001")).thenReturn(Optional.of(storageObject));
        when(storageService.generatePresignedUrl(eq("public-bucket"), eq(privateFileRecord.getStoragePath()), any(Duration.class)))
                .thenReturn("https://s3.example.com/bucket/path?X-Amz-Signature=...");
        
        // When
        FileUrlResponse response = fileAccessService.getFileUrl("blog", "file-002", "123");
        
        // Then
        assertNotNull(response);
        assertEquals("https://s3.example.com/bucket/path?X-Amz-Signature=...", response.getUrl());
        assertFalse(response.getPermanent());
        assertNotNull(response.getExpiresAt());
        assertTrue(response.getExpiresAt().isAfter(LocalDateTime.now()));
    }
    
    @Test
    void testGetFileUrl_PrivateFile_NonOwner_ThrowsException() {
        // Given
        when(fileRecordRepository.findById("file-002")).thenReturn(Optional.of(privateFileRecord));
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileAccessService.getFileUrl("blog", "file-002", "999"); // 不同的用户ID
        });
        
        assertEquals("无权访问该文件: file-002", exception.getMessage());
    }
    
    @Test
    void testGetFileUrl_FileNotFound_ThrowsException() {
        // Given
        when(fileRecordRepository.findById("non-existent")).thenReturn(Optional.empty());
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileAccessService.getFileUrl("blog", "non-existent", "123");
        });
        
        assertEquals("文件不存在: non-existent", exception.getMessage());
    }
    
    @Test
    void testGetFileUrl_DeletedFile_ThrowsException() {
        // Given
        FileRecord deletedFile = FileRecord.builder()
                .id("file-003")
                .appId("blog")
                .userId("123")
                .storageObjectId("storage-001")
                .originalFilename("deleted.jpg")
                .storagePath("2026/01/19/123/test-file.jpg")
                .fileSize(1024L)
                .contentType("image/jpeg")
                .fileHash("abc123")
                .hashAlgorithm("MD5")
                .status(FileStatus.DELETED)
                .accessLevel(AccessLevel.PUBLIC)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        when(fileRecordRepository.findById("file-003")).thenReturn(Optional.of(deletedFile));
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileAccessService.getFileUrl("blog", "file-003", "123");
        });
        
        assertEquals("文件已被删除: file-003", exception.getMessage());
    }

    @Test
    void testGetFileUrl_PublicFileOutsidePublicBucket_returnsPresignedUrl() {
        StorageObject privateBucketObject = StorageObject.builder()
                .id("storage-002")
                .appId("blog")
                .fileHash("abc123")
                .hashAlgorithm("MD5")
                .storagePath("2026/01/19/123/test-file.jpg")
                .bucketName("private-bucket")
                .fileSize(1024L)
                .contentType("image/jpeg")
                .referenceCount(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(fileRecordRepository.findById("file-001")).thenReturn(Optional.of(publicFileRecord));
        when(storageService.getBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        when(storageObjectRepository.findById("storage-001")).thenReturn(Optional.of(privateBucketObject));
        when(storageService.generatePresignedUrl(eq("private-bucket"), eq(publicFileRecord.getStoragePath()), any(Duration.class)))
                .thenReturn("https://s3.example.com/private-object?X-Amz-Signature=fallback");

        FileUrlResponse response = fileAccessService.getFileUrl("blog", "file-001", "123");

        assertNotNull(response);
        assertEquals("https://s3.example.com/private-object?X-Amz-Signature=fallback", response.getUrl());
        assertFalse(response.getPermanent());
        assertNotNull(response.getExpiresAt());
    }
    
    @Test
    void testGetFileUrl_NullAccessLevel_ThrowsException() {
        // Given - null access level 应该被拒绝访问
        FileRecord fileWithNullAccessLevel = FileRecord.builder()
                .id("file-004")
                .appId("blog")
                .userId("123")
                .storageObjectId("storage-001")
                .originalFilename("test.jpg")
                .storagePath("2026/01/19/123/test-file.jpg")
                .fileSize(1024L)
                .contentType("image/jpeg")
                .fileHash("abc123")
                .hashAlgorithm("MD5")
                .status(FileStatus.COMPLETED)
                .accessLevel(null) // null access level
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        when(fileRecordRepository.findById("file-004")).thenReturn(Optional.of(fileWithNullAccessLevel));
        
        // When & Then - null access level 应该抛出异常
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileAccessService.getFileUrl("blog", "file-004", "123");
        });
        
        assertEquals("无权访问该文件: file-004", exception.getMessage());
    }
    
    // ========== getFileDetail Tests ==========
    
    @Test
    void testGetFileDetail_PublicFile_returnsDetails() {
        // Given
        when(fileRecordRepository.findById("file-001")).thenReturn(Optional.of(publicFileRecord));
        
        // When
        FileDetailResponse response = fileAccessService.getFileDetail("blog", "file-001", "123");
        
        // Then
        assertNotNull(response);
        assertEquals("file-001", response.getFileId());
        assertEquals("123", response.getUserId());
        assertEquals("test.jpg", response.getOriginalFilename());
        assertEquals(1024L, response.getFileSize());
        assertEquals("image/jpeg", response.getContentType());
        assertEquals("abc123", response.getFileHash());
        assertEquals("MD5", response.getHashAlgorithm());
        assertEquals(FileStatus.COMPLETED, response.getStatus());
        assertEquals(AccessLevel.PUBLIC, response.getAccessLevel());
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getUpdatedAt());
    }
    
    @Test
    void testGetFileDetail_PrivateFile_Owner_returnsDetails() {
        // Given
        when(fileRecordRepository.findById("file-002")).thenReturn(Optional.of(privateFileRecord));
        
        // When
        FileDetailResponse response = fileAccessService.getFileDetail("blog", "file-002", "123");
        
        // Then
        assertNotNull(response);
        assertEquals("file-002", response.getFileId());
        assertEquals("123", response.getUserId());
        assertEquals("private.jpg", response.getOriginalFilename());
        assertEquals(AccessLevel.PRIVATE, response.getAccessLevel());
    }
    
    @Test
    void testGetFileDetail_PrivateFile_NonOwner_ThrowsException() {
        // Given
        when(fileRecordRepository.findById("file-002")).thenReturn(Optional.of(privateFileRecord));
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileAccessService.getFileDetail("blog", "file-002", "999"); // 不同的用户ID
        });
        
        assertEquals("无权访问该文件: file-002", exception.getMessage());
    }
    
    @Test
    void testGetFileDetail_FileNotFound_ThrowsException() {
        // Given
        when(fileRecordRepository.findById("non-existent")).thenReturn(Optional.empty());
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileAccessService.getFileDetail("blog", "non-existent", "123");
        });
        
        assertEquals("文件不存在: non-existent", exception.getMessage());
    }
    
    @Test
    void testGetFileDetail_DeletedFile_ThrowsException() {
        // Given
        FileRecord deletedFile = FileRecord.builder()
                .id("file-003")
                .appId("blog")
                .userId("123")
                .storageObjectId("storage-001")
                .originalFilename("deleted.jpg")
                .storagePath("2026/01/19/123/test-file.jpg")
                .fileSize(1024L)
                .contentType("image/jpeg")
                .fileHash("abc123")
                .hashAlgorithm("MD5")
                .status(FileStatus.DELETED)
                .accessLevel(AccessLevel.PUBLIC)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        when(fileRecordRepository.findById("file-003")).thenReturn(Optional.of(deletedFile));
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileAccessService.getFileDetail("blog", "file-003", "123");
        });
        
        assertEquals("文件已被删除: file-003", exception.getMessage());
    }
    
    @Test
    void testGetFileDetail_PublicFile_AnyUser_returnsDetails() {
        // Given - 公开文件，任何用户都可以查看详情
        when(fileRecordRepository.findById("file-001")).thenReturn(Optional.of(publicFileRecord));

        // When - 使用不同的用户ID
        FileDetailResponse response = fileAccessService.getFileDetail("blog", "file-001", "999");

        // Then
        assertNotNull(response);
        assertEquals("file-001", response.getFileId());
        assertEquals(AccessLevel.PUBLIC, response.getAccessLevel());
    }

    // ========== 跨租户访问测试（L1 补充） ==========

    @Test
    void testGetFileUrl_CrossTenant_ThrowsFileNotFoundException() {
        // Given - 使用不同的 appId 访问其他租户的文件
        when(fileRecordRepository.findById("file-001")).thenReturn(Optional.of(publicFileRecord));

        // When & Then - 跨租户应返回 404（不暴露文件存在性）
        FileNotFoundException exception = assertThrows(FileNotFoundException.class, () -> {
            fileAccessService.getFileUrl("other-app", "file-001", "123");
        });

        assertEquals("文件不存在: file-001", exception.getMessage());
    }

    @Test
    void testGetFileDetail_CrossTenant_ThrowsFileNotFoundException() {
        // Given - 使用不同的 appId 访问其他租户的文件
        when(fileRecordRepository.findById("file-001")).thenReturn(Optional.of(publicFileRecord));

        // When & Then - 跨租户应返回 404（不暴露文件存在性）
        FileNotFoundException exception = assertThrows(FileNotFoundException.class, () -> {
            fileAccessService.getFileDetail("other-app", "file-001", "123");
        });

        assertEquals("文件不存在: file-001", exception.getMessage());
    }

    // ========== updateAccessLevel 测试（H3/M2/M3 补充） ==========

    @Test
    void testUpdateAccessLevel_DeletedFile_ThrowsException() {
        // Given - 已删除文件不应允许修改访问级别
        FileRecord deletedFile = FileRecord.builder()
                .id("file-003")
                .appId("blog")
                .userId("123")
                .storageObjectId("storage-001")
                .originalFilename("deleted.jpg")
                .storagePath("2026/01/19/123/test-file.jpg")
                .fileSize(1024L)
                .contentType("image/jpeg")
                .fileHash("abc123")
                .hashAlgorithm("MD5")
                .status(FileStatus.DELETED)
                .accessLevel(AccessLevel.PUBLIC)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(fileRecordRepository.findById("file-003")).thenReturn(Optional.of(deletedFile));

        // When & Then
        FileNotFoundException exception = assertThrows(FileNotFoundException.class, () -> {
            fileAccessService.updateAccessLevel("blog", "file-003", "123", AccessLevel.PRIVATE);
        });

        assertEquals("文件已被删除: file-003", exception.getMessage());
    }

    @Test
    void testUpdateAccessLevel_CrossTenant_ThrowsFileNotFoundException() {
        // Given - 跨租户修改访问级别应返回 404
        when(fileRecordRepository.findById("file-001")).thenReturn(Optional.of(publicFileRecord));

        // When & Then
        FileNotFoundException exception = assertThrows(FileNotFoundException.class, () -> {
            fileAccessService.updateAccessLevel("other-app", "file-001", "123", AccessLevel.PRIVATE);
        });

        assertEquals("文件不存在: file-001", exception.getMessage());
    }
}
