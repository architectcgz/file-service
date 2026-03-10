package com.architectcgz.file.interfaces.controller;

import com.architectcgz.file.common.context.UserContext;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.result.ApiResponse;
import com.architectcgz.file.application.service.UploadApplicationService;
import com.architectcgz.file.interfaces.dto.UploadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 上传控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
public class UploadController {
    
    private final UploadApplicationService uploadApplicationService;
    
    /**
     * 上传图片
     *
     * @param file 图片文件
     * @param request HTTP请求对象
     * @return 上传结果
     */
    @PostMapping("/image")
    public ApiResponse<UploadResult> uploadImage(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        String appId = (String) request.getAttribute("appId");
        String userId = resolveUserId();
        
        log.info("Upload image request - appId: {}, userId: {}, fileName: {}, size: {}", 
                appId, userId, file.getOriginalFilename(), file.getSize());
        
        UploadResult result = uploadApplicationService.uploadImage(appId, file, userId);
        
        log.info("Upload image success - appId: {}, userId: {}, fileId: {}", 
                appId, userId, result.getFileId());
        
        return ApiResponse.success(result);
    }
    
    /**
     * 上传文件
     *
     * @param file 文件
     * @param request HTTP请求对象
     * @return 上传结果
     */
    @PostMapping("/file")
    public ApiResponse<UploadResult> uploadFile(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        String appId = (String) request.getAttribute("appId");
        String userId = resolveUserId();
        
        log.info("Upload file request - appId: {}, userId: {}, fileName: {}, size: {}", 
                appId, userId, file.getOriginalFilename(), file.getSize());
        
        UploadResult result = uploadApplicationService.uploadFile(appId, file, userId);
        
        log.info("Upload file success - appId: {}, userId: {}, fileId: {}", 
                appId, userId, result.getFileId());
        
        return ApiResponse.success(result);
    }
    
    /**
     * 删除文件
     *
     * @param fileRecordId 文件记录ID
     * @param request HTTP请求对象
     * @return 操作结果
     */
    @DeleteMapping("/{fileRecordId}")
    public ApiResponse<Void> deleteFile(
            @PathVariable String fileRecordId,
            HttpServletRequest request) {
        String appId = (String) request.getAttribute("appId");
        String userId = resolveUserId();
        
        log.info("Delete file request - appId: {}, userId: {}, fileId: {}", 
                appId, userId, fileRecordId);
        
        uploadApplicationService.deleteFile(appId, fileRecordId, userId);
        
        log.info("Delete file success - appId: {}, userId: {}, fileId: {}", 
                appId, userId, fileRecordId);
        
        return ApiResponse.success(null);
    }

    private String resolveUserId() {
        String userId = UserContext.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new AccessDeniedException("未获取到用户身份");
        }
        return userId;
    }
}
