package com.architectcgz.file.application.service;

import com.architectcgz.file.application.service.fileaccess.transaction.AccessLevelOnlyTransactionService;
import com.architectcgz.file.application.service.fileaccess.transaction.AccessLevelStorageRebindTransactionService;
import com.architectcgz.file.application.service.fileaccess.transaction.AccessLevelTransactionSupport;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 文件访问级别切换事务门面。
 *
 * 对外保留原有 helper 名称，内部事务逻辑下沉到更细的 transaction service。
 */
@Component
public class AccessLevelChangeTransactionHelper {

    private final AccessLevelOnlyTransactionService accessLevelOnlyTransactionService;
    private final AccessLevelStorageRebindTransactionService accessLevelStorageRebindTransactionService;

    @Autowired
    public AccessLevelChangeTransactionHelper(AccessLevelOnlyTransactionService accessLevelOnlyTransactionService,
                                              AccessLevelStorageRebindTransactionService accessLevelStorageRebindTransactionService) {
        this.accessLevelOnlyTransactionService = accessLevelOnlyTransactionService;
        this.accessLevelStorageRebindTransactionService = accessLevelStorageRebindTransactionService;
    }

    AccessLevelChangeTransactionHelper(FileRecordRepository fileRecordRepository,
                                       StorageObjectRepository storageObjectRepository) {
        AccessLevelTransactionSupport support = new AccessLevelTransactionSupport(
                fileRecordRepository,
                storageObjectRepository
        );
        this.accessLevelOnlyTransactionService = new AccessLevelOnlyTransactionService(support);
        this.accessLevelStorageRebindTransactionService = new AccessLevelStorageRebindTransactionService(support);
    }

    public void updateAccessLevelOnly(String fileId, AccessLevel newLevel) {
        accessLevelOnlyTransactionService.updateAccessLevelOnly(fileId, newLevel);
    }

    public void rebindToCopiedStorage(String fileId, String sourceStorageObjectId,
                                      StorageObject copiedStorageObject, AccessLevel newLevel) {
        accessLevelStorageRebindTransactionService.rebindToCopiedStorage(
                fileId,
                sourceStorageObjectId,
                copiedStorageObject,
                newLevel
        );
    }
}
