package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.InitUploadRequest;
import com.architectcgz.file.application.dto.InitUploadResponse;
import com.architectcgz.file.application.dto.UploadProgressResponse;
import com.architectcgz.file.application.service.multipart.command.MultipartPartUploadCommandService;
import com.architectcgz.file.application.service.multipart.command.MultipartUploadAbortCommandService;
import com.architectcgz.file.application.service.multipart.command.MultipartUploadCompleteCommandService;
import com.architectcgz.file.application.service.multipart.command.MultipartUploadInitCommandService;
import com.architectcgz.file.application.service.multipart.query.MultipartUploadProgressQueryService;
import com.architectcgz.file.application.service.multipart.query.MultipartUploadTaskQueryService;
import com.architectcgz.file.domain.model.UploadTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分片上传应用层门面。
 *
 * <p>为接口层收口 multipart upload 用例入口，
 * 具体实现拆分到 command/query service，避免单个 Service 承载全部职责。
 */
@Service
@RequiredArgsConstructor
public class MultipartUploadService {

    private final MultipartUploadInitCommandService multipartUploadInitCommandService;
    private final MultipartPartUploadCommandService multipartPartUploadCommandService;
    private final MultipartUploadCompleteCommandService multipartUploadCompleteCommandService;
    private final MultipartUploadAbortCommandService multipartUploadAbortCommandService;
    private final MultipartUploadProgressQueryService multipartUploadProgressQueryService;
    private final MultipartUploadTaskQueryService multipartUploadTaskQueryService;

    public InitUploadResponse initUpload(String appId, InitUploadRequest request, String userId) {
        return multipartUploadInitCommandService.initUpload(appId, request, userId);
    }

    public String uploadPart(String appId, String taskId, int partNumber, byte[] data, String userId) {
        return multipartPartUploadCommandService.uploadPart(appId, taskId, partNumber, data, userId);
    }

    public String completeUpload(String appId, String taskId, String userId) {
        return multipartUploadCompleteCommandService.completeUpload(appId, taskId, userId);
    }

    public void abortUpload(String appId, String taskId, String userId) {
        multipartUploadAbortCommandService.abortUpload(appId, taskId, userId);
    }

    public UploadProgressResponse getProgress(String appId, String taskId, String userId) {
        return multipartUploadProgressQueryService.getProgress(appId, taskId, userId);
    }

    public List<UploadTask> listTasks(String appId, String userId) {
        return multipartUploadTaskQueryService.listTasks(appId, userId);
    }
}
