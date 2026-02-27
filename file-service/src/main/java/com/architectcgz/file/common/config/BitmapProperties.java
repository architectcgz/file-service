package com.architectcgz.file.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Bitmap 优化配置属性
 * 用于控制 Redis Bitmap 分片上传优化功能
 * 
 * @author architectcgz
 */
@Data
@Component
@ConfigurationProperties(prefix = "storage.multipart.bitmap")
public class BitmapProperties {
    
    /**
     * 是否启用 Bitmap 优化
     * 默认: true
     */
    private boolean enabled = true;
    
    /**
     * 同步批次大小（每 N 个分片同步一次到数据库）
     * 默认: 10
     * 建议范围: 5-50
     */
    private int syncBatchSize = 10;
    
    /**
     * Bitmap 过期时间（小时）
     * 默认: 24 小时
     * 建议范围: 12-48
     */
    private int expireHours = 24;
    
    /**
     * 最大分片数（用于遍历 Bitmap）
     * 超过此数量的文件将直接使用数据库模式
     * 默认: 10000
     */
    private int maxParts = 10000;
}
