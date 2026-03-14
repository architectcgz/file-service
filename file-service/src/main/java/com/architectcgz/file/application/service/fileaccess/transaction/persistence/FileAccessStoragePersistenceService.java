package com.architectcgz.file.application.service.fileaccess.transaction.persistence;

import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文件访问级别切换相关的存储对象持久化服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileAccessStoragePersistenceService {

    private final StorageObjectRepository storageObjectRepository;

    public void saveCopiedStorageObject(StorageObject copiedStorageObject) {
        storageObjectRepository.save(copiedStorageObject);
        log.debug("Saved copied storage object: newStorageObjectId={}, bucket={}",
                copiedStorageObject.getId(), copiedStorageObject.getBucketName());
    }
}
