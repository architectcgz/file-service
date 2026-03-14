package com.architectcgz.file.application.service;

import com.architectcgz.file.domain.model.TenantUsage;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 统计更新原子性属性测试
 *
 * 使用随机多轮并发操作验证租户统计更新的原子性。
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Sql(scripts = "/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class StatisticsAtomicityPropertyTest {

    private static final String[] TENANT_IDS = {
            "test-tenant-1",
            "test-tenant-2",
            "test-tenant-3"
    };

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("file_service_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        postgres.start();
        redis.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.redis.redisson.config", () ->
                "singleServerConfig:\n" +
                "  address: \"redis://" + redis.getHost() + ":" + redis.getFirstMappedPort() + "\"\n" +
                "  password: null\n"
        );
    }

    @Autowired
    private TenantUsageRepository tenantUsageRepository;

    private final Random random = new Random();

    /**
     * Feature: file-service-optimization, Property 32: 统计更新原子性
     *
     * 随机生成多组并发上传/删除操作，验证最终统计结果保持一致。
     */
    @Test
    @DisplayName("Property 32: 统计更新原子性 - 并发操作下统计更新保持一致性")
    void statisticsUpdateAtomicity() throws Exception {
        for (int iteration = 1; iteration <= 30; iteration++) {
            String tenantId = randomTenantId();
            List<Operation> operations = randomOperationSequence();

            // 预先填充所有删除操作对应的基线数据，避免并发扣减触发下限裁剪后导致结果依赖执行时序。
            initializeTenantUsage(tenantId, operations);

            long expectedStorageBytes = operations.stream()
                    .filter(Operation::isUpload)
                    .mapToLong(Operation::getFileSize)
                    .sum();
            int expectedFileCount = (int) operations.stream()
                    .filter(Operation::isUpload)
                    .count();

            ExecutorService executor = Executors.newFixedThreadPool(10);
            List<Future<?>> futures = new ArrayList<>();

            for (Operation op : operations) {
                futures.add(executor.submit(() -> {
                    if (op.isUpload()) {
                        tenantUsageRepository.incrementUsage(tenantId, op.getFileSize());
                    } else {
                        tenantUsageRepository.decrementUsage(tenantId, op.getFileSize());
                    }
                }));
            }

            for (Future<?> future : futures) {
                try {
                    future.get(5, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    fail("Operation timed out");
                }
            }

            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            TenantUsage finalUsage = tenantUsageRepository.findById(tenantId)
                    .orElseThrow(() -> new AssertionError("Tenant usage not found"));

            assertEquals(
                    expectedStorageBytes,
                    finalUsage.getUsedStorageBytes(),
                    String.format(
                            "[Iteration %d] Storage bytes should be %d after %d operations, but was %d",
                            iteration,
                            expectedStorageBytes,
                            operations.size(),
                            finalUsage.getUsedStorageBytes()
                    )
            );

            assertEquals(
                    expectedFileCount,
                    finalUsage.getUsedFileCount(),
                    String.format(
                            "[Iteration %d] File count should be %d after %d operations, but was %d",
                            iteration,
                            expectedFileCount,
                            operations.size(),
                            finalUsage.getUsedFileCount()
                    )
            );

            cleanupTenantUsage(tenantId);
        }
    }

    private void initializeTenantUsage(String tenantId, List<Operation> operations) {
        long baselineStorageBytes = operations.stream()
                .filter(op -> !op.isUpload())
                .mapToLong(Operation::getFileSize)
                .sum();
        int baselineFileCount = (int) operations.stream()
                .filter(op -> !op.isUpload())
                .count();
        tenantUsageRepository.save(createTenantUsage(tenantId, baselineStorageBytes, baselineFileCount));
    }

    private void cleanupTenantUsage(String tenantId) {
        tenantUsageRepository.save(createTenantUsage(tenantId, 0L, 0));
    }

    private TenantUsage createTenantUsage(String tenantId, long usedStorageBytes, int usedFileCount) {
        TenantUsage usage = new TenantUsage();
        usage.setTenantId(tenantId);
        usage.setUsedStorageBytes(usedStorageBytes);
        usage.setUsedFileCount(usedFileCount);
        usage.setLastUploadAt(null);
        usage.setUpdatedAt(LocalDateTime.now());
        return usage;
    }

    private String randomTenantId() {
        return TENANT_IDS[random.nextInt(TENANT_IDS.length)];
    }

    private List<Operation> randomOperationSequence() {
        int count = random.nextInt(16) + 5;
        List<Operation> operations = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            OperationType type = random.nextBoolean() ? OperationType.UPLOAD : OperationType.DELETE;
            long fileSize = 1024L + random.nextLong(10 * 1024 * 1024L - 1024L + 1);
            operations.add(new Operation(type, fileSize));
        }
        return operations;
    }

    enum OperationType {
        UPLOAD,
        DELETE
    }

    static class Operation {
        private final OperationType type;
        private final long fileSize;

        Operation(OperationType type, long fileSize) {
            this.type = type;
            this.fileSize = fileSize;
        }

        boolean isUpload() {
            return type == OperationType.UPLOAD;
        }

        long getFileSize() {
            return fileSize;
        }

        @Override
        public String toString() {
            return String.format("%s(%d bytes)", type, fileSize);
        }
    }
}
