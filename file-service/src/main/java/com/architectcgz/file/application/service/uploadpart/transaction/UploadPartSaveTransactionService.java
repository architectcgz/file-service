package com.architectcgz.file.application.service.uploadpart.transaction;

import com.architectcgz.file.application.service.uploadpart.factory.UploadPartFactory;
import com.architectcgz.file.application.service.uploadpart.persistence.UploadPartPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UploadPartSaveTransactionService {

    private final UploadPartFactory uploadPartFactory;
    private final UploadPartPersistenceService uploadPartPersistenceService;

    @Transactional
    public void savePart(String taskId, int partNumber, String etag, long size) {
        uploadPartPersistenceService.savePart(
                uploadPartFactory.createUploadPart(taskId, partNumber, etag, size)
        );
    }
}
