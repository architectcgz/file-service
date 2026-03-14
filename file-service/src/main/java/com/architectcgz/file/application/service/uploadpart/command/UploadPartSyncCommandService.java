package com.architectcgz.file.application.service.uploadpart.command;

import com.architectcgz.file.domain.model.UploadPart;
import com.architectcgz.file.domain.repository.UploadPartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 分片状态同步服务。
 */
@Service
@RequiredArgsConstructor
public class UploadPartSyncCommandService {

    private final UploadPartRepository uploadPartRepository;

    @Transactional
    public void syncAllParts(String taskId, List<UploadPart> parts) {
        uploadPartRepository.syncAllPartsToDatabase(taskId, parts);
    }
}
