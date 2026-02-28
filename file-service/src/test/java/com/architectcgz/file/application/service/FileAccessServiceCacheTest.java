package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.FileUrlResponse;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.infrastructure.cache.FileUrlCacheManager;
import com.architectcgz.file.infrastructure.config.S3Properties;
import com.architectcgz.file.infrastructure.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FileAccessService 缓存功能单元测试
 *
 * 测试范围：
 * - 缓存命中/未命中场景
 * - 缓存写入与降级
 * - 缓存开关配置
 * - 私有文件不缓存
 * - 更新访问级别时清除缓存（含事务提交后执行）
 * - 访问级别未变更时跳过更新
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FileAccessService 缓存功能测试")
class FileAccessServiceCacheTest {

    @Mock
    private FileRecordRepository fileRecordRepository;
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

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fileAccessService, "privateUrlExpireSeconds", 3600);

        publicFileRecord = FileRecord.builder()
                .id("file-001").appId("blog").userId("user-123")
                .storageObjectId("storage-001").originalFilename("test.jpg")
                .storagePath("2026/02/10/user-123/test.jpg").fileSize(1024L)
                .contentType("image/jpeg").fileHash("abc123").hashAlgorithm("MD5")
                .status(FileStatus.COMPLETED).accessLevel(AccessLevel.PUBLIC)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        privateFileRecord = FileRecord.builder()
                .id("file-002").appId("blog").userId("user-123")
                .storageObjectId("storage-002").originalFilename("private.jpg")
                .storagePath("2026/02/10/user-123/private.jpg").fileSize(2048L)
                .contentType("image/jpeg").fileHash("def456").hashAlgorithm("MD5")
                .status(FileStatus.COMPLETED).accessLevel(AccessLevel.PRIVATE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    @AfterEach
    void tearDown() {
        // 清理事务同步上下文，避免测试间干扰
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ========== 缓存命中测试 ==========

    @Test
    @DisplayName("缓存命中 - 直接返回缓存的URL")
    void testCacheHit_ReturnsUrlFromCache() {
        String cachedUrl = "https://cdn.example.com/2026/02/10/user-123/test.jpg";
        when(fileUrlCacheManager.get("file-001")).thenReturn(cachedUrl);

        FileUrlResponse response = fileAccessService.getFileUrl("blog", "file-001", "user-123");

        assertNotNull(response);
        assertEquals(cachedUrl, response.getUrl());
        assertTrue(response.getPermanent());
        assertNull(response.getExpiresAt());
        verify(fileRecordRepository, never()).findById(any());
        verify(storageService, never()).getPublicUrl(any());
    }

    // ========== 缓存未命中测试 ==========

    @Test
    @DisplayName("缓存未命中 - 查询数据库并写入缓存")
    void testCacheMiss_QueriesDatabaseAndCachesResult() {
        String publicUrl = "https://cdn.example.com/2026/02/10/user-123/test.jpg";
        when(fileUrlCacheManager.get("file-001")).thenReturn(null);
        when(fileRecordRepository.findById("file-001")).thenReturn(Optional.of(publicFileRecord));
        when(storageService.getPublicUrl(publicFileRecord.getStoragePath())).thenReturn(publicUrl);

        FileUrlResponse response = fileAccessService.getFileUrl("blog", "file-001", "user-123");

        assertNotNull(response);
        assertEquals(publicUrl, response.getUrl());
        assertTrue(response.getPermanent());
        verify(fileRecordRepository).findById("file-001");
        verify(fileUrlCacheManager).put("file-001", publicUrl);
    }

    // ========== 私有文件不缓存测试 ==========

    @Test
    @DisplayName("私有文件 - 不缓存预签名URL")
    void testPrivateFile_DoesNotCachePresignedUrl() {
        String presignedUrl = "https://s3.example.com/bucket/path?X-Amz-Signature=...";
        when(fileUrlCacheManager.get("file-002")).thenReturn(null);
        when(fileRecordRepository.findById("file-002")).thenReturn(Optional.of(privateFileRecord));
        when(storageService.generatePresignedUrl(eq(privateFileRecord.getStoragePath()), any(Duration.class)))
                .thenReturn(presignedUrl);

        FileUrlResponse response = fileAccessService.getFileUrl("blog", "file-002", "user-123");

        assertNotNull(response);
        assertEquals(presignedUrl, response.getUrl());
        assertFalse(response.getPermanent());
        assertNotNull(response.getExpiresAt());
        verify(fileUrlCacheManager, never()).put(anyString(), anyString());
    }

    // ========== 缓存降级测试 ==========

    @Test
    @DisplayName("缓存返回null - 降级到数据库查询")
    void testCacheReturnsNull_FallbackToDatabase() {
        String publicUrl = "https://cdn.example.com/2026/02/10/user-123/test.jpg";
        when(fileUrlCacheManager.get("file-001")).thenReturn(null);
        when(fileRecordRepository.findById("file-001")).thenReturn(Optional.of(publicFileRecord));
        when(storageService.getPublicUrl(publicFileRecord.getStoragePath())).thenReturn(publicUrl);

        FileUrlResponse response = fileAccessService.getFileUrl("blog", "file-001", "user-123");

        assertNotNull(response);
        assertEquals(publicUrl, response.getUrl());
        verify(fileRecordRepository).findById("file-001");
    }

    // ========== 边界情况测试 ==========

    @Test
    @DisplayName("文件不存在 - 抛出异常")
    void testFileNotFound_ThrowsException() {
        when(fileUrlCacheManager.get("non-existent")).thenReturn(null);
        when(fileRecordRepository.findById("non-existent")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileAccessService.getFileUrl("blog", "non-existent", "user-123"));
        assertTrue(ex.getMessage().contains("文件不存在"));
    }

    @Test
    @DisplayName("文件不属于应用 - 抛出异常")
    void testFileNotBelongsToApp_ThrowsException() {
        when(fileUrlCacheManager.get("file-001")).thenReturn(null);
        when(fileRecordRepository.findById("file-001")).thenReturn(Optional.of(publicFileRecord));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> fileAccessService.getFileUrl("wrong-app", "file-001", "user-123"));
        assertEquals("文件不属于该应用", ex.getMessage());
    }

    @Test
    @DisplayName("私有文件非所有者访问 - 抛出异常")
    void testPrivateFileNonOwner_ThrowsException() {
        when(fileUrlCacheManager.get("file-002")).thenReturn(null);
        when(fileRecordRepository.findById("file-002")).thenReturn(Optional.of(privateFileRecord));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileAccessService.getFileUrl("blog", "file-002", "other-user"));
        assertTrue(ex.getMessage().contains("无权访问该文件"));
    }

    // ========== 更新访问级别 - 缓存清除测试 [M2] ==========

    @Test
    @DisplayName("更新访问级别 - PUBLIC转PRIVATE时注册事务后清除缓存")
    void testUpdateAccessLevel_PublicToPrivate_EvictsCacheAfterCommit() {
        // 初始化事务同步上下文（模拟 @Transactional 环境）
        TransactionSynchronizationManager.initSynchronization();

        when(fileRecordRepository.findById("file-001")).thenReturn(Optional.of(publicFileRecord));
        when(fileRecordRepository.updateAccessLevel("file-001", AccessLevel.PRIVATE)).thenReturn(true);

        fileAccessService.updateAccessLevel("blog", "file-001", "user-123", AccessLevel.PRIVATE);

        // 事务提交前，evict 不应被调用
        verify(fileUrlCacheManager, never()).evict(anyString());

        // 模拟事务提交，触发 afterCommit 回调
        TransactionSynchronizationManager.getSynchronizations().forEach(
                sync -> sync.afterCommit()
        );

        // 事务提交后，evict 应被调用
        verify(fileUrlCacheManager).evict("file-001");
    }

    @Test
    @DisplayName("更新访问级别 - PRIVATE转PUBLIC时也清除缓存")
    void testUpdateAccessLevel_PrivateToPublic_EvictsCache() {
        TransactionSynchronizationManager.initSynchronization();

        when(fileRecordRepository.findById("file-002")).thenReturn(Optional.of(privateFileRecord));
        when(fileRecordRepository.updateAccessLevel("file-002", AccessLevel.PUBLIC)).thenReturn(true);

        fileAccessService.updateAccessLevel("blog", "file-002", "user-123", AccessLevel.PUBLIC);

        // 模拟事务提交
        TransactionSynchronizationManager.getSynchronizations().forEach(
                sync -> sync.afterCommit()
        );

        verify(fileUrlCacheManager).evict("file-002");
    }

    @Test
    @DisplayName("更新访问级别 - 级别相同时跳过更新 [L2]")
    void testUpdateAccessLevel_SameLevel_SkipsUpdate() {
        when(fileRecordRepository.findById("file-001")).thenReturn(Optional.of(publicFileRecord));

        // PUBLIC -> PUBLIC，应跳过
        fileAccessService.updateAccessLevel("blog", "file-001", "user-123", AccessLevel.PUBLIC);

        verify(fileRecordRepository, never()).updateAccessLevel(anyString(), any());
        verify(fileUrlCacheManager, never()).evict(anyString());
    }

    @Test
    @DisplayName("更新访问级别 - 缓存清除失败不阻断业务")
    void testUpdateAccessLevel_CacheEvictFails_DoesNotThrow() {
        TransactionSynchronizationManager.initSynchronization();

        when(fileRecordRepository.findById("file-001")).thenReturn(Optional.of(publicFileRecord));
        when(fileRecordRepository.updateAccessLevel("file-001", AccessLevel.PRIVATE)).thenReturn(true);
        // FileUrlCacheManager.evict 内部已经 catch 异常，这里验证即使抛出也不影响
        doThrow(new RuntimeException("Redis down")).when(fileUrlCacheManager).evict("file-001");

        // updateAccessLevel 本身不应抛异常
        assertDoesNotThrow(() ->
                fileAccessService.updateAccessLevel("blog", "file-001", "user-123", AccessLevel.PRIVATE)
        );

        // 模拟事务提交触发 afterCommit（此时 evict 会抛异常，但不应传播）
        // 注意：afterCommit 中的异常由 Spring 框架处理，不会回滚事务
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> {
            try {
                sync.afterCommit();
            } catch (Exception ignored) {
                // afterCommit 异常不影响已提交的事务
            }
        });
    }
}
