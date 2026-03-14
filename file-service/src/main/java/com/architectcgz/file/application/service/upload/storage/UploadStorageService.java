package com.architectcgz.file.application.service.upload.storage;

import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.infrastructure.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * 表单上传存储协调服务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadStorageService {

    private static final AccessLevel DEFAULT_UPLOAD_ACCESS_LEVEL = AccessLevel.PUBLIC;

    private final StorageService storageService;

    public AccessLevel getDefaultUploadAccessLevel() {
        return DEFAULT_UPLOAD_ACCESS_LEVEL;
    }

    public String resolveUploadBucketName() {
        return storageService.getBucketName(DEFAULT_UPLOAD_ACCESS_LEVEL);
    }

    public String uploadFile(byte[] data, String storagePath, String contentType) {
        return storageService.uploadByAccessLevel(
                data,
                storagePath,
                contentType,
                DEFAULT_UPLOAD_ACCESS_LEVEL
        );
    }

    public String uploadTempFile(Path file, String storagePath, String contentType) {
        return storageService.uploadFromFile(
                file,
                storagePath,
                contentType,
                DEFAULT_UPLOAD_ACCESS_LEVEL
        );
    }

    public String resolvePublicUrl(String bucketName, String storagePath) {
        return storageService.getPublicUrl(bucketName, storagePath);
    }

    public void cleanupUploadedPaths(List<String> storagePaths) {
        for (String storagePath : storagePaths) {
            try {
                storageService.delete(storagePath);
                log.warn("Storage compensation cleanup: deleted path={}", storagePath);
            } catch (Exception cleanupEx) {
                log.error("Storage compensation cleanup failed: path={}", storagePath, cleanupEx);
            }
        }
    }
}
