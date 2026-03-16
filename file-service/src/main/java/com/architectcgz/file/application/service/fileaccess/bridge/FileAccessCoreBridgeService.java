package com.architectcgz.file.application.service.fileaccess.bridge;

import com.architectcgz.file.application.dto.FileUrlResponse;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.common.exception.FileNotFoundException;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.infrastructure.cache.FileUrlCacheManager;
import com.architectcgz.file.infrastructure.config.AccessProperties;
import com.architectcgz.file.infrastructure.storage.StorageService;
import com.platform.fileservice.core.application.service.AccessAppService;
import com.platform.fileservice.core.domain.exception.FileAccessDeniedException;
import com.platform.fileservice.core.domain.exception.FileAccessMutationException;
import com.platform.fileservice.core.domain.exception.FileAssetNotFoundException;
import com.platform.fileservice.core.domain.model.FileAsset;
import com.platform.fileservice.core.ports.storage.ObjectStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Delegates legacy file-access flows onto the new file-core access service.
 */
@Service
@RequiredArgsConstructor
public class FileAccessCoreBridgeService {

    private final AccessAppService accessAppService;
    private final ObjectStoragePort objectStoragePort;
    private final StorageService storageService;
    private final AccessProperties accessProperties;
    private final FileUrlCacheManager fileUrlCacheManager;

    public FileUrlResponse getFileUrl(String appId, String fileId, String requestUserId) {
        try {
            FileAsset fileAsset = accessAppService.getAccessibleFile(appId, fileId, normalizeUserId(requestUserId));
            if (fileAsset.accessLevel() == com.platform.fileservice.core.domain.model.AccessLevel.PUBLIC) {
                String cachedUrl = fileUrlCacheManager.get(fileId);
                if (cachedUrl != null) {
                    return FileUrlResponse.builder()
                            .url(cachedUrl)
                            .permanent(true)
                            .expiresAt(null)
                            .build();
                }
            }
            return buildFileUrlResponse(fileAsset);
        } catch (FileAssetNotFoundException ex) {
            throw translateNotFound(fileId, ex);
        } catch (FileAccessDeniedException ex) {
            throw new AccessDeniedException(String.format(FileServiceErrorMessages.ACCESS_DENIED_FILE, fileId));
        }
    }

    public void updateAccessLevel(String appId, String fileId, String requestUserId, AccessLevel newLevel) {
        String normalizedUserId = normalizeUserId(requestUserId);
        try {
            FileAsset fileAsset = accessAppService.getAccessibleFile(appId, fileId, normalizedUserId);
            com.platform.fileservice.core.domain.model.AccessLevel targetLevel = mapCoreAccessLevel(newLevel);
            if (fileAsset.accessLevel() == targetLevel) {
                return;
            }
            accessAppService.updateAccessLevel(appId, fileId, normalizedUserId, targetLevel);
            fileUrlCacheManager.evict(fileId);
        } catch (FileAssetNotFoundException ex) {
            throw translateNotFound(fileId, ex);
        } catch (FileAccessDeniedException ex) {
            throw new AccessDeniedException(String.format(FileServiceErrorMessages.ACCESS_DENIED_UPDATE_ACCESS_LEVEL, fileId));
        } catch (FileAccessMutationException ex) {
            throw new BusinessException(
                    FileServiceErrorCodes.UPDATE_ACCESS_LEVEL_FAILED,
                    String.format(FileServiceErrorMessages.UPDATE_ACCESS_LEVEL_FAILED, fileId),
                    ex
            );
        }
    }

    private FileUrlResponse buildFileUrlResponse(FileAsset fileAsset) {
        String bucketName = objectStoragePort.resolveBucketName(fileAsset.accessLevel());
        if (fileAsset.accessLevel() == com.platform.fileservice.core.domain.model.AccessLevel.PUBLIC) {
            String url = storageService.getPublicUrl(bucketName, fileAsset.objectKey());
            fileUrlCacheManager.put(fileAsset.fileId(), url);
            return FileUrlResponse.builder()
                    .url(url)
                    .permanent(true)
                    .expiresAt(null)
                    .build();
        }

        int expireSeconds = accessProperties.getPrivateUrlExpireSeconds();
        return FileUrlResponse.builder()
                .url(storageService.generatePresignedUrl(
                        bucketName,
                        fileAsset.objectKey(),
                        Duration.ofSeconds(expireSeconds)
                ))
                .permanent(false)
                .expiresAt(LocalDateTime.now().plusSeconds(expireSeconds))
                .build();
    }

    private com.platform.fileservice.core.domain.model.AccessLevel mapCoreAccessLevel(AccessLevel accessLevel) {
        return com.platform.fileservice.core.domain.model.AccessLevel.valueOf(accessLevel.name());
    }

    private String normalizeUserId(String requestUserId) {
        return StringUtils.hasText(requestUserId) ? requestUserId.trim() : null;
    }

    private FileNotFoundException translateNotFound(String fileId, FileAssetNotFoundException ex) {
        if (ex.getMessage() != null && ex.getMessage().contains("deleted")) {
            return FileNotFoundException.deleted(fileId);
        }
        return FileNotFoundException.notFound(fileId);
    }
}
