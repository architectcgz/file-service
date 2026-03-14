package com.architectcgz.file.application.service.fileaccess.query;

import com.architectcgz.file.common.exception.FileNotFoundException;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 文件访问记录查询服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileAccessRecordQueryService {

    private final FileRecordRepository fileRecordRepository;
    private final StorageObjectRepository storageObjectRepository;

    public FileRecord findFileOrThrow(String fileId) {
        return fileRecordRepository.findById(fileId)
                .orElseThrow(() -> FileNotFoundException.notFound(fileId));
    }

    public Optional<StorageObject> resolveStorageObject(FileRecord file) {
        if (!StringUtils.hasText(file.getStorageObjectId())) {
            return Optional.empty();
        }
        return storageObjectRepository.findById(file.getStorageObjectId());
    }

    public String resolveBucketName(FileRecord file) {
        if (!StringUtils.hasText(file.getStorageObjectId())) {
            return null;
        }
        return storageObjectRepository.findById(file.getStorageObjectId())
                .map(StorageObject::getBucketName)
                .orElseGet(() -> {
                    log.warn("StorageObject not found when resolving bucket, fallback to default bucket: fileId={}, storageObjectId={}",
                            file.getId(), file.getStorageObjectId());
                    return null;
                });
    }
}
