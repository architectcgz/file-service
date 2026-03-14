package com.architectcgz.file.application.service.uploadpart.query;

import com.architectcgz.file.application.service.uploadpart.assembler.UploadCompletedPartAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.util.List;

/**
 * 分片完成态查询服务。
 */
@Service
@RequiredArgsConstructor
public class UploadPartCompletionQueryService {

    private final UploadPartRecordQueryService uploadPartRecordQueryService;
    private final UploadCompletedPartAssembler uploadCompletedPartAssembler;

    public List<CompletedPart> loadPersistedCompletedParts(String taskId) {
        return uploadCompletedPartAssembler.toCompletedParts(
                uploadPartRecordQueryService.findPersistedParts(taskId)
        );
    }
}
