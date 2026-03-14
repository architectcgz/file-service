package com.architectcgz.file.application.service.uploadtx.transaction;

import com.architectcgz.file.application.service.uploadtx.accounting.UploadTenantUsageAccountingService;
import com.architectcgz.file.application.service.uploadtx.mutation.UploadStorageReferenceMutationService;
import com.architectcgz.file.application.service.uploadtx.mutation.UploadTaskStatusMutationService;
import com.architectcgz.file.application.service.uploadtx.persistence.UploadMetadataPersistenceService;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.UploadTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompletedInstantUploadTransactionService {

    private final UploadStorageReferenceMutationService uploadStorageReferenceMutationService;
    private final UploadMetadataPersistenceService uploadMetadataPersistenceService;
    private final UploadTenantUsageAccountingService uploadTenantUsageAccountingService;
    private final UploadTaskStatusMutationService uploadTaskStatusMutationService;

    @Transactional(rollbackFor = Exception.class)
    public void saveCompletedInstantUpload(UploadTask task, String storageObjectId, FileRecord fileRecord) {
        uploadStorageReferenceMutationService.incrementReferenceCount(storageObjectId);
        uploadMetadataPersistenceService.saveFileRecord(fileRecord);
        uploadTenantUsageAccountingService.incrementUsage(task.getAppId(), fileRecord.getFileSize());
        uploadTaskStatusMutationService.markCompleted(task.getId());
    }
}
