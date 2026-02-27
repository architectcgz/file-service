package com.architectcgz.file.infrastructure.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * App ID 验证拦截器
 * 
 * 验证所有请求必须包含有效的 X-App-Id 请求头
 */
@Slf4j
@Component
public class AppIdValidationInterceptor implements HandlerInterceptor {
    
    private static final String HEADER_APP_ID = "X-App-Id";
    private static final int MAX_APP_ID_LENGTH = 32;
    private static final String APP_ID_PATTERN = "^[a-z0-9_-]+$";
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                            HttpServletResponse response, 
                            Object handler) throws IOException {
        String appId = request.getHeader(HEADER_APP_ID);
        
        // 检查是否存在
        if (appId == null || appId.trim().isEmpty()) {
            log.warn("Request missing X-App-Id header: {} {}", request.getMethod(), request.getRequestURI());
            sendErrorResponse(response, "X-App-Id header is required");
            return false;
        }
        
        // 检查长度
        if (appId.length() > MAX_APP_ID_LENGTH) {
            log.warn("X-App-Id exceeds maximum length: {} (length: {})", appId, appId.length());
            sendErrorResponse(response, "X-App-Id exceeds maximum length of " + MAX_APP_ID_LENGTH);
            return false;
        }
        
        // 检查格式
        if (!appId.matches(APP_ID_PATTERN)) {
            log.warn("Invalid X-App-Id format: {}", appId);
            sendErrorResponse(response, "Invalid X-App-Id format. Must match pattern: " + APP_ID_PATTERN);
            return false;
        }
        
        // 验证成功，将 appId 存入请求属性
        request.setAttribute("appId", appId);
        log.debug("Request validated with appId: {}", appId);
        
        return true;
    }
    
    /**
     * 发送错误响应
     */
    private void sendErrorResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
            String.format("{\"code\":400,\"message\":\"%s\",\"data\":null}", message)
        );
    }
}
