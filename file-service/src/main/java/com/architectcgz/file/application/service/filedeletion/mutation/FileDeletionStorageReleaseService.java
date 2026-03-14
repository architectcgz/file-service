package com.architectcgz.file.application.service.filedeletion.mutation;

import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 文件删除相关的存储对象释放服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileDeletionStorageReleaseService {

    private final StorageObjectRepository storageObjectRepository;

    public void release(String storageObjectId) {
        storageObjectRepository.decrementReferenceCount(storageObjectId);
        deleteIfReleasable(storageObjectId);
    }

    private void deleteIfReleasable(String storageObjectId) {
        Optional<StorageObject> updatedOpt = storageObjectRepository.findById(storageObjectId);
        if (updatedOpt.isPresent() && updatedOpt.get().canBeDeleted()) {
            storageObjectRepository.deleteById(storageObjectId);
            log.info("引用计数归零，StorageObject 记录已删除: storageObjectId={}", storageObjectId);
        }
    }
}
