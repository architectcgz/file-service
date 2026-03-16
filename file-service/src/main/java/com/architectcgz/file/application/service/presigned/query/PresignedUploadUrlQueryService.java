package com.architectcgz.file.application.service.presigned.query;

import com.architectcgz.file.application.dto.PresignedUploadRequest;
import com.architectcgz.file.application.dto.PresignedUploadResponse;
import com.architectcgz.file.application.service.presigned.bridge.PresignedUploadCoreBridgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresignedUploadUrlQueryService {

    private final PresignedUploadCoreBridgeService presignedUploadCoreBridgeService;

    public PresignedUploadResponse getPresignedUploadUrl(String appId, PresignedUploadRequest request, String userId) {
        log.info("Generating presigned upload URL: userId={}, fileName={}, fileSize={}, fileHash={}",
                userId, request.getFileName(), request.getFileSize(), request.getFileHash());
        return presignedUploadCoreBridgeService.getPresignedUploadUrl(appId, request, userId);
    }
}
