package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.FileUrlResponse;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.common.exception.FileNotFoundException;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.infrastructure.cache.FileUrlCacheManager;
import com.architectcgz.file.infrastructure.config.S3Properties;
import com.architectcgz.file.infrastructure.storage.StorageService;
import net.jqwik.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * FileAccessService 属性测试
 * 
 * Feature: file-service-optimization
 * 使用基于属性的测试验证文件访问服务的正确性属性
 */
class FileAccessServicePropertyTest {

    /**
     * Feature: file-service-optimization, Property 2: 公开文件 URL 生成
     * 
     * 属性：对于任何访问级别为 PUBLIC 的文件，获取文件 URL 时应该返回 Public_Bucket 的直接访问 URL，
     * 而不是预签名 URL。
     * 
     * 验证需求：2.1
     */
    @Property(tries = 100)
    @Label("Property 2: 公开文件 URL 生成 - 公开文件返回永久直接访问URL")
    void publicFileUrlGeneration(
            @ForAll("fileRecords") FileRecord fileRecord,
            @ForAll("userIds") String requestUserId
    ) {
        // Given: 设置文件为公开访问级别
        fileRecord.setAccessLevel(AccessLevel.PUBLIC);
        fileRecord.setStatus(FileStatus.COMPLETED);
        
        // 创建 mock 依赖
        FileRecordRepository mockRepository = mock(FileRecordRepository.class);
        StorageObjectRepository mockStorageObjectRepository = mock(StorageObjectRepository.class);
        StorageService mockStorageService = mock(StorageService.class);
        S3Properties mockS3Properties = mock(S3Properties.class);
        FileUrlCacheManager mockFileUrlCacheManager = mock(FileUrlCacheManager.class);
        AccessLevelChangeTransactionHelper mockAccessLevelChangeTransactionHelper = mock(AccessLevelChangeTransactionHelper.class);
        
        when(mockRepository.findById(fileRecord.getId())).thenReturn(Optional.of(fileRecord));
        when(mockStorageObjectRepository.findById(anyString())).thenReturn(Optional.empty());
        when(mockFileUrlCacheManager.get(fileRecord.getId())).thenReturn(null);
        
        // 公开文件应该调用 getPublicUrl 而不是 generatePresignedUrl
        String expectedPublicUrl = "https://cdn.example.com/" + fileRecord.getStoragePath();
        when(mockStorageService.getPublicUrl((String) isNull(), eq(fileRecord.getStoragePath()))).thenReturn(expectedPublicUrl);
        
        // 创建 FileAccessService
        FileAccessService service = new FileAccessService(
                mockRepository, 
                mockStorageObjectRepository,
                mockStorageService, 
                mockS3Properties,
                mockFileUrlCacheManager,
                mockAccessLevelChangeTransactionHelper
        );
        ReflectionTestUtils.setField(service, "privateUrlExpireSeconds", 3600);
        
        // When: 获取文件 URL（任何用户都可以访问公开文件）
        FileUrlResponse response = service.getFileUrl(fileRecord.getAppId(), fileRecord.getId(), requestUserId);
        
        // Then: 验证返回的是公开 URL
        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getUrl(), "URL should not be null");
        assertEquals(expectedPublicUrl, response.getUrl(), "Should return public URL");
        assertTrue(response.getPermanent(), "Public file URL should be permanent");
        assertNull(response.getExpiresAt(), "Public file URL should not have expiration time");
        
        // 验证调用了 getPublicUrl 而不是 generatePresignedUrl
        verify(mockStorageService).getPublicUrl((String) isNull(), eq(fileRecord.getStoragePath()));
        verify(mockStorageService, never()).generatePresignedUrl(anyString(), anyString(), any(Duration.class));
    }

    /**
     * Feature: file-service-optimization, Property 3: 私有文件权限验证
     * 
     * 属性：对于任何访问级别为 PRIVATE 的文件，当非所有者用户请求访问时，
     * 系统应该拒绝请求并抛出 BusinessException。
     * 
     * 验证需求：2.2, 2.3
     */
    @Property(tries = 100)
    @Label("Property 3: 私有文件权限验证 - 非所有者无法访问私有文件")
    void privateFilePermissionVerification(
            @ForAll("fileRecords") FileRecord fileRecord,
            @ForAll("userIds") String nonOwnerUserId
    ) {
        // Given: 设置文件为私有访问级别
        fileRecord.setAccessLevel(AccessLevel.PRIVATE);
        fileRecord.setStatus(FileStatus.COMPLETED);
        
        // 确保请求用户不是文件所有者
        Assume.that(!nonOwnerUserId.equals(fileRecord.getUserId()));
        
        // 创建 mock 依赖
        FileRecordRepository mockRepository = mock(FileRecordRepository.class);
        StorageObjectRepository mockStorageObjectRepository = mock(StorageObjectRepository.class);
        StorageService mockStorageService = mock(StorageService.class);
        S3Properties mockS3Properties = mock(S3Properties.class);
        FileUrlCacheManager mockFileUrlCacheManager = mock(FileUrlCacheManager.class);
        AccessLevelChangeTransactionHelper mockAccessLevelChangeTransactionHelper = mock(AccessLevelChangeTransactionHelper.class);
        
        when(mockRepository.findById(fileRecord.getId())).thenReturn(Optional.of(fileRecord));
        when(mockStorageObjectRepository.findById(anyString())).thenReturn(Optional.empty());
        when(mockFileUrlCacheManager.get(fileRecord.getId())).thenReturn(null);
        
        // 创建 FileAccessService
        FileAccessService service = new FileAccessService(
                mockRepository, 
                mockStorageObjectRepository,
                mockStorageService, 
                mockS3Properties,
                mockFileUrlCacheManager,
                mockAccessLevelChangeTransactionHelper
        );
        ReflectionTestUtils.setField(service, "privateUrlExpireSeconds", 3600);
        
        // When & Then: 非所有者访问私有文件应该抛出异常
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getFileUrl(fileRecord.getAppId(), fileRecord.getId(), nonOwnerUserId),
                "Non-owner should not be able to access private file"
        );
        
        // 验证异常消息
        assertTrue(exception.getMessage().contains("无权访问该文件"), 
                "Exception message should indicate access denied");
        
        // 验证没有调用 URL 生成方法
        verify(mockStorageService, never()).getPublicUrl(anyString(), anyString());
        verify(mockStorageService, never()).generatePresignedUrl(anyString(), anyString(), any(Duration.class));
    }

    /**
     * Feature: file-service-optimization, Property 4: 私有文件预签名 URL 生成
     * 
     * 属性：对于任何访问级别为 PRIVATE 的文件，当所有者用户请求访问时，
     * 系统应该返回带有过期时间的预签名 URL。
     * 
     * 验证需求：2.4
     */
    @Property(tries = 100)
    @Label("Property 4: 私有文件预签名 URL 生成 - 所有者获取带过期时间的预签名URL")
    void privateFilePresignedUrlGeneration(
            @ForAll("fileRecords") FileRecord fileRecord
    ) {
        // Given: 设置文件为私有访问级别
        fileRecord.setAccessLevel(AccessLevel.PRIVATE);
        fileRecord.setStatus(FileStatus.COMPLETED);
        
        // 使用文件所有者作为请求用户
        String ownerUserId = fileRecord.getUserId();
        
        // 创建 mock 依赖
        FileRecordRepository mockRepository = mock(FileRecordRepository.class);
        StorageObjectRepository mockStorageObjectRepository = mock(StorageObjectRepository.class);
        StorageService mockStorageService = mock(StorageService.class);
        S3Properties mockS3Properties = mock(S3Properties.class);
        FileUrlCacheManager mockFileUrlCacheManager = mock(FileUrlCacheManager.class);
        AccessLevelChangeTransactionHelper mockAccessLevelChangeTransactionHelper = mock(AccessLevelChangeTransactionHelper.class);
        
        when(mockRepository.findById(fileRecord.getId())).thenReturn(Optional.of(fileRecord));
        when(mockStorageObjectRepository.findById(anyString())).thenReturn(Optional.empty());
        when(mockFileUrlCacheManager.get(fileRecord.getId())).thenReturn(null);
        
        // 私有文件应该调用 generatePresignedUrl
        String expectedPresignedUrl = "https://s3.example.com/bucket/" + fileRecord.getStoragePath() + 
                "?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=...&X-Amz-Signature=...";
        when(mockStorageService.generatePresignedUrl((String) isNull(), anyString(), any(Duration.class)))
                .thenReturn(expectedPresignedUrl);
        
        // 创建 FileAccessService
        FileAccessService service = new FileAccessService(
                mockRepository, 
                mockStorageObjectRepository,
                mockStorageService, 
                mockS3Properties,
                mockFileUrlCacheManager,
                mockAccessLevelChangeTransactionHelper
        );
        int expireSeconds = 3600;
        ReflectionTestUtils.setField(service, "privateUrlExpireSeconds", expireSeconds);
        
        // When: 文件所有者获取文件 URL
        LocalDateTime beforeRequest = LocalDateTime.now();
        FileUrlResponse response = service.getFileUrl(fileRecord.getAppId(), fileRecord.getId(), ownerUserId);
        LocalDateTime afterRequest = LocalDateTime.now();
        
        // Then: 验证返回的是预签名 URL
        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getUrl(), "URL should not be null");
        assertEquals(expectedPresignedUrl, response.getUrl(), "Should return presigned URL");
        assertFalse(response.getPermanent(), "Private file URL should not be permanent");
        assertNotNull(response.getExpiresAt(), "Private file URL should have expiration time");
        
        // 验证过期时间在合理范围内（当前时间 + expireSeconds）
        LocalDateTime expectedExpiresAt = beforeRequest.plusSeconds(expireSeconds);
        LocalDateTime maxExpiresAt = afterRequest.plusSeconds(expireSeconds);
        assertTrue(response.getExpiresAt().isAfter(expectedExpiresAt.minusSeconds(5)), 
                "Expiration time should be after expected time (with 5s tolerance)");
        assertTrue(response.getExpiresAt().isBefore(maxExpiresAt.plusSeconds(5)), 
                "Expiration time should be before max time (with 5s tolerance)");
        
        // 验证调用了 generatePresignedUrl 而不是 getPublicUrl
        verify(mockStorageService).generatePresignedUrl(
                isNull(),
                eq(fileRecord.getStoragePath()),
                eq(Duration.ofSeconds(expireSeconds))
        );
        verify(mockStorageService, never()).getPublicUrl(anyString(), anyString());
    }

    /**
     * Property 5: 跨租户访问被拒绝
     *
     * 属性：对于任何文件，当使用不同于文件所属应用的 appId 请求时，
     * 系统应该拒绝请求并抛出 FileNotFoundException（不暴露文件存在性）。
     */
    @Property(tries = 100)
    @Label("Property 5: 跨租户访问被拒绝 - 不同 appId 返回 404")
    void crossTenantAccessDenied(
            @ForAll("fileRecords") FileRecord fileRecord,
            @ForAll("appIds") String requestAppId,
            @ForAll("userIds") String requestUserId
    ) {
        // 确保请求的 appId 与文件的 appId 不同
        Assume.that(!requestAppId.equals(fileRecord.getAppId()));

        fileRecord.setAccessLevel(AccessLevel.PUBLIC);
        fileRecord.setStatus(FileStatus.COMPLETED);

        // 创建 mock 依赖
        FileRecordRepository mockRepository = mock(FileRecordRepository.class);
        StorageObjectRepository mockStorageObjectRepository = mock(StorageObjectRepository.class);
        StorageService mockStorageService = mock(StorageService.class);
        S3Properties mockS3Properties = mock(S3Properties.class);
        FileUrlCacheManager mockFileUrlCacheManager = mock(FileUrlCacheManager.class);
        AccessLevelChangeTransactionHelper mockAccessLevelChangeTransactionHelper = mock(AccessLevelChangeTransactionHelper.class);

        when(mockRepository.findById(fileRecord.getId())).thenReturn(Optional.of(fileRecord));
        when(mockStorageObjectRepository.findById(anyString())).thenReturn(Optional.empty());
        when(mockFileUrlCacheManager.get(fileRecord.getId())).thenReturn(null);

        FileAccessService service = new FileAccessService(
                mockRepository, mockStorageObjectRepository, mockStorageService, mockS3Properties,
                mockFileUrlCacheManager, mockAccessLevelChangeTransactionHelper
        );
        ReflectionTestUtils.setField(service, "privateUrlExpireSeconds", 3600);

        // When & Then: 跨租户访问应抛出 FileNotFoundException
        FileNotFoundException exception = assertThrows(
                FileNotFoundException.class,
                () -> service.getFileUrl(requestAppId, fileRecord.getId(), requestUserId),
                "Cross-tenant access should throw FileNotFoundException"
        );

        assertTrue(exception.getMessage().contains("文件不存在"),
                "Exception message should indicate file not found");

        // 验证没有调用 URL 生成方法
        verify(mockStorageService, never()).getPublicUrl(anyString(), anyString());
        verify(mockStorageService, never()).generatePresignedUrl(anyString(), anyString(), any(Duration.class));
    }

    // ========== Arbitraries (数据生成器) ==========

    /**
     * 生成文件记录
     */
    @Provide
    Arbitrary<FileRecord> fileRecords() {
        // jqwik 的 combine 最多支持 8 个参数，所以需要分组
        Arbitrary<FileRecord> part1 = Combinators.combine(
                fileIds(),
                appIds(),
                userIds(),
                storageObjectIds(),
                originalFilenames(),
                storagePaths(),
                fileSizes(),
                contentTypes()
        ).as((fileId, appId, userId, storageObjectId, originalFilename, storagePath, 
              fileSize, contentType) ->
                FileRecord.builder()
                        .id(fileId)
                        .appId(appId)
                        .userId(userId)
                        .storageObjectId(storageObjectId)
                        .originalFilename(originalFilename)
                        .storagePath(storagePath)
                        .fileSize(fileSize)
                        .contentType(contentType)
                        .status(FileStatus.COMPLETED)
                        .accessLevel(AccessLevel.PUBLIC) // 默认值，测试中会修改
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()
        );
        
        // 添加 hash 相关字段
        return Combinators.combine(
                part1,
                fileHashes(),
                hashAlgorithms()
        ).as((record, fileHash, hashAlgorithm) -> {
            record.setFileHash(fileHash);
            record.setHashAlgorithm(hashAlgorithm);
            return record;
        });
    }

    /**
     * 生成文件ID
     */
    @Provide
    Arbitrary<String> fileIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(8)
                .ofMaxLength(32)
                .map(s -> "file-" + s);
    }

    /**
     * 生成应用ID
     */
    @Provide
    Arbitrary<String> appIds() {
        return Arbitraries.of("blog", "im", "forum", "shop", "cms");
    }

    /**
     * 生成用户ID
     */
    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(s -> "user-" + s);
    }

    /**
     * 生成存储对象ID
     */
    @Provide
    Arbitrary<String> storageObjectIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(8)
                .ofMaxLength(32)
                .map(s -> "storage-" + s);
    }

    /**
     * 生成原始文件名
     */
    @Provide
    Arbitrary<String> originalFilenames() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20),
                Arbitraries.of("jpg", "png", "pdf", "txt", "mp4", "mp3", "doc", "zip")
        ).as((name, ext) -> name + "." + ext);
    }

    /**
     * 生成存储路径
     * 格式: {tenantId}/{year}/{month}/{day}/{userId}/{type}/{fileId}.{ext}
     */
    @Provide
    Arbitrary<String> storagePaths() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10), // tenantId
                Arbitraries.integers().between(2020, 2030), // year
                Arbitraries.integers().between(1, 12), // month
                Arbitraries.integers().between(1, 28), // day
                Arbitraries.strings().alpha().numeric().ofMinLength(5).ofMaxLength(15), // userId
                Arbitraries.of("images", "files", "videos", "audios"), // type
                Arbitraries.strings().alpha().numeric().ofMinLength(8).ofMaxLength(16), // fileId
                Arbitraries.of("jpg", "png", "pdf", "txt", "mp4", "mp3") // extension
        ).as((tenantId, year, month, day, userId, type, fileId, ext) ->
                String.format("%s/%04d/%02d/%02d/%s/%s/%s.%s",
                        tenantId, year, month, day, userId, type, fileId, ext)
        );
    }

    /**
     * 生成文件大小（字节）
     */
    @Provide
    Arbitrary<Long> fileSizes() {
        return Arbitraries.longs().between(1L, 104857600L); // 1 byte to 100MB
    }

    /**
     * 生成内容类型
     */
    @Provide
    Arbitrary<String> contentTypes() {
        return Arbitraries.of(
                "image/jpeg",
                "image/png",
                "image/gif",
                "application/pdf",
                "text/plain",
                "video/mp4",
                "audio/mpeg",
                "application/zip"
        );
    }

    /**
     * 生成文件哈希值
     */
    @Provide
    Arbitrary<String> fileHashes() {
        return Arbitraries.strings()
                .withCharRange('a', 'f')
                .numeric()
                .ofLength(32); // MD5 hash length
    }

    /**
     * 生成哈希算法
     */
    @Provide
    Arbitrary<String> hashAlgorithms() {
        return Arbitraries.of("MD5", "SHA256");
    }
}
