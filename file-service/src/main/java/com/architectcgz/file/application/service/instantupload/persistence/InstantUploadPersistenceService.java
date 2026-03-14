package com.architectcgz.file.application.service.instantupload.persistence;

import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 秒传持久化服务。
 */
@Service
@RequiredArgsConstructor
public class InstantUploadPersistenceService {

    private final StorageObjectRepository storageObjectRepository;
    private final FileRecordRepository fileRecordRepository;

    public void incrementReferenceCount(String storageObjectId) {
        storageObjectRepository.incrementReferenceCount(storageObjectId);
    }

    public void saveFileRecord(FileRecord fileRecord) {
        fileRecordRepository.save(fileRecord);
    }
}
