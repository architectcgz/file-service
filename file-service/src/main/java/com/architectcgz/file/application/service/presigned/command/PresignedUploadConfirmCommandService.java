package com.architectcgz.file.application.service.presigned.command;

import com.architectcgz.file.application.dto.ConfirmUploadRequest;
import com.architectcgz.file.application.service.presigned.bridge.PresignedUploadCoreBridgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresignedUploadConfirmCommandService {

    private final PresignedUploadCoreBridgeService presignedUploadCoreBridgeService;

    public Map<String, String> confirmUpload(String appId, ConfirmUploadRequest request, String userId) {
        log.info("Confirming upload: userId={}, storagePath={}, fileHash={}",
                userId, request.getStoragePath(), request.getFileHash());
        return presignedUploadCoreBridgeService.confirmUpload(appId, request, userId);
    }
}
