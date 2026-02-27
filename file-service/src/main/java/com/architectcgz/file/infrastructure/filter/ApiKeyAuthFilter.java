package com.architectcgz.file.infrastructure.filter;

import com.architectcgz.file.common.context.AdminContext;
import com.architectcgz.file.infrastructure.config.AdminProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter for authenticating admin API requests using API keys
 * Validates the X-Admin-Api-Key header and sets the admin context
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    
    private static final String API_KEY_HEADER = "X-Admin-Api-Key";
    private static final String ADMIN_API_PREFIX = "/api/v1/admin/";
    
    private final AdminProperties adminProperties;
    
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        
        String requestUri = request.getRequestURI();
        
        // Only apply authentication to admin API endpoints
        if (!requestUri.startsWith(ADMIN_API_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Extract API key from header
        String apiKey = request.getHeader(API_KEY_HEADER);
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("Admin API request without API key: {}", requestUri);
            sendUnauthorizedResponse(response, "Missing API key");
            return;
        }
        
        // Validate API key
        AdminProperties.ApiKeyConfig keyConfig = findApiKeyConfig(apiKey);
        if (keyConfig == null) {
            log.warn("Admin API request with invalid API key: {}", requestUri);
            sendUnauthorizedResponse(response, "Invalid API key");
            return;
        }
        
        // Set admin context
        AdminContext.setAdminUser(keyConfig.getName());
        AdminContext.setIpAddress(getClientIpAddress(request));
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always clear context after request processing
            AdminContext.clear();
        }
    }
    
    /**
     * Find API key configuration by key value
     */
    private AdminProperties.ApiKeyConfig findApiKeyConfig(String apiKey) {
        return adminProperties.getApiKeys().stream()
                .filter(config -> apiKey.equals(config.getKey()))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Get client IP address from request
     * Handles X-Forwarded-For header for proxied requests
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
    
    /**
     * Send 401 Unauthorized response
     */
    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format(
                "{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"%s\"}}", 
                message
        ));
    }
}
