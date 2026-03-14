package com.architectcgz.file.application.service.direct.factory;

import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.model.StorageObject;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 直传领域对象工厂。
 */
@Component
public class DirectUploadObjectFactory {

    public String generateStoragePath(String appId, String userId, String fileName) {
        LocalDateTime now = LocalDateTime.now();
        String fileId = UuidCreator.getTimeOrderedEpoch().toString();
        String extension = getExtension(fileName);

        return String.format("%s/%d/%02d/%02d/%s/files/%s.%s",
                appId,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                userId,
                fileId,
                extension);
    }

    public FileRecord buildFileRecord(String appId, String userId, String storageObjectId,
                                      String originalFilename, String storagePath, long fileSize,
                                      String contentType, String fileHash) {
        return FileRecord.builder()
                .id(UuidCreator.getTimeOrderedEpoch().toString())
                .appId(appId)
                .userId(userId)
                .storageObjectId(storageObjectId)
                .originalFilename(originalFilename)
                .storagePath(storagePath)
                .fileSize(fileSize)
                .contentType(contentType)
                .fileHash(fileHash)
                .hashAlgorithm("MD5")
                .accessLevel(AccessLevel.PUBLIC)
                .status(FileStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public StorageObject buildStorageObject(String appId, String storagePath, long fileSize,
                                            String contentType, String fileHash, String bucketName) {
        return StorageObject.builder()
                .id(UuidCreator.getTimeOrderedEpoch().toString())
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

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "bin";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
