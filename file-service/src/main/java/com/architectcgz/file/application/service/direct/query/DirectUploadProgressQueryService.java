package com.architectcgz.file.application.service.direct.query;

import com.architectcgz.file.application.dto.DirectUploadProgressResponse;
import com.architectcgz.file.application.service.direct.bridge.DirectUploadCoreBridgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DirectUploadProgressQueryService {

    private final DirectUploadCoreBridgeService directUploadCoreBridgeService;

    public DirectUploadProgressResponse getUploadProgress(String appId, String taskId, String userId) {
        return directUploadCoreBridgeService.getUploadProgress(appId, taskId, userId);
    }
}
