package com.architectcgz.file.application.service.uploadpart.query;

import com.architectcgz.file.domain.model.UploadPart;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 分片状态查询服务。
 */
@Service
@RequiredArgsConstructor
public class UploadPartStateQueryService {

    private final UploadPartRecordQueryService uploadPartRecordQueryService;

    public int countCompletedParts(String taskId) {
        return uploadPartRecordQueryService.countCompletedParts(taskId);
    }

    public List<Integer> findCompletedPartNumbers(String taskId) {
        return uploadPartRecordQueryService.findCompletedPartNumbers(taskId);
    }

    public Optional<UploadPart> findUploadedPart(String taskId, int partNumber) {
        return uploadPartRecordQueryService.findUploadedPart(taskId, partNumber);
    }
}
