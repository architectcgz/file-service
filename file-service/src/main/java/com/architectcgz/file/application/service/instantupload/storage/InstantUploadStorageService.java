package com.architectcgz.file.application.service.instantupload.storage;

import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.infrastructure.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 秒传存储协调服务。
 */
@Service
@RequiredArgsConstructor
public class InstantUploadStorageService {

    private final StorageObjectRepository storageObjectRepository;
    private final StorageService storageService;

    public String resolveFileUrl(FileRecord fileRecord) {
        return storageObjectRepository.findById(fileRecord.getStorageObjectId())
                .map(storageObject -> storageService.getPublicUrl(storageObject.getBucketName(), storageObject.getStoragePath()))
                .orElseGet(() -> storageService.getPublicUrl(fileRecord.getStoragePath()));
    }

    public String buildPublicUrl(StorageObject storageObject) {
        return storageService.getPublicUrl(storageObject.getBucketName(), storageObject.getStoragePath());
    }
}
