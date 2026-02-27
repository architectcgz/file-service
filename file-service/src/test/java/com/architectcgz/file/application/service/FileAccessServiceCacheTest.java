package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.FileUrlResponse;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.infrastructure.cache.FileRedisKeys;
import com.architectcgz.file.infrastructure.config.CacheProperties;
import com.architectcgz.file.infrastructure.config.S3Properties;
import com.architectcgz.file.infrastructure.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * FileAccessService 缓存功能单元测试
 * 
 * 测试范围：
 * - 缓存命中场景
 * - 缓存未命中场景
 * - 缓存写入
 * - Redis 异常时的降级
 * - 缓存开关配置
 * - 私有文件不缓存
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
    private RedisTemplate<String, String> redisTemplate;
    
    @Mock
    private ValueOperations<String, String> valueOperations;
    
    @Mock
    private CacheProperties cacheProperties;
    
    @Mock
    private CacheProperties.UrlCache urlCache;
    
    @InjectMocks
    private FileAccessService fileAccessService;
    
    private FileRecord publicFileRecord;
    private FileRecord privateFileRecord;
    
    @BeforeEach
    void setUp() {
        // 设置私有URL过期时间
        ReflectionTestUtils.setField(fileAccessService, "privateUrlExpireSeconds", 3600);
        
        // 准备测试数据
        publicFileRecord = FileRecord.builder()
                .id("file-001")
                .appId("blog")
                .userId("user-123")
                .storageObjectId("storage-001")
                .originalFilename("test.jpg")
                .storagePath("2026/02/10/user-123/test.jpg")
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
                .userId("user-123")
                .storageObjectId("storage-002")
                .originalFilename("private.jpg")
                .storagePath("2026/02/10/user-123/private.jpg")
                .fileSize(2048L)
                .contentType("image/jpeg")
                .fileHash("def456")
                .hashAlgorithm("MD5")
                .status(FileStatus.COMPLETED)
                .accessLevel(AccessLevel.PRIVATE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
    
    // ========== 缓存命中测试 ==========
    
    @Test
    @DisplayName("缓存命中 - 直接返回缓存的URL")
    void testCacheHit_ReturnsUrlFromCache() {
        // Given
        String fileId = "file-001";
        String cachedUrl = "https://cdn.example.com/2026/02/10/user-123/test.jpg";
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(cachedUrl);
        
        // When
        FileUrlResponse response = fileAccessService.getFileUrl("blog", fileId, "user-123");
        
        // Then
        assertNotNull(response);
        assertEquals(cachedUrl, response.getUrl());
        assertTrue(response.getPermanent());
        assertNull(response.getExpiresAt());
        
        // 验证没有查询数据库
        verify(fileRecordRepository, never()).findById(any());
        verify(storageService, never()).getPublicUrl(any());
        
        // 验证读取了缓存
        verify(valueOperations).get(cacheKey);
    }
    
    // ========== 缓存未命中测试 ==========
    
    @Test
    @DisplayName("缓存未命中 - 查询数据库并写入缓存")
    void testCacheMiss_QueriesDatabaseAndCachesResult() {
        // Given
        String fileId = "file-001";
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        String publicUrl = "https://cdn.example.com/2026/02/10/user-123/test.jpg";
        
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(cacheProperties.getUrl()).thenReturn(urlCache);
        when(urlCache.getTtl()).thenReturn(3600L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null); // 缓存未命中
        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(publicFileRecord));
        when(storageService.getPublicUrl(publicFileRecord.getStoragePath())).thenReturn(publicUrl);
        
        // When
        FileUrlResponse response = fileAccessService.getFileUrl("blog", fileId, "user-123");
        
        // Then
        assertNotNull(response);
        assertEquals(publicUrl, response.getUrl());
        assertTrue(response.getPermanent());
        assertNull(response.getExpiresAt());
        
        // 验证查询了数据库
        verify(fileRecordRepository).findById(fileId);
        verify(storageService).getPublicUrl(publicFileRecord.getStoragePath());
        
        // 验证写入了缓存
        verify(valueOperations).set(cacheKey, publicUrl, 3600L, TimeUnit.SECONDS);
    }
    
    // ========== 缓存写入测试 ==========
    
    @Test
    @DisplayName("缓存写入 - 公开文件URL写入缓存")
    void testCacheWrite_PublicFileUrlIsCached() {
        // Given
        String fileId = "file-001";
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        String publicUrl = "https://cdn.example.com/2026/02/10/user-123/test.jpg";
        
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(cacheProperties.getUrl()).thenReturn(urlCache);
        when(urlCache.getTtl()).thenReturn(3600L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(publicFileRecord));
        when(storageService.getPublicUrl(publicFileRecord.getStoragePath())).thenReturn(publicUrl);
        
        // When
        fileAccessService.getFileUrl("blog", fileId, "user-123");
        
        // Then
        verify(valueOperations).set(cacheKey, publicUrl, 3600L, TimeUnit.SECONDS);
    }
    
    @Test
    @DisplayName("缓存写入 - 使用配置的TTL")
    void testCacheWrite_UsesConfiguredTTL() {
        // Given
        String fileId = "file-001";
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        String publicUrl = "https://cdn.example.com/2026/02/10/user-123/test.jpg";
        long customTtl = 7200L; // 2小时
        
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(cacheProperties.getUrl()).thenReturn(urlCache);
        when(urlCache.getTtl()).thenReturn(customTtl);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(publicFileRecord));
        when(storageService.getPublicUrl(publicFileRecord.getStoragePath())).thenReturn(publicUrl);
        
        // When
        fileAccessService.getFileUrl("blog", fileId, "user-123");
        
        // Then
        verify(valueOperations).set(cacheKey, publicUrl, customTtl, TimeUnit.SECONDS);
    }
    
    @Test
    @DisplayName("缓存写入失败 - 不影响业务流程")
    void testCacheWrite_FailureDoesNotAffectBusiness() {
        // Given
        String fileId = "file-001";
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        String publicUrl = "https://cdn.example.com/2026/02/10/user-123/test.jpg";
        
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(cacheProperties.getUrl()).thenReturn(urlCache);
        when(urlCache.getTtl()).thenReturn(3600L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(publicFileRecord));
        when(storageService.getPublicUrl(publicFileRecord.getStoragePath())).thenReturn(publicUrl);
        
        // 模拟缓存写入失败
        doThrow(new RuntimeException("Redis connection failed"))
                .when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        
        // When & Then - 不应该抛出异常
        assertDoesNotThrow(() -> {
            FileUrlResponse response = fileAccessService.getFileUrl("blog", fileId, "user-123");
            assertNotNull(response);
            assertEquals(publicUrl, response.getUrl());
        });
    }
    
    // ========== 私有文件不缓存测试 ==========
    
    @Test
    @DisplayName("私有文件 - 不缓存预签名URL")
    void testPrivateFile_DoesNotCachePresignedUrl() {
        // Given
        String fileId = "file-002";
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        String presignedUrl = "https://s3.example.com/bucket/path?X-Amz-Signature=...";
        
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(privateFileRecord));
        when(storageService.generatePresignedUrl(eq(privateFileRecord.getStoragePath()), any(Duration.class)))
                .thenReturn(presignedUrl);
        
        // When
        FileUrlResponse response = fileAccessService.getFileUrl("blog", fileId, "user-123");
        
        // Then
        assertNotNull(response);
        assertEquals(presignedUrl, response.getUrl());
        assertFalse(response.getPermanent());
        assertNotNull(response.getExpiresAt());
        
        // 验证没有写入缓存（私有文件的预签名URL不缓存）
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }
    
    // ========== Redis 异常降级测试 ==========
    
    @Test
    @DisplayName("Redis异常 - 降级到数据库查询")
    void testRedisException_FallbackToDatabase() {
        // Given
        String fileId = "file-001";
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        String publicUrl = "https://cdn.example.com/2026/02/10/user-123/test.jpg";
        
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // 模拟Redis读取异常
        when(valueOperations.get(cacheKey)).thenThrow(new RuntimeException("Redis connection timeout"));
        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(publicFileRecord));
        when(storageService.getPublicUrl(publicFileRecord.getStoragePath())).thenReturn(publicUrl);
        
        // When & Then - 不应该抛出异常，应该降级到数据库查询
        assertDoesNotThrow(() -> {
            FileUrlResponse response = fileAccessService.getFileUrl("blog", fileId, "user-123");
            assertNotNull(response);
            assertEquals(publicUrl, response.getUrl());
        });
        
        // 验证查询了数据库
        verify(fileRecordRepository).findById(fileId);
        verify(storageService).getPublicUrl(publicFileRecord.getStoragePath());
    }
    
    @Test
    @DisplayName("Redis异常 - 缓存读取失败返回null")
    void testRedisException_CacheReadReturnsNull() {
        // Given
        String fileId = "file-001";
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        String publicUrl = "https://cdn.example.com/2026/02/10/user-123/test.jpg";
        
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenThrow(new RuntimeException("Redis error"));
        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(publicFileRecord));
        when(storageService.getPublicUrl(publicFileRecord.getStoragePath())).thenReturn(publicUrl);
        
        // When
        FileUrlResponse response = fileAccessService.getFileUrl("blog", fileId, "user-123");
        
        // Then
        assertNotNull(response);
        assertEquals(publicUrl, response.getUrl());
        
        // 验证继续执行了数据库查询
        verify(fileRecordRepository).findById(fileId);
    }
    
    // ========== 缓存开关测试 ==========
    
    @Test
    @DisplayName("缓存禁用 - 不读取缓存")
    void testCacheDisabled_DoesNotReadCache() {
        // Given
        String fileId = "file-001";
        String publicUrl = "https://cdn.example.com/2026/02/10/user-123/test.jpg";
        
        when(cacheProperties.isEnabled()).thenReturn(false); // 禁用缓存
        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(publicFileRecord));
        when(storageService.getPublicUrl(publicFileRecord.getStoragePath())).thenReturn(publicUrl);
        
        // When
        FileUrlResponse response = fileAccessService.getFileUrl("blog", fileId, "user-123");
        
        // Then
        assertNotNull(response);
        assertEquals(publicUrl, response.getUrl());
        
        // 验证没有读取缓存
        verify(valueOperations, never()).get(any());
        
        // 验证查询了数据库
        verify(fileRecordRepository).findById(fileId);
    }
    
    @Test
    @DisplayName("缓存禁用 - 不写入缓存")
    void testCacheDisabled_DoesNotWriteCache() {
        // Given
        String fileId = "file-001";
        String publicUrl = "https://cdn.example.com/2026/02/10/user-123/test.jpg";
        
        when(cacheProperties.isEnabled()).thenReturn(false); // 禁用缓存
        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(publicFileRecord));
        when(storageService.getPublicUrl(publicFileRecord.getStoragePath())).thenReturn(publicUrl);
        
        // When
        fileAccessService.getFileUrl("blog", fileId, "user-123");
        
        // Then
        // 验证没有写入缓存
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }
    
    // ========== 边界情况测试 ==========
    
    @Test
    @DisplayName("文件不存在 - 抛出异常")
    void testFileNotFound_ThrowsException() {
        // Given
        String fileId = "non-existent";
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.empty());
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileAccessService.getFileUrl("blog", fileId, "user-123");
        });
        
        assertTrue(exception.getMessage().contains("文件不存在"));
    }
    
    @Test
    @DisplayName("文件不属于应用 - 抛出异常")
    void testFileNotBelongsToApp_ThrowsException() {
        // Given
        String fileId = "file-001";
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(publicFileRecord));
        
        // When & Then
        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> {
            fileAccessService.getFileUrl("wrong-app", fileId, "user-123");
        });
        
        assertEquals("文件不属于该应用", exception.getMessage());
    }
    
    @Test
    @DisplayName("私有文件非所有者访问 - 抛出异常")
    void testPrivateFileNonOwner_ThrowsException() {
        // Given
        String fileId = "file-002";
        String cacheKey = FileRedisKeys.fileUrl(fileId);
        
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(privateFileRecord));
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileAccessService.getFileUrl("blog", fileId, "other-user");
        });
        
        assertTrue(exception.getMessage().contains("无权访问该文件"));
    }
    
    // ========== 缓存Key格式测试 ==========
    
    @Test
    @DisplayName("缓存Key格式 - 使用正确的Key格式")
    void testCacheKey_UsesCorrectFormat() {
        // Given
        String fileId = "file-001";
        String expectedCacheKey = "file:file-001:url";
        String publicUrl = "https://cdn.example.com/2026/02/10/user-123/test.jpg";
        
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(cacheProperties.getUrl()).thenReturn(urlCache);
        when(urlCache.getTtl()).thenReturn(3600L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(expectedCacheKey)).thenReturn(null);
        when(fileRecordRepository.findById(fileId)).thenReturn(Optional.of(publicFileRecord));
        when(storageService.getPublicUrl(publicFileRecord.getStoragePath())).thenReturn(publicUrl);
        
        // When
        fileAccessService.getFileUrl("blog", fileId, "user-123");
        
        // Then
        verify(valueOperations).get(expectedCacheKey);
        verify(valueOperations).set(eq(expectedCacheKey), eq(publicUrl), anyLong(), any(TimeUnit.class));
    }
}
