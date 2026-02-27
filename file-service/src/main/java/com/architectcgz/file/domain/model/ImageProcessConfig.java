package com.architectcgz.file.domain.model;

import lombok.Builder;
import lombok.Getter;

/**
 * 图片处理配置
 */
@Getter
@Builder
public class ImageProcessConfig {
    
    /**
     * 最大宽度
     */
    @Builder.Default
    private int maxWidth = 1920;
    
    /**
     * 最大高度
     */
    @Builder.Default
    private int maxHeight = 1080;
    
    /**
     * 压缩质量 (0.0 - 1.0)
     */
    @Builder.Default
    private double quality = 0.85;
    
    /**
     * 是否转换为WebP格式
     */
    @Builder.Default
    private boolean convertToWebP = true;
    
    /**
     * 缩略图宽度
     */
    @Builder.Default
    private int thumbnailWidth = 200;
    
    /**
     * 缩略图高度
     */
    @Builder.Default
    private int thumbnailHeight = 200;
    
    /**
     * 创建默认配置
     */
    public static ImageProcessConfig defaultConfig() {
        return ImageProcessConfig.builder().build();
    }
}
