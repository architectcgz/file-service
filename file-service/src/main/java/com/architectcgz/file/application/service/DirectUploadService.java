package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.DirectUploadCompleteRequest;
import com.architectcgz.file.application.dto.DirectUploadInitRequest;
import com.architectcgz.file.application.dto.DirectUploadInitResponse;
import com.architectcgz.file.application.dto.DirectUploadPartUrlRequest;
import com.architectcgz.file.application.dto.DirectUploadPartUrlResponse;
import com.architectcgz.file.application.dto.DirectUploadProgressResponse;
import com.architectcgz.file.application.service.direct.command.DirectUploadCompleteCommandService;
import com.architectcgz.file.application.service.direct.command.DirectUploadInitCommandService;
import com.architectcgz.file.application.service.direct.query.DirectUploadPartUrlQueryService;
import com.architectcgz.file.application.service.direct.query.DirectUploadProgressQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * S3 直传服务
 * 
 * 提供客户端直接上传到 S3 的功能，减轻服务器带宽压力
 * 支持断点续传和分片上传
 */
@Service
@RequiredArgsConstructor
public class DirectUploadService {
    private final DirectUploadInitCommandService directUploadInitCommandService;
    private final DirectUploadProgressQueryService directUploadProgressQueryService;
    private final DirectUploadPartUrlQueryService directUploadPartUrlQueryService;
    private final DirectUploadCompleteCommandService directUploadCompleteCommandService;

    public DirectUploadInitResponse initDirectUpload(String appId, DirectUploadInitRequest request, String userId) {
        return directUploadInitCommandService.initDirectUpload(appId, request, userId);
    }

    public DirectUploadProgressResponse getUploadProgress(String appId, String taskId, String userId) {
        return directUploadProgressQueryService.getUploadProgress(appId, taskId, userId);
    }

    public DirectUploadPartUrlResponse getPartUploadUrls(String appId, DirectUploadPartUrlRequest request, String userId) {
        return directUploadPartUrlQueryService.getPartUploadUrls(appId, request, userId);
    }

    public String completeDirectUpload(String appId, DirectUploadCompleteRequest request, String userId) {
        return directUploadCompleteCommandService.completeDirectUpload(appId, request, userId);
    }
}
