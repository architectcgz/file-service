package com.architectcgz.file.application.service;

import com.architectcgz.file.application.service.uploadtx.accounting.UploadTenantUsageAccountingService;
import com.architectcgz.file.application.service.uploadtx.mutation.UploadStorageReferenceMutationService;
import com.architectcgz.file.application.service.uploadtx.mutation.UploadTaskStatusMutationService;
import com.architectcgz.file.application.service.uploadtx.persistence.UploadMetadataPersistenceService;
import com.architectcgz.file.application.service.uploadtx.transaction.CompletedInstantUploadTransactionService;
import com.architectcgz.file.application.service.uploadtx.transaction.CompletedUploadTransactionService;
import com.architectcgz.file.application.service.uploadtx.transaction.InstantUploadTransactionService;
import com.architectcgz.file.application.service.uploadtx.transaction.NewUploadTransactionService;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import com.architectcgz.file.domain.repository.UploadTaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 上传元数据事务门面。
 *
 * 对外保留原有上传落库入口，内部拆分为多种 transaction use case。
 */
@Component
public class UploadTransactionHelper {

    private final NewUploadTransactionService newUploadTransactionService;
    private final InstantUploadTransactionService instantUploadTransactionService;
    private final CompletedUploadTransactionService completedUploadTransactionService;
    private final CompletedInstantUploadTransactionService completedInstantUploadTransactionService;

    @Autowired
    public UploadTransactionHelper(NewUploadTransactionService newUploadTransactionService,
                                   InstantUploadTransactionService instantUploadTransactionService,
                                   CompletedUploadTransactionService completedUploadTransactionService,
                                   CompletedInstantUploadTransactionService completedInstantUploadTransactionService) {
        this.newUploadTransactionService = newUploadTransactionService;
        this.instantUploadTransactionService = instantUploadTransactionService;
        this.completedUploadTransactionService = completedUploadTransactionService;
        this.completedInstantUploadTransactionService = completedInstantUploadTransactionService;
    }

    UploadTransactionHelper(StorageObjectRepository storageObjectRepository,
                            FileRecordRepository fileRecordRepository,
                            TenantUsageRepository tenantUsageRepository,
                            UploadTaskRepository uploadTaskRepository) {
        UploadMetadataPersistenceService uploadMetadataPersistenceService = new UploadMetadataPersistenceService(
                storageObjectRepository,
                fileRecordRepository
        );
        UploadTenantUsageAccountingService uploadTenantUsageAccountingService =
                new UploadTenantUsageAccountingService(tenantUsageRepository);
        UploadStorageReferenceMutationService uploadStorageReferenceMutationService =
                new UploadStorageReferenceMutationService(storageObjectRepository);
        UploadTaskStatusMutationService uploadTaskStatusMutationService =
                new UploadTaskStatusMutationService(uploadTaskRepository);
        this.newUploadTransactionService = new NewUploadTransactionService(
                uploadMetadataPersistenceService,
                uploadTenantUsageAccountingService
        );
        this.instantUploadTransactionService = new InstantUploadTransactionService(
                uploadStorageReferenceMutationService,
                uploadMetadataPersistenceService,
                uploadTenantUsageAccountingService
        );
        this.completedUploadTransactionService = new CompletedUploadTransactionService(
                uploadMetadataPersistenceService,
                uploadTenantUsageAccountingService,
                uploadTaskStatusMutationService
        );
        this.completedInstantUploadTransactionService = new CompletedInstantUploadTransactionService(
                uploadStorageReferenceMutationService,
                uploadMetadataPersistenceService,
                uploadTenantUsageAccountingService,
                uploadTaskStatusMutationService
        );
    }

    public void saveNewUpload(StorageObject storageObject, FileRecord fileRecord, long fileSize) {
        newUploadTransactionService.saveNewUpload(storageObject, fileRecord, fileSize);
    }

    public void saveInstantUpload(String storageObjectId, FileRecord fileRecord, long fileSize) {
        instantUploadTransactionService.saveInstantUpload(storageObjectId, fileRecord, fileSize);
    }

    public void saveCompletedUpload(UploadTask task, StorageObject storageObject, FileRecord fileRecord) {
        completedUploadTransactionService.saveCompletedUpload(task, storageObject, fileRecord);
    }

    public void saveCompletedInstantUpload(UploadTask task, String storageObjectId, FileRecord fileRecord) {
        completedInstantUploadTransactionService.saveCompletedInstantUpload(task, storageObjectId, fileRecord);
    }
}
