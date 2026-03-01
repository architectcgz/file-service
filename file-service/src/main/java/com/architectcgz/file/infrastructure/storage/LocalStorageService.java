package com.architectcgz.file.infrastructure.storage;

import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地存储服务实现
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {
    
    @Value("${storage.local.base-path:./uploads}")
    private String basePath;
    
    @Value("${storage.local.base-url:http://localhost:8089/files}")
    private String baseUrl;
    
    @PostConstruct
    public void init() {
        try {
            Path uploadDir = Paths.get(basePath);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
                log.info("Created upload directory: {}", uploadDir.toAbsolutePath());
            }
        } catch (IOException e) {
            throw new BusinessException(String.format(FileServiceErrorMessages.LOCAL_DIR_CREATE_FAILED, e.getMessage()));
        }
    }
    
    @Override
    public String upload(byte[] data, String path) {
        return upload(data, path, "application/octet-stream");
    }
    
    @Override
    public String upload(byte[] data, String path, String contentType) {
        try {
            Path filePath = Paths.get(basePath, path);

            // 确保父目录存或
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            Files.write(filePath, data);
            log.debug("File uploaded to local storage: {}", filePath);

            return getUrl(path);
        } catch (IOException e) {
            log.error("Failed to upload file to local storage: {}", path, e);
            throw new BusinessException(String.format(FileServiceErrorMessages.FILE_UPLOAD_FAILED, e.getMessage()));
        }
    }

    @Override
    public String uploadFromFile(Path file, String storagePath, String contentType) {
        try {
            Path targetPath = Paths.get(basePath, storagePath);

            // 确保父目录存在
            Path parentDir = targetPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            Files.copy(file, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.debug("File uploaded to local storage from file: {}", targetPath);

            return getUrl(storagePath);
        } catch (IOException e) {
            log.error("Failed to upload file to local storage from file: {}", storagePath, e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }
    
    @Override
    public String uploadToPublicBucket(byte[] data, String path, String contentType) {
        // 本地存储不区分公开和私有存储桶，直接调用 upload
        return upload(data, path, contentType);
    }
    
    @Override
    public String uploadToPrivateBucket(byte[] data, String path, String contentType) {
        // 本地存储不区分公开和私有存储桶，直接调用 upload
        return upload(data, path, contentType);
    }
    
    @Override
    public String uploadByAccessLevel(byte[] data, String path, String contentType, 
                                     com.architectcgz.file.domain.model.AccessLevel accessLevel) {
        // 本地存储不区分访问级别，直接调用 upload
        return upload(data, path, contentType);
    }
    
    @Override
    public void delete(String path) {
        try {
            Path filePath = Paths.get(basePath, path);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.debug("File deleted from local storage: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Failed to delete file from local storage: {}", path, e);
            throw new BusinessException(String.format(FileServiceErrorMessages.FILE_DELETE_FAILED, e.getMessage()));
        }
    }
    
    @Override
    public String getUrl(String path) {
        return baseUrl + "/" + path;
    }
    
    @Override
    public String getPublicUrl(String path) {
        // 本地存储所有文件都是公开的
        return getUrl(path);
    }
    
    @Override
    public String generatePresignedUrl(String path, java.time.Duration expiration) {
        // 本地存储不支持预签名URL，直接返回普通URL
        log.warn("Local storage does not support presigned URLs, returning regular URL for: {}", path);
        return getUrl(path);
    }
    
    @Override
    public boolean exists(String path) {
        return Files.exists(Paths.get(basePath, path));
    }

    @Override
    public ObjectMetadata getObjectMetadata(String path) {
        try {
            Path filePath = Paths.get(basePath, path);
            if (!Files.exists(filePath)) {
                throw new BusinessException("文件不存在: " + path);
            }

            long fileSize = Files.size(filePath);
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            log.debug("Got object metadata from local storage: path={}, size={}, contentType={}",
                    path, fileSize, contentType);

            return ObjectMetadata.builder()
                    .fileSize(fileSize)
                    .contentType(contentType)
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to get object metadata from local storage: path={}, error={}",
                    path, e.getMessage(), e);
            throw new BusinessException("获取文件元数据失败: " + e.getMessage(), e);
        }
    }
}
