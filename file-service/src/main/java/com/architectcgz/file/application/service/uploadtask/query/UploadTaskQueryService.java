package com.architectcgz.file.application.service.uploadtask.query;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.repository.UploadTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 上传任务查询服务。
 */
@Service
@RequiredArgsConstructor
public class UploadTaskQueryService {

    private final UploadTaskRepository uploadTaskRepository;

    public UploadTask getById(String taskId) {
        return uploadTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(
                        FileServiceErrorCodes.UPLOAD_TASK_NOT_FOUND,
                        FileServiceErrorMessages.UPLOAD_TASK_NOT_FOUND
                ));
    }

    public Optional<UploadTask> findByUserIdAndFileHash(String appId, String userId, String fileHash) {
        return uploadTaskRepository.findByUserIdAndFileHash(appId, userId, fileHash);
    }

    public List<UploadTask> listByUserId(String appId, String userId, int limit) {
        return uploadTaskRepository.findByUserId(appId, userId, limit);
    }
}
