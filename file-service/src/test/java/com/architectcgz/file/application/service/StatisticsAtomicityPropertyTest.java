package com.architectcgz.file.application.service;

import com.architectcgz.file.domain.model.TenantUsage;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import com.architectcgz.file.infrastructure.repository.TenantUsageRepositoryImpl;
import com.architectcgz.file.infrastructure.repository.mapper.TenantUsageMapper;
import com.architectcgz.file.infrastructure.repository.po.TenantUsagePO;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统计更新原子性属性测试
 * 
 * Feature: file-service-optimization
 * 使用基于属性的测试验证并发场景下租户统计更新的原子性
 */
@SpringBootTest
@ActiveProfiles("test")
class StatisticsAtomicityPropertyTest {

    @Autowired
    private TenantUsageRepository tenantUsageRepository;

    @Autowired
    private TenantUsageMapper tenantUsageMapper;

    /**
     * Feature: file-service-optimization, Property 32: 统计更新原子性
     * 
     * 属性：对于任何并发的文件上传和删除操作，租户使用统计的更新应该是原子性的，
     * 最终统计结果应该与操作序列一致。
     * 
     * 验证需求：12.6
     */
    @Property(tries = 100)
    @Label("Property 32: 统计更新原子性 - 并发操作下统计更新保持一致性")
    void statisticsUpdateAtomicity(
            @ForAll("tenantIds") String tenantId,
            @ForAll("operationSequences") List<Operation> operations
    ) throws Exception {
        // Given: 初始化租户使用统计
        initializeTenantUsage(tenantId);
        
        // 计算预期的最终状态
        long expectedStorageBytes = 0;
        int expectedFileCount = 0;
        
        for (Operation op : operations) {
            if (op.isUpload()) {
                expectedStorageBytes += op.getFileSize();
                expectedFileCount += 1;
            } else {
                expectedStorageBytes = Math.max(0, expectedStorageBytes - op.getFileSize());
                expectedFileCount = Math.max(0, expectedFileCount - 1);
            }
        }
        
        // When: 并发执行所有操作
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<?>> futures = new ArrayList<>();
        
        for (Operation op : operations) {
            Future<?> future = executor.submit(() -> {
                if (op.isUpload()) {
                    tenantUsageRepository.incrementUsage(tenantId, op.getFileSize());
                } else {
                    tenantUsageRepository.decrementUsage(tenantId, op.getFileSize());
                }
            });
            futures.add(future);
        }
        
        // 等待所有操作完成
        for (Future<?> future : futures) {
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                fail("Operation timed out");
            }
        }
        
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        // Then: 验证最终统计结果与预期一致
        TenantUsage finalUsage = tenantUsageRepository.findById(tenantId)
                .orElseThrow(() -> new AssertionError("Tenant usage not found"));
        
        assertEquals(
                expectedStorageBytes,
                finalUsage.getUsedStorageBytes(),
                String.format(
                        "Storage bytes should be %d after %d operations, but was %d",
                        expectedStorageBytes,
                        operations.size(),
                        finalUsage.getUsedStorageBytes()
                )
        );
        
        assertEquals(
                expectedFileCount,
                finalUsage.getUsedFileCount(),
                String.format(
                        "File count should be %d after %d operations, but was %d",
                        expectedFileCount,
                        operations.size(),
                        finalUsage.getUsedFileCount()
                )
        );
        
        // 清理测试数据
        cleanupTenantUsage(tenantId);
    }

    /**
     * 初始化租户使用统计
     */
    private void initializeTenantUsage(String tenantId) {
        TenantUsagePO po = new TenantUsagePO();
        po.setTenantId(tenantId);
        po.setUsedStorageBytes(0L);
        po.setUsedFileCount(0);
        po.setLastUploadAt(null);
        po.setUpdatedAt(LocalDateTime.now());
        
        // 删除已存在的记录
        TenantUsagePO existing = tenantUsageMapper.findById(tenantId);
        if (existing != null) {
            tenantUsageMapper.update(po);
        } else {
            tenantUsageMapper.insert(po);
        }
    }

    /**
     * 清理租户使用统计
     */
    private void cleanupTenantUsage(String tenantId) {
        // 重置为初始状态
        TenantUsagePO po = new TenantUsagePO();
        po.setTenantId(tenantId);
        po.setUsedStorageBytes(0L);
        po.setUsedFileCount(0);
        po.setLastUploadAt(null);
        po.setUpdatedAt(LocalDateTime.now());
        tenantUsageMapper.update(po);
    }

    // ========== Arbitraries (数据生成器) ==========

    /**
     * 生成租户ID
     */
    @Provide
    Arbitrary<String> tenantIds() {
        return Arbitraries.of("test-tenant-1", "test-tenant-2", "test-tenant-3");
    }

    /**
     * 生成操作序列
     * 包含上传和删除操作的混合序列
     */
    @Provide
    Arbitrary<List<Operation>> operationSequences() {
        return Arbitraries.integers()
                .between(5, 20) // 5到20个操作
                .flatMap(count -> {
                    Arbitrary<Operation> operationArbitrary = Combinators.combine(
                            Arbitraries.of(OperationType.UPLOAD, OperationType.DELETE),
                            Arbitraries.longs().between(1024L, 10485760L) // 1KB to 10MB
                    ).as(Operation::new);
                    
                    return operationArbitrary.list().ofSize(count);
                });
    }

    // ========== 辅助类 ==========

    /**
     * 操作类型枚举
     */
    enum OperationType {
        UPLOAD,
        DELETE
    }

    /**
     * 操作对象
     */
    static class Operation {
        private final OperationType type;
        private final long fileSize;

        public Operation(OperationType type, long fileSize) {
            this.type = type;
            this.fileSize = fileSize;
        }

        public boolean isUpload() {
            return type == OperationType.UPLOAD;
        }

        public long getFileSize() {
            return fileSize;
        }

        @Override
        public String toString() {
            return String.format("%s(%d bytes)", type, fileSize);
        }
    }
}
