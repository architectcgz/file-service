package com.architectcgz.file.application.service.fileaccess.transaction;

import com.architectcgz.file.application.service.fileaccess.transaction.mutation.FileAccessRecordMutationService;
import com.architectcgz.file.application.service.fileaccess.transaction.mutation.FileAccessStorageReferenceMutationService;
import com.architectcgz.file.application.service.fileaccess.transaction.persistence.FileAccessStoragePersistenceService;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.StorageObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessLevelStorageRebindTransactionService {

    private final FileAccessStoragePersistenceService fileAccessStoragePersistenceService;
    private final FileAccessRecordMutationService fileAccessRecordMutationService;
    private final FileAccessStorageReferenceMutationService fileAccessStorageReferenceMutationService;

    @Transactional(rollbackFor = Exception.class)
    public void rebindToCopiedStorage(String fileId, String sourceStorageObjectId,
                                      StorageObject copiedStorageObject, AccessLevel newLevel) {
        fileAccessStoragePersistenceService.saveCopiedStorageObject(copiedStorageObject);
        fileAccessRecordMutationService.updateStorageBindingOrThrow(fileId, copiedStorageObject, newLevel);
        fileAccessStorageReferenceMutationService.decrementReferenceCountOrThrow(sourceStorageObjectId);

        log.debug("Rebound file to copied storage: fileId={}, oldStorageObjectId={}, newStorageObjectId={}, newLevel={}",
                fileId, sourceStorageObjectId, copiedStorageObject.getId(), newLevel);
    }
}
