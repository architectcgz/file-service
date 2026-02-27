package com.architectcgz.file.interfaces.controller;

import com.architectcgz.file.common.result.ApiResponse;
import com.architectcgz.file.application.dto.InitUploadRequest;
import com.architectcgz.file.application.dto.InitUploadResponse;
import com.architectcgz.file.application.dto.UploadProgressResponse;
import com.architectcgz.file.application.service.MultipartUploadService;
import com.architectcgz.file.domain.model.UploadTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;

/**
 * Multipart upload controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/multipart")
@RequiredArgsConstructor
public class MultipartController {
    
    private final MultipartUploadService multipartUploadService;
    
    /**
     * 初始化分片上传
     * 
     * @param request 初始化请求
     * @param httpRequest HTTP请求对象
     * @param userId 用户 ID (从认证上下文获取)
     * @return 初始化响应
     */
    @PostMapping("/init")
    public ApiResponse<InitUploadResponse> initUpload(
            @Valid @RequestBody InitUploadRequest request,
            HttpServletRequest httpRequest,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        
        String appId = (String) httpRequest.getAttribute("appId");
        
        log.info("Init multipart upload - appId: {}, userId: {}, fileName: {}, fileSize: {}", 
                appId, userId, request.getFileName(), request.getFileSize());
        
        // TODO: 从 JWT Token 中获取真实的 userId
        // 目前使用请求头传递，生产环境应从 SecurityContext 获取
        if (userId == null) {
            userId = "1"; // 默认用户 ID，仅用于测试
        }
        
        InitUploadResponse response = multipartUploadService.initUpload(appId, request, userId);
        
        log.info("Init multipart upload success - appId: {}, userId: {}, taskId: {}", 
                appId, userId, response.getTaskId());
        
        return ApiResponse.success(response);
    }
    
    /**
     * 上传分片
     * 
     * @param taskId 任务 ID
     * @param partNumber 分片号(1-based)
     * @param data 分片数据（原始字节流）
     * @param httpRequest HTTP请求对象
     * @param userId 用户 ID (从认证上下文获取)
     * @return 分片 ETag
     */
    @PutMapping("/{taskId}/parts/{partNumber}")
    public ApiResponse<String> uploadPart(
            @PathVariable String taskId,
            @PathVariable int partNumber,
            @RequestBody byte[] data,
            HttpServletRequest httpRequest,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        
        String appId = (String) httpRequest.getAttribute("appId");
        
        log.info("Upload part - appId: {}, userId: {}, taskId: {}, partNumber: {}, size: {}", 
                appId, userId, taskId, partNumber, data.length);
        
        // TODO: 从 JWT Token 中获取真实的 userId
        if (userId == null) {
            userId = "1"; // 默认用户 ID，仅用于测试
        }
        
        String etag = multipartUploadService.uploadPart(taskId, partNumber, data, userId);
        
        return ApiResponse.success(etag);
    }
    
    /**
     * 完成分片上传
     * 
     * @param taskId 任务 ID
     * @param httpRequest HTTP请求对象
     * @param userId 用户 ID (从认证上下文获取)
     * @return 文件记录 ID 和 URL
     */
    @PostMapping("/{taskId}/complete")
    public ApiResponse<CompleteUploadResponse> completeUpload(
            @PathVariable String taskId,
            HttpServletRequest httpRequest,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        
        String appId = (String) httpRequest.getAttribute("appId");
        
        log.info("Complete multipart upload - appId: {}, userId: {}, taskId: {}", 
                appId, userId, taskId);
        
        // TODO: 从 JWT Token 中获取真实的 userId
        if (userId == null) {
            userId = "1"; // 默认用户 ID，仅用于测试
        }
        
        String fileId = multipartUploadService.completeUpload(taskId, userId);
        
        // TODO: 生成文件访问 URL
        String url = "/api/v1/files/" + fileId;
        
        CompleteUploadResponse response = new CompleteUploadResponse(fileId, url);
        
        log.info("Complete multipart upload success - appId: {}, userId: {}, fileId: {}", 
                appId, userId, fileId);
        
        return ApiResponse.success(response);
    }
    
    /**
     * 取消/中止分片上传
     * 
     * @param taskId 任务 ID
     * @param httpRequest HTTP请求对象
     * @param userId 用户 ID (从认证上下文获取)
     * @return 成功响应
     */
    @DeleteMapping("/{taskId}")
    public ApiResponse<Void> abortUpload(
            @PathVariable String taskId,
            HttpServletRequest httpRequest,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        
        String appId = (String) httpRequest.getAttribute("appId");
        
        log.info("Abort multipart upload - appId: {}, userId: {}, taskId: {}", 
                appId, userId, taskId);
        
        // TODO: 从 JWT Token 中获取真实的 userId
        if (userId == null) {
            userId = "1"; // 默认用户 ID，仅用于测试
        }
        
        multipartUploadService.abortUpload(taskId, userId);
        
        log.info("Abort multipart upload success - appId: {}, userId: {}, taskId: {}", 
                appId, userId, taskId);
        
        return ApiResponse.success(null);
    }
    
    /**
     * 查询上传进度
     * 
     * @param taskId 任务 ID
     * @param httpRequest HTTP请求对象
     * @param userId 用户 ID (从认证上下文获取)
     * @return 上传进度
     */
    @GetMapping("/{taskId}/progress")
    public ApiResponse<UploadProgressResponse> getProgress(
            @PathVariable String taskId,
            HttpServletRequest httpRequest,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        
        String appId = (String) httpRequest.getAttribute("appId");
        
        log.info("Get upload progress - appId: {}, userId: {}, taskId: {}", 
                appId, userId, taskId);
        
        // TODO: 从 JWT Token 中获取真实的 userId
        if (userId == null) {
            userId = "1"; // 默认用户 ID，仅用于测试
        }
        
        UploadProgressResponse response = multipartUploadService.getProgress(taskId, userId);
        
        return ApiResponse.success(response);
    }
    
    /**
     * 列出用户的上传任务
     * 
     * @param httpRequest HTTP请求对象
     * @param userId 用户 ID (从认证上下文获取)
     * @return 任务列表
     */
    @GetMapping("/tasks")
    public ApiResponse<List<UploadTask>> listTasks(
            HttpServletRequest httpRequest,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        
        String appId = (String) httpRequest.getAttribute("appId");
        
        log.info("List upload tasks - appId: {}, userId: {}", appId, userId);
        
        // TODO: 从 JWT Token 中获取真实的 userId
        if (userId == null) {
            userId = "1"; // 默认用户 ID，仅用于测试
        }
        
        List<UploadTask> tasks = multipartUploadService.listTasks(appId, userId);
        
        return ApiResponse.success(tasks);
    }
    
    /**
     * 完成上传响应
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class CompleteUploadResponse {
        private String fileId;
        private String url;
    }
}
