package com.architectcgz.file.application.service.multipart.command;

import com.architectcgz.file.application.service.multipart.bridge.MultipartUploadCoreBridgeService;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultipartUploadAbortCommandService {

    private final MultipartUploadCoreBridgeService multipartUploadCoreBridgeService;

    public void abortUpload(String appId, String taskId, String userId) {
        log.debug("Aborting multipart upload for task: {}", taskId);
        multipartUploadCoreBridgeService.abortUpload(appId, taskId, userId);
    }
}
