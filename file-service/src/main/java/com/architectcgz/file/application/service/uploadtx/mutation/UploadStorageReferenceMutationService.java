package com.architectcgz.file.application.service.uploadtx.mutation;

import com.architectcgz.file.domain.repository.StorageObjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 上传存储对象引用变更服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadStorageReferenceMutationService {

    private final StorageObjectRepository storageObjectRepository;

    public void incrementReferenceCount(String storageObjectId) {
        storageObjectRepository.incrementReferenceCount(storageObjectId);
        log.debug("StorageObject reference count incremented: id={}", storageObjectId);
    }
}
