package com.architectcgz.file.application.service.presigned.storage;

import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.infrastructure.storage.ObjectMetadata;
import com.architectcgz.file.infrastructure.storage.S3StorageService;
import com.architectcgz.file.infrastructure.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 预签名上传存储协调服务。
 */
@Component
@RequiredArgsConstructor
public class PresignedUploadStorageService {

    private final S3StorageService s3StorageService;
    private final StorageService storageService;

    @Setter
    @Value("${storage.access.presigned-url-expire-seconds:900}")
    private int presignedUrlExpireSeconds = 900;

    public int getPresignedUrlExpireSeconds() {
        return presignedUrlExpireSeconds;
    }

    public String resolveBucketName(AccessLevel accessLevel) {
        String bucketName = storageService.getBucketName(accessLevel);
        if (StringUtils.hasText(bucketName)) {
            return bucketName;
        }
        return s3StorageService.getBucketName(accessLevel);
    }

    public String generatePresignedPutUrl(String storagePath, String contentType, String bucketName) {
        return s3StorageService.generatePresignedPutUrl(
                storagePath,
                contentType,
                presignedUrlExpireSeconds,
                bucketName
        );
    }

    public ObjectMetadata getObjectMetadata(String bucketName, String storagePath) {
        return storageService.getObjectMetadata(bucketName, storagePath);
    }

    public String resolveFileUrl(AccessLevel accessLevel, String bucketName, String storagePath) {
        if (accessLevel == AccessLevel.PRIVATE) {
            return storageService.generatePresignedUrl(
                    bucketName,
                    storagePath,
                    Duration.ofSeconds(presignedUrlExpireSeconds)
            );
        }
        return storageService.getPublicUrl(bucketName, storagePath);
    }
}
