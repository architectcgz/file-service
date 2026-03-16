package com.architectcgz.file.application.service.multipart.command;

import com.architectcgz.file.application.service.multipart.bridge.MultipartUploadCoreBridgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultipartPartUploadCommandService {

    private final MultipartUploadCoreBridgeService multipartUploadCoreBridgeService;

    public String uploadPart(String appId, String taskId, int partNumber, byte[] data, String userId) {
        log.debug("Uploading part {} for task: {}", partNumber, taskId);
        return multipartUploadCoreBridgeService.uploadPart(appId, taskId, partNumber, data, userId);
    }
}
