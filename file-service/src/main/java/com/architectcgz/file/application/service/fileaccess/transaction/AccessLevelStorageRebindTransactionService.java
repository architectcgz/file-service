package com.architectcgz.file.application.service.fileaccess.transaction;

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

    private final AccessLevelTransactionSupport accessLevelTransactionSupport;

    @Transactional(rollbackFor = Exception.class)
    public void rebindToCopiedStorage(String fileId, String sourceStorageObjectId,
                                      StorageObject copiedStorageObject, AccessLevel newLevel) {
        accessLevelTransactionSupport.saveCopiedStorageObject(copiedStorageObject);
        accessLevelTransactionSupport.updateStorageBindingOrThrow(fileId, copiedStorageObject, newLevel);
        accessLevelTransactionSupport.decrementReferenceCountOrThrow(sourceStorageObjectId);

        log.debug("Rebound file to copied storage: fileId={}, oldStorageObjectId={}, newStorageObjectId={}, newLevel={}",
                fileId, sourceStorageObjectId, copiedStorageObject.getId(), newLevel);
    }
}
