package com.architectcgz.file.application.service.upload.factory;

import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.model.UploadFile;
import com.architectcgz.file.interfaces.dto.UploadResult;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 表单上传领域对象工厂。
 */
@Component
public class UploadObjectFactory {

    private static final AccessLevel DEFAULT_UPLOAD_ACCESS_LEVEL = AccessLevel.PUBLIC;

    public String generateFileId() {
        return UuidCreator.getTimeOrderedEpoch().toString();
    }

    public FileRecord buildFileRecord(String fileRecordId, String appId, String userId,
                                      String storageObjectId, String storagePath,
                                      String originalFilename, long fileSize,
                                      String contentType, String fileHash) {
        return FileRecord.builder()
                .id(fileRecordId)
                .appId(appId)
                .userId(userId)
                .storageObjectId(storageObjectId)
                .originalFilename(originalFilename)
                .storagePath(storagePath)
                .fileSize(fileSize)
                .contentType(contentType)
                .fileHash(fileHash)
                .hashAlgorithm("MD5")
                .accessLevel(DEFAULT_UPLOAD_ACCESS_LEVEL)
                .status(FileStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public StorageObject buildStorageObject(String appId, String storageObjectId, String fileHash,
                                            String storagePath, long fileSize, String contentType,
                                            String bucketName) {
        return StorageObject.builder()
                .id(storageObjectId)
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

    public UploadResult buildUploadResult(String fileRecordId, String fileUrl, String thumbnailUrl,
                                          String originalFilename, long fileSize,
                                          UploadFile.FileType fileType, String contentType) {
        return UploadResult.builder()
                .fileId(fileRecordId)
                .url(fileUrl)
                .thumbnailUrl(thumbnailUrl)
                .originalFilename(originalFilename)
                .size(fileSize)
                .fileType(fileType.name())
                .contentType(contentType)
                .build();
    }

    public String generateStoragePath(String appId, String userId, String type, String filename) {
        LocalDateTime now = LocalDateTime.now();
        return String.format("%s/%d/%02d/%02d/%s/%s/%s.%s",
                appId,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                userId,
                type,
                generateFileId(),
                resolveExtension(filename));
    }

    public String generateStoragePathWithExtension(String appId, String userId, String type, String extension) {
        LocalDateTime now = LocalDateTime.now();
        return String.format("%s/%d/%02d/%02d/%s/%s/%s.%s",
                appId,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                userId,
                type,
                generateFileId(),
                extension);
    }

    public String resolveExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
