package com.architectcgz.file.application.service.filedeletion.transaction;

import com.architectcgz.file.application.service.filedeletion.FileDeleteTransactionSupport;
import com.architectcgz.file.domain.model.FileRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminFileDeleteTransactionService {

    private final FileDeleteTransactionSupport fileDeleteTransactionSupport;

    @Transactional(rollbackFor = Exception.class)
    public void commitAdminDelete(String fileId, FileRecord fileRecord) {
        fileDeleteTransactionSupport.deleteFileRecordOrThrow(fileId);
        fileDeleteTransactionSupport.decrementTenantUsage(fileRecord.getAppId(), fileRecord.getFileSize());
        fileDeleteTransactionSupport.commitStorageRelease(
                fileDeleteTransactionSupport.resolveStorageObjectId(fileRecord)
        );
    }
}
