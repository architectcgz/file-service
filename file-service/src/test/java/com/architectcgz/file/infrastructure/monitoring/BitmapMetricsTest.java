package com.architectcgz.file.infrastructure.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BitmapMetrics 单元测试
 * 
 * 验证监控指标的正确记录
 */
class BitmapMetricsTest {
    
    private MeterRegistry meterRegistry;
    private BitmapMetrics bitmapMetrics;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        bitmapMetrics = new BitmapMetrics(meterRegistry);
        bitmapMetrics.registerCacheHitRatio();
    }
    
    @Test
    void testRecordWriteSuccess() {
        // When
        bitmapMetrics.recordWriteSuccess();
        bitmapMetrics.recordWriteSuccess();
        
        // Then
        Counter counter = meterRegistry.find("bitmap.write")
            .tag("result", "success")
            .counter();
        
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }
    
    @Test
    void testRecordWriteFailure() {
        // When
        bitmapMetrics.recordWriteFailure("connection_failure");
        bitmapMetrics.recordWriteFailure("timeout");
        
        // Then
        Counter connectionFailureCounter = meterRegistry.find("bitmap.write")
            .tag("result", "failure")
            .tag("error", "connection_failure")
            .counter();
        
        Counter timeoutCounter = meterRegistry.find("bitmap.write")
            .tag("result", "failure")
            .tag("error", "timeout")
            .counter();
        
        assertThat(connectionFailureCounter).isNotNull();
        assertThat(connectionFailureCounter.count()).isEqualTo(1.0);
        assertThat(timeoutCounter).isNotNull();
        assertThat(timeoutCounter.count()).isEqualTo(1.0);
    }
    
    @Test
    void testRecordFallback() {
        // When
        bitmapMetrics.recordFallback("redis_unavailable");
        bitmapMetrics.recordFallback("redis_unavailable");
        bitmapMetrics.recordFallback("exception");
        
        // Then
        Counter redisUnavailableCounter = meterRegistry.find("bitmap.fallback")
            .tag("reason", "redis_unavailable")
            .counter();
        
        Counter exceptionCounter = meterRegistry.find("bitmap.fallback")
            .tag("reason", "exception")
            .counter();
        
        assertThat(redisUnavailableCounter).isNotNull();
        assertThat(redisUnavailableCounter.count()).isEqualTo(2.0);
        assertThat(exceptionCounter).isNotNull();
        assertThat(exceptionCounter.count()).isEqualTo(1.0);
    }
    
    @Test
    void testRecordCacheHitAndMiss() {
        // When
        bitmapMetrics.recordCacheHit();
        bitmapMetrics.recordCacheHit();
        bitmapMetrics.recordCacheHit();
        bitmapMetrics.recordCacheMiss();
        
        // Then
        Counter hitCounter = meterRegistry.find("bitmap.cache")
            .tag("result", "hit")
            .counter();
        
        Counter missCounter = meterRegistry.find("bitmap.cache")
            .tag("result", "miss")
            .counter();
        
        assertThat(hitCounter).isNotNull();
        assertThat(hitCounter.count()).isEqualTo(3.0);
        assertThat(missCounter).isNotNull();
        assertThat(missCounter.count()).isEqualTo(1.0);
    }
    
    @Test
    void testCacheHitRatioGauge() {
        // When - 3 hits, 1 miss = 75% hit ratio
        bitmapMetrics.recordCacheHit();
        bitmapMetrics.recordCacheHit();
        bitmapMetrics.recordCacheHit();
        bitmapMetrics.recordCacheMiss();
        
        // Then
        Gauge gauge = meterRegistry.find("bitmap.cache.hit.ratio").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(0.75);
    }
    
    @Test
    void testCacheHitRatioGaugeWithNoData() {
        // When - no hits or misses
        
        // Then
        Gauge gauge = meterRegistry.find("bitmap.cache.hit.ratio").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(0.0);
    }
    
    @Test
    void testRecordTiming() {
        // When
        String result = bitmapMetrics.recordTiming("savePart", () -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "success";
        });
        
        // Then
        assertThat(result).isEqualTo("success");
        
        Timer timer = meterRegistry.find("bitmap.operation.duration")
            .tag("operation", "savePart")
            .timer();
        
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThan(0);
    }
    
    @Test
    void testRecordSync() {
        // When
        bitmapMetrics.recordSync(10);
        bitmapMetrics.recordSync(5);
        
        // Then
        Counter syncTotalCounter = meterRegistry.find("bitmap.sync.total")
            .tag("type", "async")
            .counter();
        
        Counter syncPartsCounter = meterRegistry.find("bitmap.sync.parts")
            .counter();
        
        assertThat(syncTotalCounter).isNotNull();
        assertThat(syncTotalCounter.count()).isEqualTo(2.0);
        assertThat(syncPartsCounter).isNotNull();
        assertThat(syncPartsCounter.count()).isEqualTo(15.0);
    }
    
    @Test
    void testRegisterActiveTasksGauge() {
        // Given
        AtomicInteger activeTasksCount = new AtomicInteger(0);
        
        // When
        bitmapMetrics.registerActiveTasksGauge(activeTasksCount::get);
        
        // Then
        Gauge gauge = meterRegistry.find("bitmap.active.tasks").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(0.0);
        
        // When - update active tasks
        activeTasksCount.set(5);
        
        // Then
        assertThat(gauge.value()).isEqualTo(5.0);
    }
}
