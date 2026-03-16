package com.architectcgz.file.application.service.multipart.command;

import com.architectcgz.file.application.service.multipart.bridge.MultipartUploadCoreBridgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultipartUploadCompleteCommandService {

    private final MultipartUploadCoreBridgeService multipartUploadCoreBridgeService;

    public String completeUpload(String appId, String taskId, String userId) {
        log.debug("Completing multipart upload for task: {}", taskId);
        return multipartUploadCoreBridgeService.completeUpload(appId, taskId, userId);
    }
}
