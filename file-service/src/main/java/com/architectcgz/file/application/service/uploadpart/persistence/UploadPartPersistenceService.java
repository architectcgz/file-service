package com.architectcgz.file.application.service.uploadpart.persistence;

import com.architectcgz.file.domain.model.UploadPart;
import com.architectcgz.file.domain.repository.UploadPartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 分片记录持久化服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadPartPersistenceService {

    private final UploadPartRepository uploadPartRepository;

    public void savePart(UploadPart part) {
        uploadPartRepository.savePart(part);
        log.debug("分片记录已保存: taskId={}, partNumber={}, etag={}",
                part.getTaskId(), part.getPartNumber(), part.getEtag());
    }
}
