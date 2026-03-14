package com.architectcgz.file.application.service.uploadtx.transaction;

import com.architectcgz.file.application.service.uploadtx.accounting.UploadTenantUsageAccountingService;
import com.architectcgz.file.application.service.uploadtx.persistence.UploadMetadataPersistenceService;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NewUploadTransactionService {

    private final UploadMetadataPersistenceService uploadMetadataPersistenceService;
    private final UploadTenantUsageAccountingService uploadTenantUsageAccountingService;

    @Transactional(rollbackFor = Exception.class)
    public void saveNewUpload(StorageObject storageObject, FileRecord fileRecord, long fileSize) {
        uploadMetadataPersistenceService.saveStorageObject(storageObject);
        uploadMetadataPersistenceService.saveFileRecord(fileRecord);
        uploadTenantUsageAccountingService.incrementUsage(fileRecord.getAppId(), fileSize);
    }
}
