package com.architectcgz.file.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 上传配置属或
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "upload")
public class UploadProperties {
    
    /**
     * 最大文件大小（字节或
     */
    private long maxSize = 10485760; // 10MB
    
    /**
     * 允许的图片类或
     */
    private String allowedImageTypes = "image/jpeg,image/png,image/gif,image/webp";
    
    /**
     * 允许的文件类或
     */
    private String allowedFileTypes = "application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain";
    
    /**
     * 图片处理配置
     */
    private ImageConfig image = new ImageConfig();
    
    @Data
    public static class ImageConfig {
        /**
         * 最大宽度
         */
        private int maxWidth = 1920;
        
        /**
         * 最大高度
         */
        private int maxHeight = 1080;
        
        /**
         * 压缩质量
         */
        private double quality = 0.85;
        
        /**
         * 缩略图宽度
         */
        private int thumbnailWidth = 200;
        
        /**
         * 缩略图高度
         */
        private int thumbnailHeight = 200;
        
        /**
         * 是否转换为WebP
         */
        private boolean convertToWebp = true;
    }
}
