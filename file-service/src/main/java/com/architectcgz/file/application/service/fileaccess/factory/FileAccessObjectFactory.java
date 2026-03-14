package com.architectcgz.file.application.service.fileaccess.factory;

import com.architectcgz.file.application.dto.FileDetailResponse;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 文件访问对象工厂。
 */
@Component
public class FileAccessObjectFactory {

    public FileDetailResponse buildFileDetailResponse(FileRecord file) {
        return FileDetailResponse.builder()
                .fileId(file.getId())
                .userId(file.getUserId())
                .originalFilename(file.getOriginalFilename())
                .fileSize(file.getFileSize())
                .contentType(file.getContentType())
                .fileHash(file.getFileHash())
                .hashAlgorithm(file.getHashAlgorithm())
                .accessLevel(file.getAccessLevel())
                .status(file.getStatus())
                .createdAt(file.getCreatedAt())
                .updatedAt(file.getUpdatedAt())
                .build();
    }

    public StorageObject buildCopiedStorageObject(StorageObject sourceStorageObject, String targetBucketName) {
        return StorageObject.builder()
                .id(UuidCreator.getTimeOrderedEpoch().toString())
                .appId(sourceStorageObject.getAppId())
                .fileHash(sourceStorageObject.getFileHash())
                .hashAlgorithm(sourceStorageObject.getHashAlgorithm())
                .storagePath(sourceStorageObject.getStoragePath())
                .bucketName(targetBucketName)
                .fileSize(sourceStorageObject.getFileSize())
                .contentType(sourceStorageObject.getContentType())
                .referenceCount(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
