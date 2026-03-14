package com.architectcgz.file.interfaces.controller;

import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.context.UserContext;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.result.ApiResponse;
import com.architectcgz.file.application.dto.FileDetailResponse;
import com.architectcgz.file.application.dto.FileUrlResponse;
import com.architectcgz.file.application.dto.UpdateAccessLevelRequest;
import com.architectcgz.file.application.service.FileAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.util.StringUtils;

/**
 * 文件访问控制器
 * 提供文件访问 URL 获取、文件详情查询、访问级别修改等功能
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {
    
    private final FileAccessService fileAccessService;
    
    /**
     * 获取文件访问 URL
     * 根据文件的访问级别返回不同类型的 URL：
     * - 公开文件：返回永久 CDN/S3 URL
     * - 私有文件：验证所有权后返回临时预签名 URL
     *
     * @param fileId 文件记录ID
     * @param request HTTP请求对象
     * @return 文件 URL 响应
     */
    @GetMapping("/{fileId}/url")
    public ApiResponse<FileUrlResponse> getFileUrl(
            @PathVariable String fileId,
            HttpServletRequest request) {
        String appId = (String) request.getAttribute("appId");
        String userId = resolveOptionalUserId();
        
        log.info("Get file URL request - appId: {}, userId: {}, fileId: {}", 
                appId, userId, fileId);
        
        FileUrlResponse response = fileAccessService.getFileUrl(appId, fileId, userId);
        response.setGatewayUrl(buildGatewayUrl(fileId));
        
        return ApiResponse.success(response);
    }

    /**
     * 通过网关访问文件
     * 前端统一请求 file-service，由 file-service 完成访问控制后重定向到真实对象地址。
     *
     * @param fileId 文件记录ID
     * @param request HTTP请求对象
     * @return 302 重定向响应
     */
    @GetMapping("/{fileId}/content")
    public ResponseEntity<Void> accessFileContent(
            @PathVariable String fileId,
            HttpServletRequest request) {
        String appId = (String) request.getAttribute("appId");
        String userId = resolveOptionalUserId();

        log.info("Access file content via gateway - appId: {}, userId: {}, fileId: {}",
                appId, userId, fileId);

        FileUrlResponse response = fileAccessService.getFileUrl(appId, fileId, userId);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(response.getUrl()));

        if (Boolean.FALSE.equals(response.getPermanent())) {
            builder.cacheControl(CacheControl.noStore());
        }
        return builder.build();
    }
    
    /**
     * 获取文件详情
     * 返回文件的元数据信息（文件名、大小、类型、状态等）
     * 对于私有文件，需要验证用户所有权
     *
     * @param fileId 文件记录ID
     * @param request HTTP请求对象
     * @return 文件详情响应
     */
    @GetMapping("/{fileId}")
    public ApiResponse<FileDetailResponse> getFileDetail(
            @PathVariable String fileId,
            HttpServletRequest request) {
        String appId = (String) request.getAttribute("appId");
        String userId = resolveOptionalUserId();
        
        log.info("Get file detail request - appId: {}, userId: {}, fileId: {}", 
                appId, userId, fileId);
        
        FileDetailResponse response = fileAccessService.getFileDetail(appId, fileId, userId);
        
        return ApiResponse.success(response);
    }
    
    /**
     * 修改文件访问级别
     * 只有文件所有者可以修改访问级别
     * 可以在 PUBLIC 和 PRIVATE 之间切换
     *
     * @param fileId 文件记录ID
     * @param request HTTP请求对象
     * @param updateRequest 更新访问级别请求
     * @return 成功响应
     */
    @PutMapping("/{fileId}/access-level")
    public ApiResponse<Void> updateAccessLevel(
            @PathVariable String fileId,
            HttpServletRequest request,
            @Valid @RequestBody UpdateAccessLevelRequest updateRequest) {
        String appId = (String) request.getAttribute("appId");
        String userId = requireUserId();
        
        log.info("Update access level request - appId: {}, userId: {}, fileId: {}, newLevel: {}", 
                appId, userId, fileId, updateRequest.getAccessLevel());
        
        fileAccessService.updateAccessLevel(appId, fileId, userId, updateRequest.getAccessLevel());
        
        log.info("Update access level success - appId: {}, userId: {}, fileId: {}", 
                appId, userId, fileId);
        
        return ApiResponse.success();
    }

    private String resolveOptionalUserId() {
        String userId = UserContext.getUserId();
        return StringUtils.hasText(userId) ? userId : null;
    }

    private String requireUserId() {
        String userId = UserContext.getUserId();
        if (!StringUtils.hasText(userId)) {
            throw new AccessDeniedException(FileServiceErrorMessages.USER_IDENTITY_MISSING);
        }
        return userId;
    }

    private String buildGatewayUrl(String fileId) {
        return String.format("/api/v1/files/%s/content", fileId);
    }
}
