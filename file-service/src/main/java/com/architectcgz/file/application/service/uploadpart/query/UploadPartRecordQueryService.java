package com.architectcgz.file.application.service.uploadpart.query;

import com.architectcgz.file.domain.model.UploadPart;
import com.architectcgz.file.domain.repository.UploadPartRepository;
import com.architectcgz.file.infrastructure.repository.mapper.UploadPartMapper;
import com.architectcgz.file.infrastructure.repository.po.UploadPartPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 分片记录查询服务。
 */
@Service
@RequiredArgsConstructor
public class UploadPartRecordQueryService {

    private final UploadPartRepository uploadPartRepository;
    private final UploadPartMapper uploadPartMapper;

    public int countCompletedParts(String taskId) {
        return uploadPartRepository.countCompletedParts(taskId);
    }

    public List<Integer> findCompletedPartNumbers(String taskId) {
        return uploadPartRepository.findCompletedPartNumbers(taskId);
    }

    public Optional<UploadPart> findUploadedPart(String taskId, int partNumber) {
        return uploadPartRepository.findByTaskIdAndPartNumber(taskId, partNumber);
    }

    public List<UploadPartPO> findPersistedParts(String taskId) {
        return uploadPartMapper.selectByTaskId(taskId);
    }
}
