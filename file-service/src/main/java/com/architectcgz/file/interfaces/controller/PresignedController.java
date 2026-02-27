package com.architectcgz.file.interfaces.controller;

import com.architectcgz.file.common.result.ApiResponse;
import com.architectcgz.file.application.dto.ConfirmUploadRequest;
import com.architectcgz.file.application.dto.PresignedUploadRequest;
import com.architectcgz.file.application.dto.PresignedUploadResponse;
import com.architectcgz.file.application.service.PresignedUrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 预签名 URL 控制器
 * 支持客户端直接上传文件到 S3
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
public class PresignedController {
    
    private final PresignedUrlService presignedUrlService;
    
    /**
     * 获取预签名上传 URL
     * 允许客户端直接上传文件到 S3，无需通过服务器中转
     * 
     * @param request 预签名上传请求
     * @param httpRequest HTTP请求对象
     * @param userId 用户 ID (从认证上下文获取)
     * @return 预签名上传响应
     */
    @PostMapping("/presign")
    public ApiResponse<PresignedUploadResponse> getPresignedUploadUrl(
            @Valid @RequestBody PresignedUploadRequest request,
            HttpServletRequest httpRequest,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        
        String appId = (String) httpRequest.getAttribute("appId");
        
        log.info("Get presigned URL - appId: {}, userId: {}, fileName: {}, fileSize: {}", 
                appId, userId, request.getFileName(), request.getFileSize());
        
        // TODO: 从 JWT Token 中获取真实的 userId
        // 目前使用请求头传递，生产环境应从 SecurityContext 获取
        if (userId == null) {
            userId = "1"; // 默认用户 ID，仅用于测试
        }
        
        PresignedUploadResponse response = presignedUrlService.getPresignedUploadUrl(appId, request, userId);
        
        log.info("Get presigned URL success - appId: {}, userId: {}, storagePath: {}", 
                appId, userId, response.getStoragePath());
        
        return ApiResponse.success(response);
    }
    
    /**
     * 确认上传完成
     * 客户端使用预签名 URL 上传完成后，调用此接口创建文件记录
     * 
     * @param request 确认上传请求
     * @param httpRequest HTTP请求对象
     * @param userId 用户 ID (从认证上下文获取)
     * @return 文件记录 ID 和访问 URL
     */
    @PostMapping("/confirm")
    public ApiResponse<ConfirmUploadResponse> confirmUpload(
            @Valid @RequestBody ConfirmUploadRequest request,
            HttpServletRequest httpRequest,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        
        String appId = (String) httpRequest.getAttribute("appId");
        
        log.info("Confirm upload - appId: {}, userId: {}, storagePath: {}", 
                appId, userId, request.getStoragePath());
        
        // TODO: 从 JWT Token 中获取真实的 userId
        // 目前使用请求头传递，生产环境应从 SecurityContext 获取
        if (userId == null) {
            userId = "1"; // 默认用户 ID，仅用于测试
        }
        
        Map<String, String> result = presignedUrlService.confirmUpload(appId, request, userId);
        
        ConfirmUploadResponse response = new ConfirmUploadResponse(
                result.get("fileId"),
                result.get("url")
        );
        
        log.info("Confirm upload success - appId: {}, userId: {}, fileId: {}", 
                appId, userId, response.getFileId());
        
        return ApiResponse.success(response);
    }
    
    /**
     * 确认上传响应
     */
    @Data
    @AllArgsConstructor
    public static class ConfirmUploadResponse {
        /**
         * 文件记录 ID
         */
        private String fileId;
        
        /**
         * 文件访问 URL
         */
        private String url;
    }
}
