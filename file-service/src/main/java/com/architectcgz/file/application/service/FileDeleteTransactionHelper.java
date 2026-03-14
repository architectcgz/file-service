package com.architectcgz.file.application.service;

import com.architectcgz.file.application.service.filedeletion.FileDeleteTransactionSupport;
import com.architectcgz.file.application.service.filedeletion.query.StorageObjectLastReferenceQueryService;
import com.architectcgz.file.application.service.filedeletion.transaction.AdminFileDeleteTransactionService;
import com.architectcgz.file.application.service.filedeletion.transaction.UserFileDeleteTransactionService;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 文件删除事务门面。
 *
 * 对外保留原有 helper 名称和方法签名，内部拆分为查询与短事务服务。
 */
@Service
public class FileDeleteTransactionHelper {

    private final StorageObjectLastReferenceQueryService storageObjectLastReferenceQueryService;
    private final UserFileDeleteTransactionService userFileDeleteTransactionService;
    private final AdminFileDeleteTransactionService adminFileDeleteTransactionService;

    @Autowired
    public FileDeleteTransactionHelper(StorageObjectLastReferenceQueryService storageObjectLastReferenceQueryService,
                                       UserFileDeleteTransactionService userFileDeleteTransactionService,
                                       AdminFileDeleteTransactionService adminFileDeleteTransactionService) {
        this.storageObjectLastReferenceQueryService = storageObjectLastReferenceQueryService;
        this.userFileDeleteTransactionService = userFileDeleteTransactionService;
        this.adminFileDeleteTransactionService = adminFileDeleteTransactionService;
    }

    FileDeleteTransactionHelper(FileRecordRepository fileRecordRepository,
                                StorageObjectRepository storageObjectRepository,
                                TenantUsageRepository tenantUsageRepository) {
        FileDeleteTransactionSupport support = new FileDeleteTransactionSupport(
                fileRecordRepository,
                storageObjectRepository,
                tenantUsageRepository
        );
        this.storageObjectLastReferenceQueryService = new StorageObjectLastReferenceQueryService(support);
        this.userFileDeleteTransactionService = new UserFileDeleteTransactionService(support);
        this.adminFileDeleteTransactionService = new AdminFileDeleteTransactionService(support);
    }

    public Optional<StorageObject> findStorageObjectIfLastReference(String storageObjectId) {
        return storageObjectLastReferenceQueryService.findStorageObjectIfLastReference(storageObjectId);
    }

    public void commitUserDelete(String appId, String fileRecordId,
                                 String storageObjectId, Long fileSize) {
        userFileDeleteTransactionService.commitUserDelete(appId, fileRecordId, storageObjectId, fileSize);
    }

    public void commitAdminDelete(String fileId, FileRecord fileRecord) {
        adminFileDeleteTransactionService.commitAdminDelete(fileId, fileRecord);
    }
}
