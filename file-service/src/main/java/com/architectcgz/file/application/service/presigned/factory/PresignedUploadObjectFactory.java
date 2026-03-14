package com.architectcgz.file.application.service.presigned.factory;

import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.model.StorageObject;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 预签名上传对象工厂。
 */
@Component
public class PresignedUploadObjectFactory {

    public String generateStoragePath(String appId, String userId, String extension) {
        LocalDateTime now = LocalDateTime.now();
        return String.format("%s/%d/%02d/%02d/%s/files/%s.%s",
                appId,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                userId,
                generateFileId(),
                extension);
    }

    public String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    public StorageObject buildStorageObject(String appId, String fileHash, String storagePath,
                                            String bucketName, long fileSize, String contentType) {
        return StorageObject.builder()
                .id(generateFileId())
                .appId(appId)
                .fileHash(fileHash)
                .hashAlgorithm("MD5")
                .storagePath(storagePath)
                .bucketName(bucketName)
                .fileSize(fileSize)
                .contentType(contentType)
                .referenceCount(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public FileRecord buildFileRecord(String appId, String userId, String storageObjectId,
                                      String originalFilename, String storagePath, long fileSize,
                                      String contentType, String fileHash, AccessLevel accessLevel) {
        return FileRecord.builder()
                .id(generateFileId())
                .appId(appId)
                .userId(userId)
                .storageObjectId(storageObjectId)
                .originalFilename(originalFilename)
                .storagePath(storagePath)
                .fileSize(fileSize)
                .contentType(contentType)
                .fileHash(fileHash)
                .hashAlgorithm("MD5")
                .status(FileStatus.COMPLETED)
                .accessLevel(accessLevel)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private String generateFileId() {
        return UuidCreator.getTimeOrderedEpoch().toString();
    }
}
