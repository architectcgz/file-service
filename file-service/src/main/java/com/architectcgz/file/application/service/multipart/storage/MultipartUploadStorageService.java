package com.architectcgz.file.application.service.multipart.storage;

import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.infrastructure.storage.S3StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.util.List;

/**
 * 分片上传存储协调服务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultipartUploadStorageService {

    private static final AccessLevel DEFAULT_MULTIPART_UPLOAD_ACCESS_LEVEL = AccessLevel.PUBLIC;

    private final S3StorageService s3StorageService;

    public String resolveUploadBucketName() {
        return s3StorageService.getBucketName(DEFAULT_MULTIPART_UPLOAD_ACCESS_LEVEL);
    }

    public String createMultipartUpload(String storagePath, String contentType, String bucketName) {
        return s3StorageService.createMultipartUpload(storagePath, contentType, bucketName);
    }

    public String uploadPart(String storagePath, String uploadId, int partNumber, byte[] data, String bucketName) {
        return s3StorageService.uploadPart(storagePath, uploadId, partNumber, data, bucketName);
    }

    public void abortMultipartUpload(String storagePath, String uploadId, String bucketName) {
        s3StorageService.abortMultipartUpload(storagePath, uploadId, bucketName);
    }

    public void completeMultipartUpload(String storagePath, String uploadId, List<CompletedPart> completedParts, String bucketName) {
        s3StorageService.completeMultipartUpload(storagePath, uploadId, completedParts, bucketName);
    }

    public void cleanupS3Quietly(String storagePath, String bucketName) {
        try {
            s3StorageService.delete(bucketName, storagePath);
            log.warn("分片合并后已清理临时 S3 对象: bucket={}, path={}", bucketName, storagePath);
        } catch (Exception cleanupEx) {
            log.error("分片合并后 S3 清理失败: bucket={}, path={}", bucketName, storagePath, cleanupEx);
        }
    }
}
