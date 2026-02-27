package com.architectcgz.file.performance;

import com.architectcgz.file.common.config.BitmapProperties;
import com.architectcgz.file.domain.model.UploadPart;
import com.architectcgz.file.domain.repository.UploadPartRepository;
import com.architectcgz.file.infrastructure.repository.mapper.UploadPartMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
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
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bitmap 压力测试
 * 
 * 测试目标:
 * 1. 测试 100 个并发上传任务
 * 2. 验证响应时间 < 100ms
 * 3. 验证系统稳定性
 * 
 * Requirements: 9.5
 */
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2",
    "spring.profiles.active=test"
})
@Testcontainers
@ActiveProfiles("test")
@Sql(scripts = "/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Slf4j
@DisplayName("Bitmap Stress Test")
public class BitmapStressTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("file_service_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    private UploadPartRepository repository;

    @Autowired
    private UploadPartMapper mapper;

    @Autowired
    private BitmapProperties bitmapProperties;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private com.architectcgz.file.infrastructure.repository.mapper.UploadTaskMapper taskMapper;

    private static final int CONCURRENT_TASKS = 100;
    private static final int PARTS_PER_TASK = 10;
    private static final int MAX_RESPONSE_TIME_MS = 100;

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        log.info("=".repeat(80));
        log.info("Setting up stress test");
        log.info("  Concurrent Tasks: {}", CONCURRENT_TASKS);
        log.info("  Parts Per Task: {}", PARTS_PER_TASK);
        log.info("  Max Response Time: {} ms", MAX_RESPONSE_TIME_MS);
        log.info("=".repeat(80));
        
        // Enable Bitmap mode
        bitmapProperties.setEnabled(true);
        
        // Create thread pool for concurrent uploads
        executorService = Executors.newFixedThreadPool(CONCURRENT_TASKS);
    }

    @AfterEach
    void tearDown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("Stress test teardown completed");
    }

    /**
     * Test 13.3: 执行压力测试
     * 
     * 测试步骤:
     * 1. 创建 100 个并发上传任务
     * 2. 每个任务上传 10 个分片
     * 3. 记录每次操作的响应时间
     * 4. 验证所有响应时间 < 100ms
     * 5. 验证系统稳定性（无异常、无数据丢失）
     * 
     * Requirements: 9.5
     */
    @Test
    @DisplayName("Test 100 concurrent upload tasks with response time < 100ms")
    void testConcurrentUploadStress() throws InterruptedException, ExecutionException {
        log.info("\n" + "=".repeat(80));
        log.info("Starting Concurrent Upload Stress Test");
        log.info("=".repeat(80));

        // Metrics collection
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);
        AtomicLong maxResponseTime = new AtomicLong(0);
        AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
        ConcurrentLinkedQueue<Long> responseTimes = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();

        // Create upload tasks and persist them to database
        List<String> taskIds = new ArrayList<>();
        log.info("\nPhase 0: Creating Upload Tasks");
        log.info("-".repeat(80));
        
        for (int i = 0; i < CONCURRENT_TASKS; i++) {
            String taskId = "stress-test-" + UUID.randomUUID();
            taskIds.add(taskId);
            
            // 创建上传任务记录
            com.architectcgz.file.infrastructure.repository.po.UploadTaskPO taskPO = 
                new com.architectcgz.file.infrastructure.repository.po.UploadTaskPO();
            taskPO.setId(taskId);
            taskPO.setAppId("test-app");
            taskPO.setUserId("test-user");
            taskPO.setFileName("stress-test-file-" + i + ".dat");
            taskPO.setFileSize(50L * 1024 * 1024); // 50MB
            taskPO.setFileHash("hash-" + i);
            taskPO.setStoragePath("/test/path");
            taskPO.setUploadId("upload-" + UUID.randomUUID());
            taskPO.setTotalParts(PARTS_PER_TASK);
            taskPO.setChunkSize(5 * 1024 * 1024); // 5MB
            taskPO.setStatus("uploading");
            taskPO.setCreatedAt(LocalDateTime.now());
            taskPO.setUpdatedAt(LocalDateTime.now());
            taskPO.setExpiresAt(LocalDateTime.now().plusDays(1));
            
            taskMapper.insert(taskPO);
        }
        
        log.info("Created {} upload tasks", CONCURRENT_TASKS);

        log.info("\nPhase 1: Concurrent Upload Execution");
        log.info("-".repeat(80));

        // Submit all upload tasks using CountDownLatch for synchronization
        CountDownLatch latch = new CountDownLatch(CONCURRENT_TASKS);
        long testStartTime = System.currentTimeMillis();

        for (String taskId : taskIds) {
            executorService.submit(() -> {
                try {
                    // Upload parts for this task
                    for (int partNumber = 1; partNumber <= PARTS_PER_TASK; partNumber++) {
                        long startTime = System.nanoTime();
                        
                        try {
                            UploadPart part = UploadPart.builder()
                                    .id(UUID.randomUUID().toString())
                                    .taskId(taskId)
                                    .partNumber(partNumber)
                                    .etag("etag-" + partNumber)
                                    .size(5L * 1024 * 1024) // 5MB
                                    .uploadedAt(LocalDateTime.now())
                                    .build();
                            
                            repository.savePart(part);
                            
                            long endTime = System.nanoTime();
                            long responseTimeMs = (endTime - startTime) / 1_000_000;
                            
                            // Record metrics
                            responseTimes.add(responseTimeMs);
                            totalResponseTime.addAndGet(responseTimeMs);
                            successCount.incrementAndGet();
                            
                            // Update max/min response time
                            updateMax(maxResponseTime, responseTimeMs);
                            updateMin(minResponseTime, responseTimeMs);
                            
                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                            errors.add(String.format("Task %s Part %d: %s", 
                                    taskId, partNumber, e.getMessage()));
                            log.error("Upload failed: taskId={}, partNumber={}", 
                                    taskId, partNumber, e);
                        }
                    }
                } catch (Exception e) {
                    log.error("Task execution failed: taskId={}", taskId, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all tasks to complete
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        if (!completed) {
            log.error("Stress test timed out after 60 seconds");
        }

        long testEndTime = System.currentTimeMillis();
        long totalTestTime = testEndTime - testStartTime;

        log.info("All concurrent uploads completed in {} ms", totalTestTime);

        // Phase 2: Analyze Results
        log.info("\n" + "=".repeat(80));
        log.info("Phase 2: Performance Analysis");
        log.info("=".repeat(80));

        int totalOperations = successCount.get() + failureCount.get();
        double avgResponseTime = totalOperations > 0 ? 
                (double) totalResponseTime.get() / totalOperations : 0;
        double successRate = totalOperations > 0 ? 
                (double) successCount.get() / totalOperations * 100 : 0;
        double throughput = totalOperations * 1000.0 / totalTestTime;

        log.info("\nExecution Summary:");
        log.info("  Total Operations: {}", totalOperations);
        log.info("  Successful: {} ({:.2f}%)", successCount.get(), successRate);
        log.info("  Failed: {} ({:.2f}%)", failureCount.get(), 100 - successRate);
        log.info("  Total Time: {} ms", totalTestTime);
        log.info("  Throughput: {:.2f} operations/sec", throughput);

        log.info("\nResponse Time Statistics:");
        log.info("  Average: {:.2f} ms", avgResponseTime);
        log.info("  Min: {} ms", minResponseTime.get() == Long.MAX_VALUE ? 0 : minResponseTime.get());
        log.info("  Max: {} ms", maxResponseTime.get());

        // Calculate percentiles
        List<Long> sortedTimes = new ArrayList<>(responseTimes);
        sortedTimes.sort(Long::compareTo);
        
        if (!sortedTimes.isEmpty()) {
            long p50 = getPercentile(sortedTimes, 50);
            long p95 = getPercentile(sortedTimes, 95);
            long p99 = getPercentile(sortedTimes, 99);
            
            log.info("  P50 (Median): {} ms", p50);
            log.info("  P95: {} ms", p95);
            log.info("  P99: {} ms", p99);
        }

        // Response time distribution
        long under50ms = sortedTimes.stream().filter(t -> t < 50).count();
        long under100ms = sortedTimes.stream().filter(t -> t < 100).count();
        long under200ms = sortedTimes.stream().filter(t -> t < 200).count();
        long over200ms = sortedTimes.stream().filter(t -> t >= 200).count();

        log.info("\nResponse Time Distribution:");
        log.info("  < 50ms:  {} ({:.1f}%)", under50ms, 100.0 * under50ms / sortedTimes.size());
        log.info("  < 100ms: {} ({:.1f}%)", under100ms, 100.0 * under100ms / sortedTimes.size());
        log.info("  < 200ms: {} ({:.1f}%)", under200ms, 100.0 * under200ms / sortedTimes.size());
        log.info("  >= 200ms: {} ({:.1f}%)", over200ms, 100.0 * over200ms / sortedTimes.size());

        // Phase 3: Data Integrity Verification
        log.info("\n" + "=".repeat(80));
        log.info("Phase 3: Data Integrity Verification");
        log.info("=".repeat(80));

        // Wait for async sync to complete
        Thread.sleep(3000);

        int verifiedTasks = 0;
        int dataIntegrityErrors = 0;

        for (String taskId : taskIds) {
            try {
                // Verify Bitmap
                int bitmapCount = repository.countCompletedParts(taskId);
                
                // Verify data consistency
                if (bitmapCount == PARTS_PER_TASK) {
                    verifiedTasks++;
                } else {
                    dataIntegrityErrors++;
                    log.warn("Data integrity issue: taskId={}, expected={}, actual={}", 
                            taskId, PARTS_PER_TASK, bitmapCount);
                }
            } catch (Exception e) {
                dataIntegrityErrors++;
                log.error("Verification failed: taskId={}", taskId, e);
            }
        }

        log.info("\nData Integrity Results:");
        log.info("  Verified Tasks: {}/{}", verifiedTasks, CONCURRENT_TASKS);
        log.info("  Integrity Errors: {}", dataIntegrityErrors);

        // Phase 4: Error Analysis
        if (!errors.isEmpty()) {
            log.info("\n" + "=".repeat(80));
            log.info("Phase 4: Error Analysis");
            log.info("=".repeat(80));
            log.info("Total Errors: {}", errors.size());
            
            // Show first 10 errors
            int errorCount = 0;
            for (String error : errors) {
                if (errorCount++ >= 10) {
                    log.info("... and {} more errors", errors.size() - 10);
                    break;
                }
                log.info("  {}", error);
            }
        }

        // Phase 5: Cleanup
        log.info("\n" + "=".repeat(80));
        log.info("Phase 5: Cleanup");
        log.info("=".repeat(80));

        for (String taskId : taskIds) {
            cleanup(taskId);
        }
        log.info("Cleanup completed");

        // Phase 6: Assertions
        log.info("\n" + "=".repeat(80));
        log.info("Phase 6: Requirement Verification");
        log.info("=".repeat(80));

        // Requirement 9.5: Response time < 100ms
        log.info("\nRequirement 9.5: Response time < 100ms");
        log.info("  Average Response Time: {:.2f} ms ({})", 
                avgResponseTime, avgResponseTime < MAX_RESPONSE_TIME_MS ? "PASS" : "FAIL");
        log.info("  Max Response Time: {} ms ({})", 
                maxResponseTime.get(), maxResponseTime.get() < MAX_RESPONSE_TIME_MS ? "PASS" : "FAIL");
        log.info("  P95 Response Time: {} ms ({})", 
                getPercentile(sortedTimes, 95), 
                getPercentile(sortedTimes, 95) < MAX_RESPONSE_TIME_MS ? "PASS" : "FAIL");

        // System Stability
        log.info("\nSystem Stability:");
        log.info("  Success Rate: {:.2f}% ({})", 
                successRate, successRate >= 99.0 ? "PASS" : "FAIL");
        log.info("  Data Integrity: {}/{} tasks ({})", 
                verifiedTasks, CONCURRENT_TASKS, 
                verifiedTasks == CONCURRENT_TASKS ? "PASS" : "FAIL");

        log.info("\n" + "=".repeat(80));
        
        // Assertions
        assertThat(avgResponseTime)
                .as("Average response time should be less than 100ms")
                .isLessThan(MAX_RESPONSE_TIME_MS);
        
        assertThat(maxResponseTime.get())
                .as("Max response time should be less than 100ms")
                .isLessThan(MAX_RESPONSE_TIME_MS);
        
        assertThat(successRate)
                .as("Success rate should be at least 99%")
                .isGreaterThanOrEqualTo(99.0);
        
        assertThat(verifiedTasks)
                .as("All tasks should have correct data")
                .isEqualTo(CONCURRENT_TASKS);
        
        assertThat(dataIntegrityErrors)
                .as("There should be no data integrity errors")
                .isEqualTo(0);

        log.info("All Stress Test Requirements PASSED!");
        log.info("=".repeat(80));
    }

    /**
     * Update atomic max value
     */
    private void updateMax(AtomicLong atomic, long value) {
        long current;
        do {
            current = atomic.get();
            if (value <= current) {
                return;
            }
        } while (!atomic.compareAndSet(current, value));
    }

    /**
     * Update atomic min value
     */
    private void updateMin(AtomicLong atomic, long value) {
        long current;
        do {
            current = atomic.get();
            if (value >= current) {
                return;
            }
        } while (!atomic.compareAndSet(current, value));
    }

    /**
     * Calculate percentile
     */
    private long getPercentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }

    /**
     * Cleanup test data
     */
    private void cleanup(String taskId) {
        try {
            // Delete parts from database
            mapper.deleteByTaskId(taskId);
            
            // Delete task from database
            taskMapper.updateStatus(taskId, "deleted", LocalDateTime.now());
            
            // Delete from Redis
            String bitmapKey = "upload:task:" + taskId + ":parts";
            redisTemplate.delete(bitmapKey);
        } catch (Exception e) {
            log.warn("Cleanup failed for taskId={}", taskId, e);
        }
    }
}
