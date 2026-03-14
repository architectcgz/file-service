package com.architectcgz.file.application.service.filemanagement.deletion;

import com.architectcgz.file.application.service.FileDeleteTransactionHelper;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.infrastructure.cache.FileUrlCacheManager;
import com.architectcgz.file.infrastructure.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 文件管理删除执行服务。
 */
@Service
@RequiredArgsConstructor
public class FileManagementDeletionService {

    private final StorageService storageService;
    private final FileUrlCacheManager fileUrlCacheManager;
    private final FileDeleteTransactionHelper fileDeleteTransactionHelper;

    public Optional<StorageObject> findStorageObjectIfLastReference(String storageObjectId) {
        return fileDeleteTransactionHelper.findStorageObjectIfLastReference(storageObjectId);
    }

    public void deleteStorageObject(StorageObject storageObject) {
        storageService.delete(storageObject.getBucketName(), storageObject.getStoragePath());
    }

    public void commitAdminDelete(String fileId, FileRecord fileRecord) {
        fileDeleteTransactionHelper.commitAdminDelete(fileId, fileRecord);
    }

    public void evictFileUrlCache(String fileId) {
        fileUrlCacheManager.evict(fileId);
    }
}
