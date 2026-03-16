package com.architectcgz.file.application.service.multipart.command;

import com.architectcgz.file.application.dto.InitUploadRequest;
import com.architectcgz.file.application.dto.InitUploadResponse;
import com.architectcgz.file.application.service.multipart.bridge.MultipartUploadCoreBridgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultipartUploadInitCommandService {

    private final MultipartUploadCoreBridgeService multipartUploadCoreBridgeService;

    public InitUploadResponse initUpload(String appId, InitUploadRequest request, String userId) {
        log.debug("Initializing multipart upload for user: {}, file: {}, size: {}",
                userId, request.getFileName(), request.getFileSize());
        return multipartUploadCoreBridgeService.initUpload(appId, request, userId);
    }
}
