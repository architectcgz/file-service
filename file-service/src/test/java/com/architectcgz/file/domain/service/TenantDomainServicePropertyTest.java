package com.architectcgz.file.domain.service;

import com.architectcgz.file.common.exception.FileTooLargeException;
import com.architectcgz.file.common.exception.QuotaExceededException;
import com.architectcgz.file.common.exception.TenantNotFoundException;
import com.architectcgz.file.common.exception.TenantSuspendedException;
import com.architectcgz.file.domain.model.Tenant;
import com.architectcgz.file.domain.model.TenantStatus;
import com.architectcgz.file.domain.model.TenantUsage;
import com.architectcgz.file.domain.repository.TenantRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import com.architectcgz.file.infrastructure.config.TenantProperties;
import net.jqwik.api.*;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TenantDomainService 属性测试
 * 
 * Feature: file-service-optimization
 * 使用基于属性的测试验证租户配额检查的正确性属性
 */
class TenantDomainServicePropertyTest {

    /**
     * Feature: file-service-optimization, Property 5: 新租户默认配额初始化
     * 
     * 属性：对于任何新创建的租户，系统应该使用配置的默认值初始化其配额
     * （max_storage_bytes、max_file_count、max_single_file_size）。
     * 
     * 验证需求：3.4
     */
    @Property(tries = 100)
    @Label("Property 5: 新租户默认配额初始化 - 新租户使用默认配额")
    void newTenantDefaultQuotaInitialization(
            @ForAll("tenantIds") String tenantId,
            @ForAll("defaultQuotas") TenantProperties.DefaultQuota defaultQuota
    ) {
        // Given: 租户不存在，自动创建已启用
        TenantRepository mockTenantRepository = mock(TenantRepository.class);
        TenantUsageRepository mockUsageRepository = mock(TenantUsageRepository.class);
        TenantProperties mockProperties = mock(TenantProperties.class);
        
        // 第一次查询返回空（租户不存在），第二次返回保存的租户
        when(mockTenantRepository.findById(tenantId)).thenReturn(Optional.empty());
        when(mockTenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant savedTenant = invocation.getArgument(0);
            // 模拟保存后再次查询会返回该租户
            when(mockTenantRepository.findById(tenantId)).thenReturn(Optional.of(savedTenant));
            return savedTenant;
        });
        when(mockUsageRepository.save(any(TenantUsage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // 配置自动创建和默认配额
        when(mockProperties.isAutoCreate()).thenReturn(true);
        when(mockProperties.getDefaultMaxStorageBytes()).thenReturn(defaultQuota.getMaxStorageBytes());
        when(mockProperties.getDefaultMaxFileCount()).thenReturn(defaultQuota.getMaxFileCount());
        when(mockProperties.getDefaultMaxSingleFileSize()).thenReturn(defaultQuota.getMaxSingleFileSize());
        
        // 创建服务
        TenantDomainService service = new TenantDomainService(
                mockTenantRepository, 
                mockUsageRepository, 
                mockProperties
        );
        
        // When: 获取或创建租户
        Tenant createdTenant = service.getOrCreateTenant(tenantId);
        
        // Then: 验证租户使用默认配额初始化
        assertNotNull(createdTenant, "Created tenant should not be null");
        assertEquals(tenantId, createdTenant.getTenantId(), "Tenant ID should match");
        assertEquals(tenantId, createdTenant.getTenantName(), "Tenant name should default to tenant ID");
        assertEquals(TenantStatus.ACTIVE, createdTenant.getStatus(), "New tenant should be ACTIVE");
        assertEquals(defaultQuota.getMaxStorageBytes(), createdTenant.getMaxStorageBytes(), 
                "Max storage bytes should match default quota");
        assertEquals(defaultQuota.getMaxFileCount(), createdTenant.getMaxFileCount(), 
                "Max file count should match default quota");
        assertEquals(defaultQuota.getMaxSingleFileSize(), createdTenant.getMaxSingleFileSize(), 
                "Max single file size should match default quota");
        assertNotNull(createdTenant.getCreatedAt(), "Created timestamp should be set");
        assertNotNull(createdTenant.getUpdatedAt(), "Updated timestamp should be set");
        
        // 验证租户和使用统计都被保存
        verify(mockTenantRepository, times(1)).save(any(Tenant.class));
        verify(mockUsageRepository, times(1)).save(any(TenantUsage.class));
    }

    /**
     * Feature: file-service-optimization, Property 34: 租户自动创建
     * 
     * 属性：对于任何未知的租户 ID，当启用租户自动创建功能时，
     * 系统应该自动创建该租户并使用默认配额，租户状态应该为 ACTIVE。
     * 
     * 验证需求：14.1
     */
    @Property(tries = 100)
    @Label("Property 34: 租户自动创建 - 启用自动创建时创建不存在的租户")
    void tenantAutoCreationEnabled(
            @ForAll("tenantIds") String tenantId
    ) {
        // Given: 租户不存在，自动创建已启用
        TenantRepository mockTenantRepository = mock(TenantRepository.class);
        TenantUsageRepository mockUsageRepository = mock(TenantUsageRepository.class);
        TenantProperties mockProperties = mock(TenantProperties.class);
        
        // 租户不存在
        when(mockTenantRepository.findById(tenantId)).thenReturn(Optional.empty());
        when(mockTenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mockUsageRepository.save(any(TenantUsage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // 启用自动创建
        when(mockProperties.isAutoCreate()).thenReturn(true);
        when(mockProperties.getDefaultMaxStorageBytes()).thenReturn(10737418240L); // 10GB
        when(mockProperties.getDefaultMaxFileCount()).thenReturn(10000);
        when(mockProperties.getDefaultMaxSingleFileSize()).thenReturn(104857600L); // 100MB
        
        // 创建服务
        TenantDomainService service = new TenantDomainService(
                mockTenantRepository, 
                mockUsageRepository, 
                mockProperties
        );
        
        // When: 获取或创建租户
        Tenant tenant = service.getOrCreateTenant(tenantId);
        
        // Then: 租户应该被自动创建
        assertNotNull(tenant, "Tenant should be auto-created");
        assertEquals(tenantId, tenant.getTenantId(), "Tenant ID should match");
        assertEquals(TenantStatus.ACTIVE, tenant.getStatus(), "Auto-created tenant should be ACTIVE");
        
        // 验证调用了保存方法
        verify(mockTenantRepository, times(1)).save(any(Tenant.class));
        verify(mockUsageRepository, times(1)).save(any(TenantUsage.class));
    }

    /**
     * Feature: file-service-optimization, Property 35: 租户自动创建禁用
     * 
     * 属性：对于任何未知的租户 ID，当禁用租户自动创建功能时，
     * 系统应该抛出 TenantNotFoundException 异常。
     * 
     * 验证需求：14.4
     */
    @Property(tries = 100)
    @Label("Property 35: 租户自动创建禁用 - 禁用自动创建时抛出异常")
    void tenantAutoCreationDisabled(
            @ForAll("tenantIds") String tenantId
    ) {
        // Given: 租户不存在，自动创建已禁用
        TenantRepository mockTenantRepository = mock(TenantRepository.class);
        TenantUsageRepository mockUsageRepository = mock(TenantUsageRepository.class);
        TenantProperties mockProperties = mock(TenantProperties.class);
        
        // 租户不存在
        when(mockTenantRepository.findById(tenantId)).thenReturn(Optional.empty());
        
        // 禁用自动创建
        when(mockProperties.isAutoCreate()).thenReturn(false);
        
        // 创建服务
        TenantDomainService service = new TenantDomainService(
                mockTenantRepository, 
                mockUsageRepository, 
                mockProperties
        );
        
        // When & Then: 应该抛出 TenantNotFoundException
        TenantNotFoundException exception = assertThrows(
                TenantNotFoundException.class,
                () -> service.getOrCreateTenant(tenantId),
                "Should throw TenantNotFoundException when auto-create is disabled"
        );
        
        // 验证异常消息包含租户ID
        assertTrue(exception.getMessage().contains(tenantId),
                "Exception message should contain tenant ID");
        
        // 验证没有调用保存方法
        verify(mockTenantRepository, never()).save(any(Tenant.class));
        verify(mockUsageRepository, never()).save(any(TenantUsage.class));
    }

    /**
     * Feature: file-service-optimization, Property 9: 非活跃租户上传拒绝
     * 
     * 属性：对于任何状态不是 ACTIVE 的租户，当用户尝试上传文件时，
     * 系统应该拒绝上传并抛出 TenantSuspendedException。
     * 
     * 验证需求：5.1
     */
    @Property(tries = 100)
    @Label("Property 9: 非活跃租户上传拒绝 - 非ACTIVE状态租户无法上传文件")
    void inactiveTenantUploadRejection(
            @ForAll("tenants") Tenant tenant,
            @ForAll("fileSizes") long fileSize
    ) {
        // Given: 设置租户为非活跃状态（SUSPENDED 或 DELETED）
        TenantStatus inactiveStatus = Arbitraries.of(TenantStatus.SUSPENDED, TenantStatus.DELETED)
                .sample();
        tenant.setStatus(inactiveStatus);
        
        // 创建 mock 依赖
        TenantRepository mockTenantRepository = mock(TenantRepository.class);
        TenantUsageRepository mockUsageRepository = mock(TenantUsageRepository.class);
        TenantProperties mockProperties = mock(TenantProperties.class);
        
        when(mockTenantRepository.findById(tenant.getTenantId())).thenReturn(Optional.of(tenant));
        
        // 创建服务
        TenantDomainService service = new TenantDomainService(
                mockTenantRepository, 
                mockUsageRepository, 
                mockProperties
        );
        
        // When & Then: 非活跃租户上传应该抛出 TenantSuspendedException
        TenantSuspendedException exception = assertThrows(
                TenantSuspendedException.class,
                () -> service.checkQuota(tenant.getTenantId(), fileSize),
                "Inactive tenant should not be able to upload files"
        );
        
        // 验证异常消息包含租户ID
        assertTrue(exception.getMessage().contains(tenant.getTenantId()),
                "Exception message should contain tenant ID");
        
        // 验证没有查询使用统计（因为在状态检查时就失败了）
        verify(mockUsageRepository, never()).findById(any());
    }

    /**
     * Feature: file-service-optimization, Property 10: 存储空间配额检查
     * 
     * 属性：对于任何活跃租户的文件上传操作，当当前使用量加上新文件大小超过最大存储空间时，
     * 系统应该拒绝上传并抛出 QuotaExceededException。
     * 
     * 验证需求：5.3
     */
    @Property(tries = 100)
    @Label("Property 10: 存储空间配额检查 - 超出存储配额时拒绝上传")
    void storageQuotaCheck(
            @ForAll("tenants") Tenant tenant,
            @ForAll("tenantUsages") TenantUsage usage,
            @ForAll("fileSizes") long fileSize
    ) {
        // Given: 设置租户为活跃状态
        tenant.setStatus(TenantStatus.ACTIVE);
        usage.setTenantId(tenant.getTenantId());
        
        // 确保当前使用量 + 文件大小会超过配额
        // 设置使用量为接近配额上限
        long maxStorage = tenant.getMaxStorageBytes();
        long currentUsage = maxStorage - fileSize + 1; // 确保 currentUsage + fileSize > maxStorage
        usage.setUsedStorageBytes(currentUsage);
        
        // 确保文件大小不超过单文件限制（避免触发其他检查）
        Assume.that(fileSize <= tenant.getMaxSingleFileSize());
        
        // 确保文件数量不超过限制（避免触发其他检查）
        usage.setUsedFileCount(tenant.getMaxFileCount() - 10);
        
        // 创建 mock 依赖
        TenantRepository mockTenantRepository = mock(TenantRepository.class);
        TenantUsageRepository mockUsageRepository = mock(TenantUsageRepository.class);
        TenantProperties mockProperties = mock(TenantProperties.class);
        
        when(mockTenantRepository.findById(tenant.getTenantId())).thenReturn(Optional.of(tenant));
        when(mockUsageRepository.findById(tenant.getTenantId())).thenReturn(Optional.of(usage));
        
        // 创建服务
        TenantDomainService service = new TenantDomainService(
                mockTenantRepository, 
                mockUsageRepository, 
                mockProperties
        );
        
        // When & Then: 超出存储配额应该抛出 QuotaExceededException
        QuotaExceededException exception = assertThrows(
                QuotaExceededException.class,
                () -> service.checkQuota(tenant.getTenantId(), fileSize),
                "Upload should be rejected when storage quota is exceeded"
        );
        
        // 验证异常消息包含配额信息
        assertTrue(exception.getMessage().contains("Storage"),
                "Exception message should indicate storage quota exceeded");
        assertTrue(exception.getMessage().contains(String.valueOf(currentUsage + fileSize)),
                "Exception message should contain current usage");
        assertTrue(exception.getMessage().contains(String.valueOf(maxStorage)),
                "Exception message should contain storage limit");
    }

    /**
     * Feature: file-service-optimization, Property 11: 文件数量配额检查
     * 
     * 属性：对于任何活跃租户的文件上传操作，当当前文件数量已达到最大文件数量时，
     * 系统应该拒绝上传并抛出 QuotaExceededException。
     * 
     * 验证需求：5.5
     */
    @Property(tries = 100)
    @Label("Property 11: 文件数量配额检查 - 达到文件数量上限时拒绝上传")
    void fileCountQuotaCheck(
            @ForAll("tenants") Tenant tenant,
            @ForAll("tenantUsages") TenantUsage usage,
            @ForAll("fileSizes") long fileSize
    ) {
        // Given: 设置租户为活跃状态
        tenant.setStatus(TenantStatus.ACTIVE);
        usage.setTenantId(tenant.getTenantId());
        
        // 设置文件数量已达到上限
        int maxFileCount = tenant.getMaxFileCount();
        usage.setUsedFileCount(maxFileCount);
        
        // 确保存储空间不超过限制（避免触发其他检查）
        long safeStorageUsage = tenant.getMaxStorageBytes() / 2;
        usage.setUsedStorageBytes(safeStorageUsage);
        
        // 确保文件大小不超过单文件限制（避免触发其他检查）
        Assume.that(fileSize <= tenant.getMaxSingleFileSize());
        
        // 确保新文件不会导致存储空间超限（避免触发其他检查）
        Assume.that(safeStorageUsage + fileSize <= tenant.getMaxStorageBytes());
        
        // 创建 mock 依赖
        TenantRepository mockTenantRepository = mock(TenantRepository.class);
        TenantUsageRepository mockUsageRepository = mock(TenantUsageRepository.class);
        TenantProperties mockProperties = mock(TenantProperties.class);
        
        when(mockTenantRepository.findById(tenant.getTenantId())).thenReturn(Optional.of(tenant));
        when(mockUsageRepository.findById(tenant.getTenantId())).thenReturn(Optional.of(usage));
        
        // 创建服务
        TenantDomainService service = new TenantDomainService(
                mockTenantRepository, 
                mockUsageRepository, 
                mockProperties
        );
        
        // When & Then: 达到文件数量上限应该抛出 QuotaExceededException
        QuotaExceededException exception = assertThrows(
                QuotaExceededException.class,
                () -> service.checkQuota(tenant.getTenantId(), fileSize),
                "Upload should be rejected when file count limit is reached"
        );
        
        // 验证异常消息包含文件数量信息
        assertTrue(exception.getMessage().contains("File count"),
                "Exception message should indicate file count quota exceeded");
        assertTrue(exception.getMessage().contains(String.valueOf(maxFileCount + 1)),
                "Exception message should contain new file count");
        assertTrue(exception.getMessage().contains(String.valueOf(maxFileCount)),
                "Exception message should contain file count limit");
    }

    /**
     * Feature: file-service-optimization, Property 12: 单文件大小限制检查
     * 
     * 属性：对于任何活跃租户的文件上传操作，当文件大小超过单文件大小限制时，
     * 系统应该拒绝上传并抛出 FileTooLargeException。
     * 
     * 验证需求：5.7
     */
    @Property(tries = 100)
    @Label("Property 12: 单文件大小限制检查 - 文件超过单文件大小限制时拒绝上传")
    void singleFileSizeLimitCheck(
            @ForAll("tenants") Tenant tenant,
            @ForAll("tenantUsages") TenantUsage usage
    ) {
        // Given: 设置租户为活跃状态
        tenant.setStatus(TenantStatus.ACTIVE);
        usage.setTenantId(tenant.getTenantId());
        
        // 生成一个超过单文件大小限制的文件
        long maxSingleFileSize = tenant.getMaxSingleFileSize();
        long oversizedFile = maxSingleFileSize + Arbitraries.longs().between(1L, 1000000L).sample();
        
        // 确保存储空间足够（避免触发其他检查）
        usage.setUsedStorageBytes(0L);
        
        // 确保文件数量未达到上限（避免触发其他检查）
        usage.setUsedFileCount(0);
        
        // 创建 mock 依赖
        TenantRepository mockTenantRepository = mock(TenantRepository.class);
        TenantUsageRepository mockUsageRepository = mock(TenantUsageRepository.class);
        TenantProperties mockProperties = mock(TenantProperties.class);
        
        when(mockTenantRepository.findById(tenant.getTenantId())).thenReturn(Optional.of(tenant));
        when(mockUsageRepository.findById(tenant.getTenantId())).thenReturn(Optional.of(usage));
        
        // 创建服务
        TenantDomainService service = new TenantDomainService(
                mockTenantRepository, 
                mockUsageRepository, 
                mockProperties
        );
        
        // When & Then: 文件过大应该抛出 FileTooLargeException
        FileTooLargeException exception = assertThrows(
                FileTooLargeException.class,
                () -> service.checkQuota(tenant.getTenantId(), oversizedFile),
                "Upload should be rejected when file size exceeds single file size limit"
        );
        
        // 验证异常消息包含文件大小信息
        assertTrue(exception.getMessage().contains(String.valueOf(oversizedFile)),
                "Exception message should contain actual file size");
        assertTrue(exception.getMessage().contains(String.valueOf(maxSingleFileSize)),
                "Exception message should contain maximum allowed file size");
    }

    // ========== Arbitraries (数据生成器) ==========

    /**
     * 生成租户对象
     */
    @Provide
    Arbitrary<Tenant> tenants() {
        return Combinators.combine(
                tenantIds(),
                tenantNames(),
                maxStorageBytes(),
                maxFileCounts(),
                maxSingleFileSizes()
        ).as((tenantId, tenantName, maxStorage, maxFileCount, maxSingleFileSize) -> {
            Tenant tenant = new Tenant();
            tenant.setTenantId(tenantId);
            tenant.setTenantName(tenantName);
            tenant.setStatus(TenantStatus.ACTIVE); // 默认活跃，测试中会修改
            tenant.setMaxStorageBytes(maxStorage);
            tenant.setMaxFileCount(maxFileCount);
            tenant.setMaxSingleFileSize(maxSingleFileSize);
            tenant.setCreatedAt(LocalDateTime.now());
            tenant.setUpdatedAt(LocalDateTime.now());
            return tenant;
        });
    }

    /**
     * 生成租户使用统计对象
     */
    @Provide
    Arbitrary<TenantUsage> tenantUsages() {
        return Combinators.combine(
                tenantIds(),
                usedStorageBytes(),
                usedFileCounts()
        ).as((tenantId, usedStorage, usedFileCount) -> {
            TenantUsage usage = new TenantUsage(tenantId);
            usage.setUsedStorageBytes(usedStorage);
            usage.setUsedFileCount(usedFileCount);
            usage.setLastUploadAt(LocalDateTime.now().minusDays(1));
            usage.setUpdatedAt(LocalDateTime.now());
            return usage;
        });
    }

    /**
     * 生成租户ID
     */
    @Provide
    Arbitrary<String> tenantIds() {
        return Arbitraries.of("blog", "im", "forum", "shop", "cms", "admin", "test");
    }

    /**
     * 生成租户名称
     */
    @Provide
    Arbitrary<String> tenantNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(s -> "Tenant-" + s);
    }

    /**
     * 生成最大存储空间（字节）
     * 范围：1GB 到 100GB
     */
    @Provide
    Arbitrary<Long> maxStorageBytes() {
        return Arbitraries.longs()
                .between(1073741824L, 107374182400L); // 1GB to 100GB
    }

    /**
     * 生成最大文件数量
     * 范围：100 到 100000
     */
    @Provide
    Arbitrary<Integer> maxFileCounts() {
        return Arbitraries.integers()
                .between(100, 100000);
    }

    /**
     * 生成单文件大小限制（字节）
     * 范围：1MB 到 1GB
     */
    @Provide
    Arbitrary<Long> maxSingleFileSizes() {
        return Arbitraries.longs()
                .between(1048576L, 1073741824L); // 1MB to 1GB
    }

    /**
     * 生成已使用存储空间（字节）
     * 范围：0 到 50GB
     */
    @Provide
    Arbitrary<Long> usedStorageBytes() {
        return Arbitraries.longs()
                .between(0L, 53687091200L); // 0 to 50GB
    }

    /**
     * 生成已使用文件数量
     * 范围：0 到 50000
     */
    @Provide
    Arbitrary<Integer> usedFileCounts() {
        return Arbitraries.integers()
                .between(0, 50000);
    }

    /**
     * 生成文件大小（字节）
     * 范围：1 byte 到 500MB
     */
    @Provide
    Arbitrary<Long> fileSizes() {
        return Arbitraries.longs()
                .between(1L, 524288000L); // 1 byte to 500MB
    }

    /**
     * 生成默认配额配置
     */
    @Provide
    Arbitrary<TenantProperties.DefaultQuota> defaultQuotas() {
        return Combinators.combine(
                maxStorageBytes(),
                maxFileCounts(),
                maxSingleFileSizes()
        ).as((maxStorage, maxFileCount, maxSingleFileSize) -> {
            TenantProperties.DefaultQuota quota = new TenantProperties.DefaultQuota();
            quota.setMaxStorageBytes(maxStorage);
            quota.setMaxFileCount(maxFileCount);
            quota.setMaxSingleFileSize(maxSingleFileSize);
            return quota;
        });
    }
}
