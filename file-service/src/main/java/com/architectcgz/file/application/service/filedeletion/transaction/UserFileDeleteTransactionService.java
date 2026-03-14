package com.architectcgz.file.application.service.filedeletion.transaction;

import com.architectcgz.file.application.service.filedeletion.FileDeleteTransactionSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserFileDeleteTransactionService {

    private final FileDeleteTransactionSupport fileDeleteTransactionSupport;

    @Transactional(rollbackFor = Exception.class)
    public void commitUserDelete(String appId, String fileRecordId,
                                 String storageObjectId, Long fileSize) {
        fileDeleteTransactionSupport.markFileDeleted(fileRecordId);
        fileDeleteTransactionSupport.decrementTenantUsage(appId, fileSize);
        fileDeleteTransactionSupport.commitStorageRelease(storageObjectId);
    }
}
