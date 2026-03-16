package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.FileUrlResponse;
import com.architectcgz.file.application.service.fileaccess.bridge.FileAccessCoreBridgeService;
import com.architectcgz.file.domain.model.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 文件访问应用层门面。
 */
@Service
@RequiredArgsConstructor
public class FileAccessService {

    private final FileAccessCoreBridgeService fileAccessCoreBridgeService;

    public FileUrlResponse getFileUrl(String appId, String fileId, String requestUserId) {
        return fileAccessCoreBridgeService.getFileUrl(appId, fileId, requestUserId);
    }

    public void updateAccessLevel(String appId, String fileId, String requestUserId, AccessLevel newLevel) {
        fileAccessCoreBridgeService.updateAccessLevel(appId, fileId, requestUserId, newLevel);
    }
}
