package com.architectcgz.file.infrastructure.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 上传去重配置。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "upload.dedup")
public class UploadDedupProperties {

    /**
     * 去重 claim 租约时长。
     * 需覆盖一次完整对象上传 + 落库时间，避免健康请求过早丢失 claim。
     */
    @NotNull
    private Duration claimLease = Duration.ofMinutes(10);

    /**
     * 未拿到 claim 时，等待其他上传者完成并复用存储对象的最长时长。
     */
    @NotNull
    private Duration waitTimeout = Duration.ofSeconds(30);

    /**
     * 等待 claim 结果时的轮询间隔。
     */
    @NotNull
    private Duration pollInterval = Duration.ofMillis(100);

    /**
     * claim 心跳续租间隔。
     * 需显著小于 claimLease，避免健康上传因执行时间较长而被误接管。
     */
    @NotNull
    private Duration renewInterval = Duration.ofSeconds(5);

    /**
     * 等待方单次阻塞等待去重结果通知的最长时长。
     * 超时后会重新查库并尝试 claim，避免消息丢失时永久等待。
     */
    @NotNull
    private Duration notificationMaxWait = Duration.ofSeconds(3);

    /**
     * claim 续租调度线程数。
     * 并发大文件上传较多时可适度调大，避免续租任务排队。
     */
    private int renewSchedulerThreads = 2;
}
