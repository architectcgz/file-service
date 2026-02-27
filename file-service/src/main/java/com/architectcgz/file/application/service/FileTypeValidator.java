package com.architectcgz.file.application.service;

import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.infrastructure.config.FileTypeProperties;
import com.architectcgz.file.infrastructure.util.FileTypeDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文件类型验证服务
 * 负责验证文件扩展名、Content-Type、文件大小和文件魔数
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileTypeValidator {
    
    private final FileTypeProperties fileTypeProperties;
    
    /**
     * 验证文件扩展名
     * 
     * @param fileName 文件名
     * @throws BusinessException 如果扩展名不允许
     */
    public void validateFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            throw new BusinessException("文件名不能为空");
        }
        
        String extension = getExtension(fileName);
        if (extension == null || extension.isEmpty()) {
            throw new BusinessException("文件必须有扩展名");
        }
        
        if (!fileTypeProperties.isAllowedExtension(extension)) {
            log.warn("不支持的文件扩展名: {}", extension);
            throw new BusinessException("不支持的文件扩展名: " + extension);
        }
        
        log.debug("文件扩展名验证通过: {}", extension);
    }
    
    /**
     * 验证 Content-Type
     * 
     * @param contentType Content-Type 值
     * @throws BusinessException 如果 Content-Type 不允许
     */
    public void validateContentType(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            throw new BusinessException("Content-Type 不能为空");
        }
        
        // 移除可能的参数（如 charset）
        String mimeType = contentType.split(";")[0].trim();
        
        if (!fileTypeProperties.isAllowedContentType(mimeType)) {
            log.warn("不支持的文件类型: {}", mimeType);
            throw new BusinessException("不支持的文件类型: " + mimeType);
        }
        
        log.debug("Content-Type 验证通过: {}", mimeType);
    }
    
    /**
     * 验证文件大小
     * 
     * @param fileSize 文件大小（字节）
     * @throws BusinessException 如果文件大小超出限制
     */
    public void validateFileSize(long fileSize) {
        if (fileSize <= 0) {
            throw new BusinessException("文件大小必须大于 0");
        }
        
        if (!fileTypeProperties.isFileSizeAllowed(fileSize)) {
            long maxSizeMB = fileTypeProperties.getMaxFileSize() / (1024 * 1024);
            log.warn("文件大小超出限制: {} bytes (最大 {} MB)", fileSize, maxSizeMB);
            throw new BusinessException("文件大小超出限制，最大允许 " + maxSizeMB + " MB");
        }
        
        log.debug("文件大小验证通过: {} bytes", fileSize);
    }
    
    /**
     * 完整验证文件（扩展名 + Content-Type + 大小）
     * 
     * @param fileName 文件名
     * @param contentType Content-Type 值
     * @param fileSize 文件大小（字节）
     * @throws BusinessException 如果验证失败
     */
    public void validateFile(String fileName, String contentType, long fileSize) {
        validateFileExtension(fileName);
        validateContentType(contentType);
        validateFileSize(fileSize);
        
        log.info("文件验证通过: fileName={}, contentType={}, size={}", fileName, contentType, fileSize);
    }
    
    /**
     * 完整验证文件（根据配置决定是否包含文件魔数检测）
     * 
     * @param fileName 文件名
     * @param contentType Content-Type 值
     * @param fileHeader 文件头字节数组（至少 12 字节）
     * @param fileSize 文件大小（字节）
     * @throws BusinessException 如果验证失败
     */
    public void validateFileWithMagicNumber(String fileName, String contentType, 
                                           byte[] fileHeader, long fileSize) {
        // 1. 验证扩展名
        validateFileExtension(fileName);
        
        // 2. 验证 Content-Type
        validateContentType(contentType);
        
        // 3. 验证文件大小
        validateFileSize(fileSize);
        
        // 4. 如果启用了魔数检测，则进行检测
        if (!fileTypeProperties.isEnableMagicNumberCheck()) {
            log.debug("魔数检测已禁用，跳过文件类型检测");
            return;
        }
        
        // 5. 检测实际文件类型（通过魔数）
        if (fileHeader == null || fileHeader.length < 4) {
            log.warn("文件头数据不足，无法进行魔数检测");
            throw new BusinessException("文件头数据不足，无法验证文件类型");
        }
        
        String detectedType = FileTypeDetector.detectFileType(fileHeader);
        if (detectedType == null) {
            log.warn("无法识别文件类型，文件名: {}", fileName);
            throw new BusinessException("无法识别文件类型");
        }
        
        // 6. 验证声明的类型与检测到的类型是否匹配
        if (!FileTypeDetector.isTypeMatch(contentType, detectedType)) {
            log.warn("文件类型不匹配: 声明={}, 检测={}, 文件名={}", 
                contentType, detectedType, fileName);
            throw new BusinessException("文件类型与内容不匹配");
        }
        
        log.info("文件验证通过（含魔数检测）: fileName={}, contentType={}, detectedType={}, size={}", 
            fileName, contentType, detectedType, fileSize);
    }
    
    /**
     * 提取文件扩展名
     * 
     * @param fileName 文件名
     * @return 扩展名（小写，不含点）
     */
    private String getExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            return null;
        }
        
        return fileName.substring(lastDotIndex + 1).toLowerCase();
    }
}
