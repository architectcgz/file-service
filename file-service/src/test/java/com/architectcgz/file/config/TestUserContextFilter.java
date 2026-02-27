package com.architectcgz.file.config;

import com.architectcgz.file.common.context.UserContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Test filter to set UserContext from headers in integration tests
 */
@Component
public class TestUserContextFilter implements Filter {
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            
            // Extract headers
            String userId = httpRequest.getHeader("X-User-Id");
            String appId = httpRequest.getHeader("X-App-Id");
            
            // Set context
            if (userId != null) {
                UserContext.setUserId(userId);
            }
            if (appId != null) {
                UserContext.setAppId(appId);
                httpRequest.setAttribute("appId", appId);
            }
            
            chain.doFilter(request, response);
        } finally {
            // Clear context after request
            UserContext.clear();
        }
    }
}
