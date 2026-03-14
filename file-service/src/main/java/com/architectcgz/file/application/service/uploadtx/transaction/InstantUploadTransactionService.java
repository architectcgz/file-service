package com.architectcgz.file.application.service.uploadtx.transaction;

import com.architectcgz.file.application.service.uploadtx.accounting.UploadTenantUsageAccountingService;
import com.architectcgz.file.application.service.uploadtx.mutation.UploadStorageReferenceMutationService;
import com.architectcgz.file.application.service.uploadtx.persistence.UploadMetadataPersistenceService;
import com.architectcgz.file.domain.model.FileRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstantUploadTransactionService {

    private final UploadStorageReferenceMutationService uploadStorageReferenceMutationService;
    private final UploadMetadataPersistenceService uploadMetadataPersistenceService;
    private final UploadTenantUsageAccountingService uploadTenantUsageAccountingService;

    @Transactional(rollbackFor = Exception.class)
    public void saveInstantUpload(String storageObjectId, FileRecord fileRecord, long fileSize) {
        uploadStorageReferenceMutationService.incrementReferenceCount(storageObjectId);
        uploadMetadataPersistenceService.saveFileRecord(fileRecord);
        uploadTenantUsageAccountingService.incrementUsage(fileRecord.getAppId(), fileSize);
    }
}
