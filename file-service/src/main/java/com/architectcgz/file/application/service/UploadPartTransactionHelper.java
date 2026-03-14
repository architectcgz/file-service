package com.architectcgz.file.application.service;

import com.architectcgz.file.application.service.uploadpart.factory.UploadPartFactory;
import com.architectcgz.file.application.service.uploadpart.persistence.UploadPartPersistenceService;
import com.architectcgz.file.application.service.uploadpart.transaction.UploadPartSaveTransactionService;
import com.architectcgz.file.domain.repository.UploadPartRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 分片上传事务门面。
 *
 * 对外保留保存分片记录入口，内部事务逻辑下沉到 uploadpart/transaction。
 */
@Component
public class UploadPartTransactionHelper {

    private final UploadPartSaveTransactionService uploadPartSaveTransactionService;

    @Autowired
    public UploadPartTransactionHelper(UploadPartSaveTransactionService uploadPartSaveTransactionService) {
        this.uploadPartSaveTransactionService = uploadPartSaveTransactionService;
    }

    UploadPartTransactionHelper(UploadPartRepository uploadPartRepository) {
        this(new UploadPartSaveTransactionService(
                new UploadPartFactory(),
                new UploadPartPersistenceService(uploadPartRepository)
        ));
    }

    public void savePart(String taskId, int partNumber, String etag, long size) {
        uploadPartSaveTransactionService.savePart(taskId, partNumber, etag, size);
    }
}
