package com.architectcgz.file.application.service.presigned.command;

import com.architectcgz.file.application.dto.ConfirmUploadRequest;
import com.architectcgz.file.application.service.presigned.factory.PresignedUploadObjectFactory;
import com.architectcgz.file.application.service.presigned.query.PresignedStorageObjectQueryService;
import com.architectcgz.file.application.service.presigned.storage.PresignedUploadStorageService;
import com.architectcgz.file.application.service.presigned.validator.PresignedUploadAccessResolver;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresignedUploadConfirmCommandService {

    private final PresignedUploadAccessResolver presignedUploadAccessResolver;
    private final PresignedStorageObjectQueryService presignedStorageObjectQueryService;
    private final PresignedUploadObjectFactory presignedUploadObjectFactory;
    private final PresignedUploadStorageService presignedUploadStorageService;
    private final StorageObjectRepository storageObjectRepository;
    private final FileRecordRepository fileRecordRepository;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> confirmUpload(String appId, ConfirmUploadRequest request, String userId) {
        log.info("Confirming upload: userId={}, storagePath={}, fileHash={}",
                userId, request.getStoragePath(), request.getFileHash());

        var accessLevel = presignedUploadAccessResolver.resolveAccessLevel(request.getAccessLevel());
        String bucketName = presignedUploadStorageService.resolveBucketName(accessLevel);
        var metadata = presignedUploadStorageService.getObjectMetadata(bucketName, request.getStoragePath());
        long realFileSize = metadata.getFileSize();
        String realContentType = metadata.getContentType();

        log.info("Got object metadata from storage: storagePath={}, fileSize={}, contentType={}",
                request.getStoragePath(), realFileSize, realContentType);

        var existingStorageObject = presignedStorageObjectQueryService.findExistingStorageObject(
                appId, request.getFileHash(), bucketName);

        String storageObjectId;
        String fileUrl;
        String recordStoragePath;
        long recordFileSize;
        String recordContentType;

        if (existingStorageObject.isPresent()) {
            var storageObject = existingStorageObject.get();
            storageObjectRepository.incrementReferenceCount(storageObject.getId());
            storageObjectId = storageObject.getId();
            fileUrl = presignedUploadStorageService.resolveFileUrl(
                    accessLevel, storageObject.getBucketName(), storageObject.getStoragePath());
            recordStoragePath = storageObject.getStoragePath();
            recordFileSize = storageObject.getFileSize();
            recordContentType = storageObject.getContentType();

            log.info("File deduplication: fileHash={}, storageObjectId={}, referenceCount={}",
                    request.getFileHash(), storageObjectId, storageObject.getReferenceCount() + 1);
        } else {
            var storageObject = presignedUploadObjectFactory.buildStorageObject(
                    appId, request.getFileHash(), request.getStoragePath(),
                    bucketName, realFileSize, realContentType
            );
            storageObjectRepository.save(storageObject);
            storageObjectId = storageObject.getId();
            fileUrl = presignedUploadStorageService.resolveFileUrl(accessLevel, bucketName, request.getStoragePath());
            recordStoragePath = request.getStoragePath();
            recordFileSize = realFileSize;
            recordContentType = realContentType;

            log.info("New StorageObject created: storageObjectId={}, storagePath={}",
                    storageObjectId, request.getStoragePath());
        }

        var fileRecord = presignedUploadObjectFactory.buildFileRecord(
                appId, userId, storageObjectId, request.getOriginalFilename(), recordStoragePath,
                recordFileSize, recordContentType, request.getFileHash(), accessLevel
        );
        fileRecordRepository.save(fileRecord);

        log.info("Upload confirmed: fileRecordId={}, userId={}, storagePath={}",
                fileRecord.getId(), userId, request.getStoragePath());

        Map<String, String> result = new HashMap<>();
        result.put("fileId", fileRecord.getId());
        result.put("url", fileUrl);
        return result;
    }
}
