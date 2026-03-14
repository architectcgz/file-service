package com.architectcgz.file.application.service.instantupload.query;

import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.infrastructure.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 秒传记录查询服务。
 */
@Service
@RequiredArgsConstructor
public class InstantUploadRecordQueryService {

    private static final AccessLevel DEFAULT_INSTANT_UPLOAD_ACCESS_LEVEL = AccessLevel.PUBLIC;

    private final StorageObjectRepository storageObjectRepository;
    private final FileRecordRepository fileRecordRepository;
    private final StorageService storageService;

    public Optional<FileRecord> findExistingUserFile(String appId, String userId, String fileHash) {
        return fileRecordRepository.findByUserIdAndFileHash(appId, userId, fileHash)
                .filter(fileRecord -> !fileRecord.isDeleted());
    }

    public Optional<StorageObject> findSharedStorageObject(String appId, String fileHash) {
        String targetBucketName = storageService.getBucketName(DEFAULT_INSTANT_UPLOAD_ACCESS_LEVEL);
        return StringUtils.hasText(targetBucketName)
                ? storageObjectRepository.findByFileHashAndBucket(appId, fileHash, targetBucketName)
                : storageObjectRepository.findByFileHash(appId, fileHash);
    }
}
