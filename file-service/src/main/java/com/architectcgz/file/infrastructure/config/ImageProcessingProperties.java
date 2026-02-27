package com.architectcgz.file.infrastructure.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 图片处理配置属性
 * 
 * 使用 @ConfigurationProperties 自动绑定配置，支持动态刷新
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "upload.image")
public class ImageProcessingProperties {
    
    /**
     * 图片最大宽度（像素）
     */
    @Min(value = 100, message = "图片最大宽度不能少于 100 像素")
    private int maxWidth = 1920;
    
    /**
     * 图片最大高度（像素）
     */
    @Min(value = 100, message = "图片最大高度不能少于 100 像素")
    private int maxHeight = 1080;
    
    /**
     * 图片质量（0.0-1.0）
     */
    @DecimalMin(value = "0.1", message = "图片质量不能低于 0.1")
    @DecimalMax(value = "1.0", message = "图片质量不能超过 1.0")
    private double quality = 0.85;
    
    /**
     * 缩略图宽度（像素）
     */
    @Min(value = 50, message = "缩略图宽度不能少于 50 像素")
    private int thumbnailWidth = 200;
    
    /**
     * 缩略图高度（像素）
     */
    @Min(value = 50, message = "缩略图高度不能少于 50 像素")
    private int thumbnailHeight = 200;
    
    /**
     * 是否转换为 WebP 格式
     */
    private boolean convertToWebp = true;
}
