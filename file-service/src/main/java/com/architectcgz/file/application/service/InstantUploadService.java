package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.InstantUploadCheckRequest;
import com.architectcgz.file.application.dto.InstantUploadCheckResponse;
import com.architectcgz.file.application.service.instantupload.assembler.InstantUploadResponseAssembler;
import com.architectcgz.file.application.service.instantupload.factory.InstantUploadObjectFactory;
import com.architectcgz.file.application.service.instantupload.command.InstantUploadCheckCommandService;
import com.architectcgz.file.application.service.instantupload.persistence.InstantUploadPersistenceService;
import com.architectcgz.file.application.service.instantupload.query.InstantUploadRecordQueryService;
import com.architectcgz.file.application.service.instantupload.storage.InstantUploadStorageService;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.infrastructure.storage.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 秒传应用层门面。
 *
 * 对外保留秒传检查入口，内部实现下沉到 command service。
 */
@Service
public class InstantUploadService {

    private final InstantUploadCheckCommandService instantUploadCheckCommandService;

    @Autowired
    public InstantUploadService(InstantUploadCheckCommandService instantUploadCheckCommandService) {
        this.instantUploadCheckCommandService = instantUploadCheckCommandService;
    }

    InstantUploadService(StorageObjectRepository storageObjectRepository,
                         FileRecordRepository fileRecordRepository,
                         StorageService storageService) {
        this(new InstantUploadCheckCommandService(
                new InstantUploadRecordQueryService(storageObjectRepository, fileRecordRepository, storageService),
                new InstantUploadPersistenceService(storageObjectRepository, fileRecordRepository),
                new InstantUploadObjectFactory(),
                new InstantUploadStorageService(storageObjectRepository, storageService),
                new InstantUploadResponseAssembler()
        ));
    }

    public InstantUploadCheckResponse checkInstantUpload(String appId, InstantUploadCheckRequest request, String userId) {
        return instantUploadCheckCommandService.checkInstantUpload(appId, request, userId);
    }
}
