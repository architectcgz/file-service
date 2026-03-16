package com.architectcgz.file.application.service.direct.query;

import com.architectcgz.file.application.dto.DirectUploadPartUrlRequest;
import com.architectcgz.file.application.dto.DirectUploadPartUrlResponse;
import com.architectcgz.file.application.service.direct.bridge.DirectUploadCoreBridgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectUploadPartUrlQueryService {

    private final DirectUploadCoreBridgeService directUploadCoreBridgeService;

    public DirectUploadPartUrlResponse getPartUploadUrls(String appId, DirectUploadPartUrlRequest request, String userId) {
        log.debug("获取分片上传URL: taskId={}, partNumbers={}", request.getTaskId(), request.getPartNumbers());
        return directUploadCoreBridgeService.getPartUploadUrls(appId, request, userId);
    }
}
