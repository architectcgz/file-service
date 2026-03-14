package com.architectcgz.file.application.service.uploadtx.persistence;

import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 上传元数据持久化服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadMetadataPersistenceService {

    private final StorageObjectRepository storageObjectRepository;
    private final FileRecordRepository fileRecordRepository;

    public void saveStorageObject(StorageObject storageObject) {
        storageObjectRepository.save(storageObject);
        log.debug("StorageObject saved: id={}, path={}", storageObject.getId(), storageObject.getStoragePath());
    }

    public void saveFileRecord(FileRecord fileRecord) {
        fileRecordRepository.save(fileRecord);
        log.debug("FileRecord saved: id={}, storageObjectId={}", fileRecord.getId(), fileRecord.getStorageObjectId());
    }
}
