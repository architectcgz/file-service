package com.architectcgz.file.application.service;

import com.architectcgz.file.application.service.uploadtx.accounting.UploadTenantUsageAccountingService;
import com.architectcgz.file.application.service.uploadtx.mutation.UploadStorageReferenceMutationService;
import com.architectcgz.file.application.service.uploadtx.persistence.UploadMetadataPersistenceService;
import com.architectcgz.file.application.service.uploadtx.transaction.InstantUploadTransactionService;
import com.architectcgz.file.application.service.uploadtx.transaction.NewUploadTransactionService;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.domain.repository.TenantRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
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

    @Autowired
    public UploadTransactionHelper(NewUploadTransactionService newUploadTransactionService,
                                   InstantUploadTransactionService instantUploadTransactionService) {
        this.newUploadTransactionService = newUploadTransactionService;
        this.instantUploadTransactionService = instantUploadTransactionService;
    }

    UploadTransactionHelper(StorageObjectRepository storageObjectRepository,
                            FileRecordRepository fileRecordRepository,
                            TenantRepository tenantRepository,
                            TenantUsageRepository tenantUsageRepository) {
        UploadMetadataPersistenceService uploadMetadataPersistenceService = new UploadMetadataPersistenceService(
                storageObjectRepository,
                fileRecordRepository
        );
        UploadTenantUsageAccountingService uploadTenantUsageAccountingService =
                new UploadTenantUsageAccountingService(tenantUsageRepository, tenantRepository);
        UploadStorageReferenceMutationService uploadStorageReferenceMutationService =
                new UploadStorageReferenceMutationService(storageObjectRepository);
        this.newUploadTransactionService = new NewUploadTransactionService(
                uploadMetadataPersistenceService,
                uploadTenantUsageAccountingService
        );
        this.instantUploadTransactionService = new InstantUploadTransactionService(
                uploadStorageReferenceMutationService,
                uploadMetadataPersistenceService,
                uploadTenantUsageAccountingService
        );
    }

    public void saveNewUpload(StorageObject storageObject, FileRecord fileRecord, long fileSize) {
        newUploadTransactionService.saveNewUpload(storageObject, fileRecord, fileSize);
    }

    public void saveInstantUpload(String storageObjectId, FileRecord fileRecord, long fileSize) {
        instantUploadTransactionService.saveInstantUpload(storageObjectId, fileRecord, fileSize);
    }
}
