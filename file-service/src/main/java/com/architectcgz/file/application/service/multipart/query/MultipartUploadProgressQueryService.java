package com.architectcgz.file.application.service.multipart.query;

import com.architectcgz.file.application.dto.UploadProgressResponse;
import com.architectcgz.file.application.service.multipart.bridge.MultipartUploadCoreBridgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MultipartUploadProgressQueryService {

    private final MultipartUploadCoreBridgeService multipartUploadCoreBridgeService;

    public UploadProgressResponse getProgress(String appId, String taskId, String userId) {
        return multipartUploadCoreBridgeService.getProgress(appId, taskId, userId);
    }
}
