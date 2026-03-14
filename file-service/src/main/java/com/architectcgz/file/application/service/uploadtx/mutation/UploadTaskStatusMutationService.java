package com.architectcgz.file.application.service.uploadtx.mutation;

import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.architectcgz.file.domain.repository.UploadTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 上传任务状态变更服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadTaskStatusMutationService {

    private final UploadTaskRepository uploadTaskRepository;

    public void markCompleted(String taskId) {
        uploadTaskRepository.updateStatus(taskId, UploadTaskStatus.COMPLETED);
        log.debug("UploadTask marked completed: taskId={}", taskId);
    }
}
