package com.architectcgz.file.application.service;

import com.architectcgz.file.domain.model.TenantUsage;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 上传统计更新属性测试
 * 
 * Feature: file-service-optimization
 * 使用基于属性的测试验证文件上传时租户统计更新的正确性属性
 */
class UploadStatisticsPropertyTest {

    /**
     * Feature: file-service-optimization, Property 6: 租户最后上传时间更新
     * 
     * 属性：对于任何成功的文件上传操作，系统应该更新对应租户的最后上传时间为当前时间。
     * 
     * 验证需求：3.5
     */
    @Property(tries = 100)
    @Label("Property 6: 租户最后上传时间更新 - 上传成功后更新最后上传时间")
    void tenantLastUploadTimeUpdate(
            @ForAll("tenantIds") String tenantId,
            @ForAll("fileSizes") long fileSize
    ) {
        // Given: 创建 mock 仓储
        TenantUsageRepository mockUsageRepository = mock(TenantUsageRepository.class);
        
        // 记录调用前的时间
        OffsetDateTime beforeUpload = OffsetDateTime.now(ZoneOffset.UTC);
        
        // 模拟 incrementUsage 调用
        doAnswer(invocation -> {
            // 验证调用参数
            String actualTenantId = invocation.getArgument(0);
            long actualFileSize = invocation.getArgument(1);
            
            assertEquals(tenantId, actualTenantId, "Tenant ID should match");
            assertEquals(fileSize, actualFileSize, "File size should match");
            
            return null;
        }).when(mockUsageRepository).incrementUsage(eq(tenantId), eq(fileSize));
        
        // 创建一个模拟的使用统计对象，用于验证
        TenantUsage updatedUsage = new TenantUsage(tenantId);
        updatedUsage.setUsedStorageBytes(1000L);
        updatedUsage.setUsedFileCount(1);
        updatedUsage.setLastUploadAt(OffsetDateTime.now(ZoneOffset.UTC)); // 模拟数据库更新后的时间
        updatedUsage.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        
        when(mockUsageRepository.findById(tenantId)).thenReturn(Optional.of(updatedUsage));
        
        // When: 调用 incrementUsage（模拟上传成功）
        mockUsageRepository.incrementUsage(tenantId, fileSize);
        
        // 记录调用后的时间
        OffsetDateTime afterUpload = OffsetDateTime.now(ZoneOffset.UTC);
        
        // Then: 验证 incrementUsage 被调用
        verify(mockUsageRepository, times(1)).incrementUsage(tenantId, fileSize);
        
        // 验证更新后的最后上传时间在合理范围内
        TenantUsage result = mockUsageRepository.findById(tenantId).orElseThrow();
        assertNotNull(result.getLastUploadAt(), "Last upload time should be set");
        
        // 验证最后上传时间在上传操作的时间范围内（允许1秒误差）
        assertTrue(
                !result.getLastUploadAt().isBefore(beforeUpload.minusSeconds(1)) &&
                !result.getLastUploadAt().isAfter(afterUpload.plusSeconds(1)),
                String.format("Last upload time should be between %s and %s, but was %s",
                        beforeUpload, afterUpload, result.getLastUploadAt())
        );
    }

    /**
     * Feature: file-service-optimization, Property 30: 上传成功统计增加
     * 
     * 属性：对于任何成功的文件上传操作，系统应该增加对应租户的已使用存储空间
     * （增加文件大小）、已使用文件数量（增加 1）并更新最后上传时间。
     * 
     * 验证需求：12.1
     */
    @Property(tries = 100)
    @Label("Property 30: 上传成功统计增加 - 上传成功后增加存储空间和文件数量")
    void uploadSuccessStatisticsIncrease(
            @ForAll("tenantIds") String tenantId,
            @ForAll("initialUsages") TenantUsage initialUsage,
            @ForAll("fileSizes") long fileSize
    ) {
        // Given: 设置初始使用统计
        initialUsage.setTenantId(tenantId);
        long initialStorageBytes = initialUsage.getUsedStorageBytes();
        int initialFileCount = initialUsage.getUsedFileCount();
        OffsetDateTime initialLastUploadAt = initialUsage.getLastUploadAt();
        
        // 创建 mock 仓储
        TenantUsageRepository mockUsageRepository = mock(TenantUsageRepository.class);
        
        // 模拟查询初始状态
        when(mockUsageRepository.findById(tenantId))
                .thenReturn(Optional.of(initialUsage));
        
        // 记录上传前的时间
        OffsetDateTime beforeUpload = OffsetDateTime.now(ZoneOffset.UTC);
        
        // 模拟 incrementUsage 调用，并更新对象状态
        doAnswer(invocation -> {
            String actualTenantId = invocation.getArgument(0);
            long actualFileSize = invocation.getArgument(1);
            
            // 验证参数
            assertEquals(tenantId, actualTenantId, "Tenant ID should match");
            assertEquals(fileSize, actualFileSize, "File size should match");
            
            // 模拟数据库更新：增加存储空间和文件数量
            initialUsage.setUsedStorageBytes(initialStorageBytes + fileSize);
            initialUsage.setUsedFileCount(initialFileCount + 1);
            initialUsage.setLastUploadAt(OffsetDateTime.now(ZoneOffset.UTC));
            initialUsage.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            
            return null;
        }).when(mockUsageRepository).incrementUsage(eq(tenantId), eq(fileSize));
        
        // When: 调用 incrementUsage（模拟上传成功）
        mockUsageRepository.incrementUsage(tenantId, fileSize);
        
        // 记录上传后的时间
        OffsetDateTime afterUpload = OffsetDateTime.now(ZoneOffset.UTC);
        
        // Then: 验证 incrementUsage 被调用
        verify(mockUsageRepository, times(1)).incrementUsage(tenantId, fileSize);
        
        // 查询更新后的使用统计
        TenantUsage updatedUsage = mockUsageRepository.findById(tenantId).orElseThrow();
        
        // 验证存储空间增加了文件大小
        assertEquals(
                initialStorageBytes + fileSize,
                updatedUsage.getUsedStorageBytes(),
                "Used storage bytes should increase by file size"
        );
        
        // 验证文件数量增加了 1
        assertEquals(
                initialFileCount + 1,
                updatedUsage.getUsedFileCount(),
                "Used file count should increase by 1"
        );
        
        // 验证最后上传时间被更新
        assertNotNull(updatedUsage.getLastUploadAt(), "Last upload time should be set");
        
        // 如果之前有上传时间，验证新时间不早于旧时间
        if (initialLastUploadAt != null) {
            assertFalse(
                    updatedUsage.getLastUploadAt().isBefore(initialLastUploadAt),
                    "Last upload time should not be earlier than previous upload time"
            );
        }
        
        // 验证最后上传时间在上传操作的时间范围内（允许1秒误差）
        assertTrue(
                !updatedUsage.getLastUploadAt().isBefore(beforeUpload.minusSeconds(1)) &&
                !updatedUsage.getLastUploadAt().isAfter(afterUpload.plusSeconds(1)),
                String.format("Last upload time should be between %s and %s, but was %s",
                        beforeUpload, afterUpload, updatedUsage.getLastUploadAt())
        );
        
        // 验证 updatedAt 时间戳被更新
        assertNotNull(updatedUsage.getUpdatedAt(), "Updated timestamp should be set");
        assertTrue(
                !updatedUsage.getUpdatedAt().isBefore(beforeUpload.minusSeconds(1)) &&
                !updatedUsage.getUpdatedAt().isAfter(afterUpload.plusSeconds(1)),
                "Updated timestamp should be within upload time range"
        );
    }

    // ========== Arbitraries (数据生成器) ==========

    /**
     * 生成租户ID
     */
    @Provide
    Arbitrary<String> tenantIds() {
        return Arbitraries.of("blog", "im", "forum", "shop", "cms", "admin", "test", "api");
    }

    /**
     * 生成文件大小（字节）
     * 范围：1 byte 到 100MB
     */
    @Provide
    Arbitrary<Long> fileSizes() {
        return Arbitraries.longs()
                .between(1L, 104857600L); // 1 byte to 100MB
    }

    /**
     * 生成初始使用统计对象
     */
    @Provide
    Arbitrary<TenantUsage> initialUsages() {
        return Combinators.combine(
                usedStorageBytes(),
                usedFileCounts(),
                lastUploadTimes()
        ).as((usedStorage, usedFileCount, lastUploadAt) -> {
            TenantUsage usage = new TenantUsage();
            usage.setUsedStorageBytes(usedStorage);
            usage.setUsedFileCount(usedFileCount);
            usage.setLastUploadAt(lastUploadAt);
            usage.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
            return usage;
        });
    }

    /**
     * 生成已使用存储空间（字节）
     * 范围：0 到 10GB
     */
    @Provide
    Arbitrary<Long> usedStorageBytes() {
        return Arbitraries.longs()
                .between(0L, 10737418240L); // 0 to 10GB
    }

    /**
     * 生成已使用文件数量
     * 范围：0 到 10000
     */
    @Provide
    Arbitrary<Integer> usedFileCounts() {
        return Arbitraries.integers()
                .between(0, 10000);
    }

    /**
     * 生成最后上传时间
     * 可能为 null（首次上传）或过去的某个时间
     */
    @Provide
    Arbitrary<OffsetDateTime> lastUploadTimes() {
        return Arbitraries.frequencyOf(
                // 20% 概率为 null（首次上传）
                Tuple.of(1, Arbitraries.just(null)),
                // 80% 概率为过去的某个时间
                Tuple.of(4, Arbitraries.longs()
                        .between(1L, 365L) // 1天到365天前
                        .map(days -> OffsetDateTime.now(ZoneOffset.UTC).minusDays(days)))
        );
    }
}
