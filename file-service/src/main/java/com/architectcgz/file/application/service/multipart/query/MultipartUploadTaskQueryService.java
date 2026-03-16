package com.architectcgz.file.application.service.multipart.query;

import com.architectcgz.file.application.service.multipart.bridge.MultipartUploadCoreBridgeService;
import com.architectcgz.file.domain.model.UploadTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MultipartUploadTaskQueryService {

    private final MultipartUploadCoreBridgeService multipartUploadCoreBridgeService;

    public List<UploadTask> listTasks(String appId, String userId) {
        return multipartUploadCoreBridgeService.listTasks(appId, userId);
    }
}
