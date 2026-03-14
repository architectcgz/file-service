package com.architectcgz.file.application.service.fileaccess.query;

import com.architectcgz.file.application.dto.FileUrlResponse;
import com.architectcgz.file.application.service.fileaccess.storage.FileAccessStorageService;
import com.architectcgz.file.application.service.fileaccess.validator.FileAccessValidator;
import com.architectcgz.file.domain.model.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUrlQueryService {

    private final FileAccessRecordQueryService fileAccessRecordQueryService;
    private final FileAccessValidator fileAccessValidator;
    private final FileAccessStorageService fileAccessStorageService;

    public FileUrlResponse getFileUrl(String appId, String fileId, String requestUserId) {
        String cachedUrl = fileAccessStorageService.getCachedUrl(fileId);
        if (cachedUrl != null) {
            var file = fileAccessRecordQueryService.findFileOrThrow(fileId);
            fileAccessValidator.validateFileAccess(file, fileId, requestUserId, appId);

            log.debug("Returning cached URL for fileId={}", fileId);
            return FileUrlResponse.builder()
                    .url(cachedUrl)
                    .permanent(true)
                    .expiresAt(null)
                    .build();
        }

        var file = fileAccessRecordQueryService.findFileOrThrow(fileId);
        fileAccessValidator.validateFileAccess(file, fileId, requestUserId, appId);

        String url;
        boolean isPermanent;
        LocalDateTime expiresAt;
        String bucketName = fileAccessRecordQueryService.resolveBucketName(file);
        String publicBucketName = fileAccessStorageService.resolvePublicBucketName();

        if (file.getAccessLevel() == AccessLevel.PUBLIC) {
            if (fileAccessStorageService.isPublicBucket(bucketName, publicBucketName)) {
                url = fileAccessStorageService.getPublicUrl(bucketName, file.getStoragePath());
                isPermanent = true;
                expiresAt = null;
                fileAccessStorageService.cachePublicUrl(fileId, url);
            } else {
                url = fileAccessStorageService.generatePresignedUrl(bucketName, file.getStoragePath());
                isPermanent = false;
                expiresAt = LocalDateTime.now().plusSeconds(fileAccessStorageService.getPrivateUrlExpireSeconds());
                log.warn("Public file stored outside public bucket, fallback to presigned URL: fileId={}, bucket={}",
                        fileId, bucketName);
            }
        } else {
            url = fileAccessStorageService.generatePresignedUrl(bucketName, file.getStoragePath());
            isPermanent = false;
            expiresAt = LocalDateTime.now().plusSeconds(fileAccessStorageService.getPrivateUrlExpireSeconds());
        }

        return FileUrlResponse.builder()
                .url(url)
                .permanent(isPermanent)
                .expiresAt(expiresAt)
                .build();
    }
}
