package com.architectcgz.file.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 孤立文件清理配置属性
 *
 * 管理定时清理任务的相关参数，包括批次大小、最大处理总量、
 * 时间保护窗口和分布式锁超时等配置
 */
@Data
@ConfigurationProperties(prefix = "file-service.cleanup.orphaned")
public class CleanupProperties {

    /**
     * 每批处理的最大记录数
     */
    private int batchSize = 100;

    /**
     * 单次调度的最大处理总量，防止长时间占用资源
     */
    private int maxTotal = 1000;

    /**
     * 时间保护窗口（分钟）
     * 只清理 updated_at 早于该时间窗口的零引用记录，
     * 避免与正常删除流程互相干扰
     */
    private int graceMinutes = 60;

    /**
     * 分布式锁超时时间（秒）
     * 防止多实例同时执行清理任务
     */
    private long lockTimeoutSeconds = 1800;
}
