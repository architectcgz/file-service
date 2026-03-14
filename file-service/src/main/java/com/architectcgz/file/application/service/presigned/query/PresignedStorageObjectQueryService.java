package com.architectcgz.file.application.service.presigned.query;

import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 预签名上传存储对象查询服务。
 */
@Service
@RequiredArgsConstructor
public class PresignedStorageObjectQueryService {

    private final StorageObjectRepository storageObjectRepository;

    public Optional<StorageObject> findExistingStorageObject(String appId, String fileHash, String bucketName) {
        if (StringUtils.hasText(bucketName)) {
            return storageObjectRepository.findByFileHashAndBucket(appId, fileHash, bucketName);
        }
        return storageObjectRepository.findByFileHash(appId, fileHash);
    }
}
