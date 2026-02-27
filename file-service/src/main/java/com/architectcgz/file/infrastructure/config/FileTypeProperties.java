package com.architectcgz.file.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 文件类型限制配置属或
 * 用于配置允许上传的文件类型（MIME类型和扩展名或
 */
@Data
@Component
@ConfigurationProperties(prefix = "storage.file-types")
public class FileTypeProperties {
    
    /**
     * 允许或MIME 类型分类
     */
    private AllowedTypes allowedTypes = new AllowedTypes();
    
    /**
     * 允许的文件扩展名列表
     */
    private Set<String> allowedExtensions = new HashSet<>(Arrays.asList(
        "jpg", "jpeg", "png", "gif", "webp", "svg",
        "mp4", "webm", "mov",
        "pdf", "doc", "docx", "txt"
    ));
    
    /**
     * 最大文件大小（字节或
     * 默认 100MB
     */
    private long maxFileSize = 104857600L;
    
    /**
     * 是否启用文件魔数检或
     * 默认启用，防止文件类型伪或
     */
    private boolean enableMagicNumberCheck = true;
    
    /**
     * 允许或MIME 类型分类
     */
    @Data
    public static class AllowedTypes {
        /**
         * 允许的图片类或
         */
        private Set<String> images = new HashSet<>(Arrays.asList(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/svg+xml"
        ));
        
        /**
         * 允许的视频类或
         */
        private Set<String> videos = new HashSet<>(Arrays.asList(
            "video/mp4",
            "video/webm",
            "video/quicktime"
        ));
        
        /**
         * 允许的文档类或
         */
        private Set<String> documents = new HashSet<>(Arrays.asList(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain"
        ));
        
        /**
         * 获取所有允许的 MIME 类型
         */
        public Set<String> getAllAllowedTypes() {
            Set<String> allTypes = new HashSet<>();
            allTypes.addAll(images);
            allTypes.addAll(videos);
            allTypes.addAll(documents);
            return allTypes;
        }
    }
    
    /**
     * 检或MIME 类型是否允许
     */
    public boolean isAllowedContentType(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return false;
        }
        return allowedTypes.getAllAllowedTypes().contains(contentType.toLowerCase());
    }
    
    /**
     * 检查文件扩展名是否允许
     */
    public boolean isAllowedExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return false;
        }
        return allowedExtensions.contains(extension.toLowerCase());
    }
    
    /**
     * 检查文件大小是否在限制或
     */
    public boolean isFileSizeAllowed(long fileSize) {
        return fileSize > 0 && fileSize <= maxFileSize;
    }
}
