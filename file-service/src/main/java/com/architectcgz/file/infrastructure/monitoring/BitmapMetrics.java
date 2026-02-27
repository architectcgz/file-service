package com.architectcgz.file.infrastructure.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Bitmap 监控指标
 * 使用 Micrometer 记录各类指标
 * 
 * 监控指标包括:
 * - bitmap.write: Bitmap 写入操作计数 (成功/失败)
 * - bitmap.fallback: 回退到数据库的次数
 * - bitmap.cache: 缓存命中/未命中计数
 * - bitmap.operation.duration: 操作耗时分布
 * - bitmap.sync.total: 同步操作总次数
 * - bitmap.sync.parts: 同步的分片总数
 * - bitmap.cache.hit.ratio: 缓存命中率 (Gauge)
 * - bitmap.active.tasks: 活跃任务数 (Gauge)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BitmapMetrics {
    
    private final MeterRegistry meterRegistry;
    
    // 缓存命中/未命中计数器
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    
    /**
     * 记录 Bitmap 写入成功
     */
    public void recordWriteSuccess() {
        meterRegistry.counter("bitmap.write",
            "result", "success"
        ).increment();
    }
    
    /**
     * 记录 Bitmap 写入失败
     * 
     * @param errorType 错误类型 (如: connection_failure, timeout, etc.)
     */
    public void recordWriteFailure(String errorType) {
        meterRegistry.counter("bitmap.write",
            "result", "failure",
            "error", errorType
        ).increment();
    }
    
    /**
     * 记录回退到数据库
     * 
     * @param reason 回退原因 (如: redis_unavailable, redis_timeout, etc.)
     */
    public void recordFallback(String reason) {
        meterRegistry.counter("bitmap.fallback",
            "reason", reason
        ).increment();
    }
    
    /**
     * 记录缓存命中
     */
    public void recordCacheHit() {
        cacheHits.incrementAndGet();
        meterRegistry.counter("bitmap.cache",
            "result", "hit"
        ).increment();
    }
    
    /**
     * 记录缓存未命中
     */
    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
        meterRegistry.counter("bitmap.cache",
            "result", "miss"
        ).increment();
    }
    
    /**
     * 记录操作耗时
     * 
     * @param operation 操作名称 (如: savePart, countParts, findParts, etc.)
     * @param supplier 要执行的操作
     * @param <T> 返回值类型
     * @return 操作的返回值
     */
    public <T> T recordTiming(String operation, Supplier<T> supplier) {
        return Timer.builder("bitmap.operation.duration")
            .tag("operation", operation)
            .description("Bitmap 操作耗时")
            .register(meterRegistry)
            .record(supplier);
    }
    
    /**
     * 记录同步操作
     * 
     * @param partCount 同步的分片数量
     */
    public void recordSync(int partCount) {
        meterRegistry.counter("bitmap.sync.total",
            "type", "async"
        ).increment();
        meterRegistry.counter("bitmap.sync.parts").increment(partCount);
    }
    
    /**
     * 注册缓存命中率 Gauge
     * 在应用启动时自动调用
     */
    @PostConstruct
    public void registerCacheHitRatio() {
        Gauge.builder("bitmap.cache.hit.ratio", () -> {
            long hits = cacheHits.get();
            long misses = cacheMisses.get();
            long total = hits + misses;
            return total == 0 ? 0.0 : (double) hits / total;
        })
        .description("Bitmap 缓存命中率")
        .register(meterRegistry);
        
        log.info("Bitmap 监控指标已注册");
    }
    
    /**
     * 注册活跃任务数 Gauge
     * 
     * @param activeTasksSupplier 提供活跃任务数的 Supplier
     */
    public void registerActiveTasksGauge(Supplier<Integer> activeTasksSupplier) {
        Gauge.builder("bitmap.active.tasks", activeTasksSupplier, Supplier::get)
            .description("当前活跃的上传任务数")
            .register(meterRegistry);
        
        log.info("活跃任务数 Gauge 已注册");
    }
}
