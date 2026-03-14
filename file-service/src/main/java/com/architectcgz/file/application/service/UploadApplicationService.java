package com.architectcgz.file.application.service;

import com.architectcgz.file.application.service.upload.command.FileDeleteCommandService;
import com.architectcgz.file.application.service.upload.command.FileUploadCommandService;
import com.architectcgz.file.application.service.upload.command.ImageUploadCommandService;
import com.architectcgz.file.interfaces.dto.UploadResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传应用服务
 * 
 * 使用 @ConfigurationProperties 支持配置动态刷新
 */
@Service
@RequiredArgsConstructor
public class UploadApplicationService {
    private final ImageUploadCommandService imageUploadCommandService;
    private final FileUploadCommandService fileUploadCommandService;
    private final FileDeleteCommandService fileDeleteCommandService;

    public UploadResult uploadImage(String appId, MultipartFile file, String userId) {
        return imageUploadCommandService.uploadImage(appId, file, userId);
    }
    public UploadResult uploadFile(String appId, MultipartFile file, String userId) {
        return fileUploadCommandService.uploadFile(appId, file, userId);
    }

    public void deleteFile(String appId, String fileRecordId, String userId) {
        fileDeleteCommandService.deleteFile(appId, fileRecordId, userId);
    }
}
