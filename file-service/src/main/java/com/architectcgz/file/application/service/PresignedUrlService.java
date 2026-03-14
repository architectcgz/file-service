package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.ConfirmUploadRequest;
import com.architectcgz.file.application.dto.PresignedUploadRequest;
import com.architectcgz.file.application.dto.PresignedUploadResponse;
import com.architectcgz.file.application.service.presigned.command.PresignedUploadConfirmCommandService;
import com.architectcgz.file.application.service.presigned.query.PresignedUploadUrlQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 预签名上传应用层门面。
 *
 * 为接口层收口预签名直传的申请与确认入口，
 * 具体用例拆分到 command/query service。
 */
@Service
@RequiredArgsConstructor
public class PresignedUrlService {

    private final PresignedUploadUrlQueryService presignedUploadUrlQueryService;
    private final PresignedUploadConfirmCommandService presignedUploadConfirmCommandService;

    public PresignedUploadResponse getPresignedUploadUrl(String appId, PresignedUploadRequest request, String userId) {
        return presignedUploadUrlQueryService.getPresignedUploadUrl(appId, request, userId);
    }

    public Map<String, String> confirmUpload(String appId, ConfirmUploadRequest request, String userId) {
        return presignedUploadConfirmCommandService.confirmUpload(appId, request, userId);
    }
}
