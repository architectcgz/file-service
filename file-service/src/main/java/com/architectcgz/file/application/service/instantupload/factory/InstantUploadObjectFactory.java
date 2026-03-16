package com.architectcgz.file.application.service.instantupload.factory;

import com.architectcgz.file.application.dto.InstantUploadCheckRequest;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.model.StorageObject;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 秒传对象工厂。
 */
@Component
public class InstantUploadObjectFactory {

    public FileRecord createFileRecord(String appId, String userId,
                                       InstantUploadCheckRequest request,
                                       StorageObject storageObject) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return FileRecord.builder()
                .id(UuidCreator.getTimeOrderedEpoch().toString())
                .appId(appId)
                .userId(userId)
                .storageObjectId(storageObject.getId())
                .originalFilename(request.getFileName())
                .storagePath(storageObject.getStoragePath())
                .fileSize(storageObject.getFileSize())
                .contentType(storageObject.getContentType())
                .fileHash(request.getFileHash())
                .hashAlgorithm("MD5")
                .status(FileStatus.COMPLETED)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
