package com.architectcgz.file.application.service.instantupload.command;

import com.architectcgz.file.application.dto.InstantUploadCheckRequest;
import com.architectcgz.file.application.dto.InstantUploadCheckResponse;
import com.architectcgz.file.application.service.instantupload.assembler.InstantUploadResponseAssembler;
import com.architectcgz.file.application.service.instantupload.factory.InstantUploadObjectFactory;
import com.architectcgz.file.application.service.instantupload.persistence.InstantUploadPersistenceService;
import com.architectcgz.file.application.service.instantupload.query.InstantUploadRecordQueryService;
import com.architectcgz.file.application.service.instantupload.storage.InstantUploadStorageService;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstantUploadCheckCommandService {

    private final InstantUploadRecordQueryService instantUploadRecordQueryService;
    private final InstantUploadPersistenceService instantUploadPersistenceService;
    private final InstantUploadObjectFactory instantUploadObjectFactory;
    private final InstantUploadStorageService instantUploadStorageService;
    private final InstantUploadResponseAssembler instantUploadResponseAssembler;

    /**
     * 秒传检查会在命中共享对象时写入 FileRecord，因此保留事务边界在 command service。
     */
    @Transactional(rollbackFor = Exception.class)
    public InstantUploadCheckResponse checkInstantUpload(String appId, InstantUploadCheckRequest request, String userId) {
        log.debug("Checking instant upload: appId={}, fileHash={}, userId={}, fileName={}",
                appId, request.getFileHash(), userId, request.getFileName());

        Optional<FileRecord> existingFileRecord = instantUploadRecordQueryService.findExistingUserFile(
                appId,
                userId,
                request.getFileHash()
        );
        if (existingFileRecord.isPresent()) {
            FileRecord fileRecord = existingFileRecord.get();
            String fileUrl = instantUploadStorageService.resolveFileUrl(fileRecord);

            log.debug("Instant upload: user already has file with same hash: userId={}, fileHash={}, fileId={}",
                    userId, request.getFileHash(), fileRecord.getId());

            return instantUploadResponseAssembler.successResponse(fileRecord.getId(), fileUrl);
        }

        Optional<StorageObject> existingStorageObject = instantUploadRecordQueryService.findSharedStorageObject(
                appId,
                request.getFileHash()
        );
        if (existingStorageObject.isPresent()) {
            StorageObject storageObject = existingStorageObject.get();
            instantUploadPersistenceService.incrementReferenceCount(storageObject.getId());

            FileRecord fileRecord = instantUploadObjectFactory.createFileRecord(appId, userId, request, storageObject);
            instantUploadPersistenceService.saveFileRecord(fileRecord);

            String fileUrl = instantUploadStorageService.buildPublicUrl(storageObject);

            log.debug("Instant upload successful: fileHash={}, userId={}, fileId={}, storageObjectId={}",
                    request.getFileHash(), userId, fileRecord.getId(), storageObject.getId());

            return instantUploadResponseAssembler.successResponse(fileRecord.getId(), fileUrl);
        }

        log.debug("File not found for instant upload: fileHash={}, userId={}", request.getFileHash(), userId);
        return instantUploadResponseAssembler.needUploadResponse();
    }
}
