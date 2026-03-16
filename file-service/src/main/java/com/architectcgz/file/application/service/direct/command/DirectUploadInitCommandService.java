package com.architectcgz.file.application.service.direct.command;

import com.architectcgz.file.application.dto.DirectUploadInitRequest;
import com.architectcgz.file.application.dto.DirectUploadInitResponse;
import com.architectcgz.file.application.service.FileTypeValidator;
import com.architectcgz.file.application.service.direct.bridge.DirectUploadCoreBridgeService;
import com.architectcgz.file.domain.service.TenantDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectUploadInitCommandService {

    private final FileTypeValidator fileTypeValidator;
    private final TenantDomainService tenantDomainService;
    private final DirectUploadCoreBridgeService directUploadCoreBridgeService;

    public DirectUploadInitResponse initDirectUpload(String appId, DirectUploadInitRequest request, String userId) {
        log.debug("初始化直传上传: appId={}, userId={}, fileName={}, fileSize={}, fileHash={}",
                appId, userId, request.getFileName(), request.getFileSize(), request.getFileHash());

        tenantDomainService.validateUploadPrerequisites(appId, request.getFileSize());
        fileTypeValidator.validateFile(
                request.getFileName(),
                request.getContentType(),
                request.getFileSize()
        );
        return directUploadCoreBridgeService.initDirectUpload(appId, request, userId);
    }
}
