package com.architectcgz.file.application.service.multipart.factory;

import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.model.UploadTask;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 分片上传领域对象工厂。
 */
@Component
public class MultipartUploadObjectFactory {

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

    public StorageObject buildStorageObject(UploadTask task, String fileHash, String bucketName) {
        return StorageObject.builder()
                .id(UuidCreator.getTimeOrderedEpoch().toString())
                .appId(task.getAppId())
                .fileHash(fileHash)
                .hashAlgorithm("MD5")
                .storagePath(task.getStoragePath())
                .bucketName(bucketName)
                .fileSize(task.getFileSize())
                .contentType(task.getContentType())
                .referenceCount(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public FileRecord buildFileRecord(UploadTask task, String userId, String storageObjectId, String fileHash,
                                      String storagePath, long fileSize, String contentType) {
        return FileRecord.builder()
                .id(UuidCreator.getTimeOrderedEpoch().toString())
                .appId(task.getAppId())
                .userId(userId)
                .storageObjectId(storageObjectId)
                .originalFilename(task.getFileName())
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

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "bin";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
