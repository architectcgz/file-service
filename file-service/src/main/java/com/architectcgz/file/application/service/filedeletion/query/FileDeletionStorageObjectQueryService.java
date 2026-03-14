package com.architectcgz.file.application.service.filedeletion.query;

import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 文件删除相关的存储对象查询服务。
 */
@Service
@RequiredArgsConstructor
public class FileDeletionStorageObjectQueryService {

    private final StorageObjectRepository storageObjectRepository;

    public Optional<StorageObject> findStorageObjectIfLastReference(String storageObjectId) {
        return storageObjectRepository.findById(storageObjectId)
                .filter(StorageObject::isLastReference);
    }
}
