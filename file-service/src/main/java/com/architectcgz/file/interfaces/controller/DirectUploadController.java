package com.architectcgz.file.interfaces.controller;

import com.architectcgz.file.application.dto.DirectUploadCompleteRequest;
import com.architectcgz.file.application.dto.DirectUploadInitRequest;
import com.architectcgz.file.application.dto.DirectUploadInitResponse;
import com.architectcgz.file.application.dto.DirectUploadPartUrlRequest;
import com.architectcgz.file.application.dto.DirectUploadPartUrlResponse;
import com.architectcgz.file.application.service.DirectUploadService;
import com.architectcgz.file.common.result.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * S3 直传上传控制器
 * 
 * 提供客户端直接上传到 S3 的 API
 * 支持断点续传和分片上传
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/direct-upload")
@RequiredArgsConstructor
public class DirectUploadController {
    
    private final DirectUploadService directUploadService;
    
    /**
     * 初始化直传上传
     * 
     * 客户端调用此接口获取上传任务信息和 uploadId
     * 
     * @param appId 应用ID
     * @param userId 用户ID
     * @param request 初始化请求
     * @return 初始化响应
     */
    @PostMapping("/init")
    public ApiResponse<DirectUploadInitResponse> initUpload(
            @RequestHeader("X-App-Id") String appId,
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody DirectUploadInitRequest request) {
        
        log.info("初始化直传上传: appId={}, userId={}, fileName={}", 
                appId, userId, request.getFileName());
        
        DirectUploadInitResponse response = directUploadService.initDirectUpload(appId, request, userId);
        
        return ApiResponse.success(response);
    }
    
    /**
     * 获取分片上传 URL
     * 
     * 客户端调用此接口获取预签名 URL，然后直接上传分片到 S3
     * 
     * @param userId 用户ID
     * @param request 请求参数
     * @return 预签名 URL 列表
     */
    @PostMapping("/part-urls")
    public ApiResponse<DirectUploadPartUrlResponse> getPartUploadUrls(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody DirectUploadPartUrlRequest request) {
        
        log.info("获取分片上传URL: userId={}, taskId={}, partCount={}", 
                userId, request.getTaskId(), request.getPartNumbers().size());
        
        DirectUploadPartUrlResponse response = directUploadService.getPartUploadUrls(request, userId);
        
        return ApiResponse.success(response);
    }
    
    /**
     * 完成直传上传
     * 
     * 客户端上传完所有分片后，调用此接口完成上传
     * 
     * @param userId 用户ID
     * @param request 完成请求
     * @return 文件记录ID
     */
    @PostMapping("/complete")
    public ApiResponse<String> completeUpload(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody DirectUploadCompleteRequest request) {
        
        log.info("完成直传上传: userId={}, taskId={}", userId, request.getTaskId());
        
        String fileId = directUploadService.completeDirectUpload(request, userId);
        
        return ApiResponse.success(fileId);
    }
}
