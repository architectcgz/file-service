package com.architectcgz.file.application.service.direct.command;

import com.architectcgz.file.application.dto.DirectUploadCompleteRequest;
import com.architectcgz.file.application.service.direct.bridge.DirectUploadCoreBridgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectUploadCompleteCommandService {

    private final DirectUploadCoreBridgeService directUploadCoreBridgeService;

    public String completeDirectUpload(String appId, DirectUploadCompleteRequest request, String userId) {
        int requestPartCount = request.getParts() == null ? 0 : request.getParts().size();
        log.debug("完成直传上传: taskId={}, parts={}", request.getTaskId(), requestPartCount);
        return directUploadCoreBridgeService.completeDirectUpload(appId, request, userId);
    }
}
