package com.architectcgz.file.application.service;

import com.architectcgz.file.common.exception.FileNotFoundException;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.TenantRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import com.architectcgz.file.infrastructure.cache.FileRedisKeys;
import com.architectcgz.file.infrastructure.config.CacheProperties;
import com.architectcgz.file.infrastructure.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FileManagementService 缓存清除功能单元测试
 * 
 * 测试范围：
 * - 文件删除时清除缓存
 * - 缓存清除失败不影响文件删除
 * - 缓存开关配置
 * - Redis 异常处理
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FileManagementService 缓存清除功能测试")
class FileManagementServiceCacheTest {
    
    @Mock
    private FileRecordRepository fileRecordRepository;
    
    @Mock
    private TenantRepository tenantRepository;
    
    @Mock
    private TenantUsageRepository tenantUsageRepository;
    
    @Mock
    private StorageService storageService;
    
    @Mock
    private AuditLogService auditLogService;
    
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    
    @Mock
    private CacheProperties cacheProperties;
    
    @InjectMocks
    private FileManagementService fileManagementService;
    
    private FileRecord testFileRecord;
    
    @BeforeEach
    void setUp() {
        // 准备测试数据
        testFileRecord = FileRecord.builder()
                .id("file-001")
                .appId("blog")
                .userId("user-123")
                .storageObjectId("storage-001")
                .originalFilename("test.jpg")
                .storagePath("2026/02/11/user-123/test.jpg")
                .fileSize(1024L)
                .contentType("image/jpeg")
                .fileHash("abc123")
                .hashAlgorithm("MD5")
                .status(FileStatus.COMPLETED)
                .accessLevel(AccessLevel.PUBLIC)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
    
    // ========== 缓存清除测试 ==========
    
    @Test
    @DisplayName("文件删除 - 成功清除缓存")
    void testDeleteFile_ClearsCacheSuccessfully() {
        // Given
        String fileId = "file-001";
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        String adminUserId = "admin-001";
        
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(testFileRecord));
        when(fileRecordRepository.deleteById(fileId)).thenReturn(true);
        when(redisTemplate.delete(cacheKey)).thenReturn(true);
        doNothing().when(storageService).delete(anyString());
        doNothing().when(tenantUsageRepository).decrementUsage(anyString(), anyLong());
        doNothing().when(auditLogService).log(any());
        
        // When
        fileManagementService.deleteFile(fileId, adminUserId);
        
        // Then
        verify(redisTemplate).delete(cacheKey);
        verify(fileRecordRepository).deleteById(fileId);
        verify(storageService).delete(testFileRecord.getStoragePath());
        verify(tenantUsageRepository).decrementUsage(testFileRecord.getAppId(), testFileRecord.getFileSize());
    }
    
    @Test
    @DisplayName("文件删除 - 缓存不存在时正常处理")
    void testDeleteFile_CacheNotFound() {
        // Given
        String fileId = "file-001";
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        String adminUserId = "admin-001";
        
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(testFileRecord));
        when(fileRecordRepository.deleteById(fileId)).thenReturn(true);
        when(redisTemplate.delete(cacheKey)).thenReturn(false); // 缓存不存在
        doNothing().when(storageService).delete(anyString());
        doNothing().when(tenantUsageRepository).decrementUsage(anyString(), anyLong());
        doNothing().when(auditLogService).log(any());
        
        // When & Then - 不应该抛出异常
        assertDoesNotThrow(() -> {
            fileManagementService.deleteFile(fileId, adminUserId);
        });
        
        verify(redisTemplate).delete(cacheKey);
        verify(fileRecordRepository).deleteById(fileId);
    }
    
    @Test
    @DisplayName("文件删除 - 缓存清除失败不影响文件删除")
    void testDeleteFile_CacheClearFailureDoesNotAffectDeletion() {
        // Given
        String fileId = "file-001";
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        String adminUserId = "admin-001";
        
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(testFileRecord));
        when(fileRecordRepository.deleteById(fileId)).thenReturn(true);
        when(redisTemplate.delete(cacheKey)).thenThrow(new RuntimeException("Redis connection failed"));
        doNothing().when(storageService).delete(anyString());
        doNothing().when(tenantUsageRepository).decrementUsage(anyString(), anyLong());
        doNothing().when(auditLogService).log(any());
        
        // When & Then - 不应该抛出异常，文件删除应该成功
        assertDoesNotThrow(() -> {
            fileManagementService.deleteFile(fileId, adminUserId);
        });
        
        // 验证文件删除操作仍然执行
        verify(fileRecordRepository).deleteById(fileId);
        verify(storageService).delete(testFileRecord.getStoragePath());
        verify(tenantUsageRepository).decrementUsage(testFileRecord.getAppId(), testFileRecord.getFileSize());
    }
    
    // ========== 缓存开关测试 ==========
    
    @Test
    @DisplayName("缓存禁用 - 不清除缓存")
    void testDeleteFile_CacheDisabled() {
        // Given
        String fileId = "file-001";
        String adminUserId = "admin-001";
        
        when(cacheProperties.isEnabled()).thenReturn(false); // 禁用缓存
        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(testFileRecord));
        when(fileRecordRepository.deleteById(fileId)).thenReturn(true);
        doNothing().when(storageService).delete(anyString());
        doNothing().when(tenantUsageRepository).decrementUsage(anyString(), anyLong());
        doNothing().when(auditLogService).log(any());
        
        // When
        fileManagementService.deleteFile(fileId, adminUserId);
        
        // Then
        // 验证没有调用 Redis 删除操作
        verify(redisTemplate, never()).delete(anyString());
        
        // 验证文件删除操作正常执行
        verify(fileRecordRepository).deleteById(fileId);
        verify(storageService).delete(testFileRecord.getStoragePath());
    }
    
    // ========== 缓存 Key 格式测试 ==========
    
    @Test
    @DisplayName("缓存 Key 格式 - 使用正确的 Key 格式")
    void testDeleteFile_UsesCorrectCacheKeyFormat() {
        // Given
        String fileId = "file-001";
        String expectedCacheKey = "file:file-001:url";
        String adminUserId = "admin-001";
        
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(testFileRecord));
        when(fileRecordRepository.deleteById(fileId)).thenReturn(true);
        when(redisTemplate.delete(expectedCacheKey)).thenReturn(true);
        doNothing().when(storageService).delete(anyString());
        doNothing().when(tenantUsageRepository).decrementUsage(anyString(), anyLong());
        doNothing().when(auditLogService).log(any());
        
        // When
        fileManagementService.deleteFile(fileId, adminUserId);
        
        // Then
        verify(redisTemplate).delete(expectedCacheKey);
    }
    
    // ========== 边界情况测试 ==========
    
    @Test
    @DisplayName("文件不存在 - 抛出异常")
    void testDeleteFile_FileNotFound() {
        // Given
        String fileId = "non-existent";
        String adminUserId = "admin-001";
        
        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.empty());
        
        // When & Then
        FileNotFoundException exception = assertThrows(FileNotFoundException.class, () -> {
            fileManagementService.deleteFile(fileId, adminUserId);
        });
        
        assertEquals("文件不存在: non-existent", exception.getMessage());
        
        // 验证没有执行删除操作
        verify(fileRecordRepository, never()).deleteById(anyString());
        verify(storageService, never()).delete(anyString());
        verify(redisTemplate, never()).delete(anyString());
    }
    
    @Test
    @DisplayName("存储删除失败 - 抛出异常")
    void testDeleteFile_StorageDeletionFails() {
        // Given
        String fileId = "file-001";
        String adminUserId = "admin-001";
        
        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(testFileRecord));
        doThrow(new RuntimeException("Storage deletion failed"))
                .when(storageService).delete(anyString());
        
        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            fileManagementService.deleteFile(fileId, adminUserId);
        });
        
        assertTrue(exception.getMessage().contains("Failed to delete file"));
        
        // 验证没有执行后续操作
        verify(fileRecordRepository, never()).deleteById(anyString());
        verify(redisTemplate, never()).delete(anyString());
    }
    
    // ========== Redis 异常处理测试 ==========
    
    @Test
    @DisplayName("Redis 异常 - 记录警告日志但不影响删除")
    void testDeleteFile_RedisException() {
        // Given
        String fileId = "file-001";
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        String adminUserId = "admin-001";
        
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(testFileRecord));
        when(fileRecordRepository.deleteById(fileId)).thenReturn(true);
        when(redisTemplate.delete(cacheKey)).thenThrow(new RuntimeException("Redis timeout"));
        doNothing().when(storageService).delete(anyString());
        doNothing().when(tenantUsageRepository).decrementUsage(anyString(), anyLong());
        doNothing().when(auditLogService).log(any());
        
        // When & Then - 不应该抛出异常
        assertDoesNotThrow(() -> {
            fileManagementService.deleteFile(fileId, adminUserId);
        });
        
        // 验证文件删除成功
        verify(fileRecordRepository).deleteById(fileId);
        verify(storageService).delete(testFileRecord.getStoragePath());
    }
    
    // ========== 批量删除缓存清除测试 ==========
    
    @Test
    @DisplayName("批量删除 - 清除所有文件的缓存")
    void testBatchDeleteFiles_ClearsAllCaches() {
        // Given
        String fileId1 = "file-001";
        String fileId2 = "file-002";
        String adminUserId = "admin-001";
        
        FileRecord file2 = FileRecord.builder()
                .id(fileId2)
                .appId("blog")
                .userId("user-123")
                .storageObjectId("storage-002")
                .originalFilename("test2.jpg")
                .storagePath("2026/02/11/user-123/test2.jpg")
                .fileSize(2048L)
                .contentType("image/jpeg")
                .fileHash("def456")
                .hashAlgorithm("MD5")
                .status(FileStatus.COMPLETED)
                .accessLevel(AccessLevel.PUBLIC)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(fileRecordRepository.findById(fileId1)).thenReturn(Optional.of(testFileRecord));
        when(fileRecordRepository.findById(fileId2)).thenReturn(Optional.of(file2));
        when(fileRecordRepository.deleteById(anyString())).thenReturn(true);
        when(redisTemplate.delete(eq(FileRedisKeys.fileUrl(fileId1)))).thenReturn(true);
        when(redisTemplate.delete(eq(FileRedisKeys.fileUrl(fileId2)))).thenReturn(true);
        doNothing().when(storageService).delete(anyString());
        doNothing().when(tenantUsageRepository).decrementUsage(anyString(), anyLong());
        doNothing().when(auditLogService).log(any());
        
        // When
        fileManagementService.batchDeleteFiles(java.util.Arrays.asList(fileId1, fileId2), adminUserId);
        
        // Then
        verify(redisTemplate).delete(FileRedisKeys.fileUrl(fileId1));
        verify(redisTemplate).delete(FileRedisKeys.fileUrl(fileId2));
        verify(fileRecordRepository).deleteById(fileId1);
        verify(fileRecordRepository).deleteById(fileId2);
    }
    
    @Test
    @DisplayName("批量删除 - 部分缓存清除失败不影响其他文件")
    void testBatchDeleteFiles_PartialCacheFailure() {
        // Given
        String fileId1 = "file-001";
        String fileId2 = "file-002";
        String adminUserId = "admin-001";
        
        FileRecord file2 = FileRecord.builder()
                .id(fileId2)
                .appId("blog")
                .userId("user-123")
                .storageObjectId("storage-002")
                .originalFilename("test2.jpg")
                .storagePath("2026/02/11/user-123/test2.jpg")
                .fileSize(2048L)
                .contentType("image/jpeg")
                .fileHash("def456")
                .hashAlgorithm("MD5")
                .status(FileStatus.COMPLETED)
                .accessLevel(AccessLevel.PUBLIC)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(fileRecordRepository.findById(fileId1)).thenReturn(Optional.of(testFileRecord));
        when(fileRecordRepository.findById(fileId2)).thenReturn(Optional.of(file2));
        when(fileRecordRepository.deleteById(anyString())).thenReturn(true);
        
        // 第一个文件的缓存清除失败
        when(redisTemplate.delete(eq(FileRedisKeys.fileUrl(fileId1))))
                .thenThrow(new RuntimeException("Redis error"));
        when(redisTemplate.delete(eq(FileRedisKeys.fileUrl(fileId2)))).thenReturn(true);
        
        doNothing().when(storageService).delete(anyString());
        doNothing().when(tenantUsageRepository).decrementUsage(anyString(), anyLong());
        doNothing().when(auditLogService).log(any());
        
        // When
        var result = fileManagementService.batchDeleteFiles(
                java.util.Arrays.asList(fileId1, fileId2), adminUserId);
        
        // Then
        assertEquals(2, result.getSuccessCount(), "两个文件都应该删除成功");
        assertEquals(0, result.getFailureCount(), "不应该有失败");
        
        // 验证两个文件都被删除
        verify(fileRecordRepository).deleteById(fileId1);
        verify(fileRecordRepository).deleteById(fileId2);
    }
}
