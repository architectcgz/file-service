package com.architectcgz.file.infrastructure.filter;

import com.architectcgz.file.common.context.UserContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * User Context Filter
 * Extracts user information from request headers and sets it in UserContext
 */
@Slf4j
@Component
@Order(1)  // Execute before interceptors
public class UserContextFilter implements Filter {
    
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_APP_ID = "X-App-Id";
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            
            // Extract headers
            String userId = httpRequest.getHeader(HEADER_USER_ID);
            String appId = httpRequest.getHeader(HEADER_APP_ID);
            
            // Set context
            if (userId != null && !userId.trim().isEmpty()) {
                UserContext.setUserId(userId);
                log.debug("Set userId in context: {}", userId);
            }
            
            if (appId != null && !appId.trim().isEmpty()) {
                UserContext.setAppId(appId);
                httpRequest.setAttribute("appId", appId);
                log.debug("Set appId in context: {}", appId);
            }
            
            chain.doFilter(request, response);
        } finally {
            // Clear context after request to prevent memory leaks
            UserContext.clear();
        }
    }
}
