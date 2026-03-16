package com.architectcgz.file.interfaces.controller;

import com.architectcgz.file.application.dto.FileUrlResponse;
import com.architectcgz.file.application.service.FileAccessService;
import com.architectcgz.file.common.context.UserContext;
import com.architectcgz.file.infrastructure.config.AccessProperties;
import com.architectcgz.file.infrastructure.config.CacheProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 文件访问跳转控制器。
 *
 * 统一把 file-service 内部的 URL 解析能力暴露为 302 跳转接口，
 * 供 file-gateway 透传 Location 与 Cache-Control。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileAccessController {

    private final FileAccessService fileAccessService;
    private final AccessProperties accessProperties;
    private final CacheProperties cacheProperties;

    @GetMapping("/{fileId}/content")
    public ResponseEntity<Void> accessFileContent(@PathVariable String fileId, HttpServletRequest request) {
        String appId = (String) request.getAttribute("appId");
        String userId = normalizeUserId(UserContext.getUserId());

        FileUrlResponse fileUrl = fileAccessService.getFileUrl(appId, fileId, userId);
        String cacheControl = Boolean.TRUE.equals(fileUrl.getPermanent())
                ? "public, max-age=" + cacheProperties.getUrl().getTtl()
                : "private, max-age=" + accessProperties.getPrivateUrlExpireSeconds();

        log.debug("Resolved file redirect: appId={}, userId={}, fileId={}, permanent={}",
                appId, userId, fileId, fileUrl.getPermanent());

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(fileUrl.getUrl()))
                .header(HttpHeaders.CACHE_CONTROL, cacheControl)
                .build();
    }

    private String normalizeUserId(String userId) {
        return StringUtils.hasText(userId) ? userId.trim() : null;
    }
}
