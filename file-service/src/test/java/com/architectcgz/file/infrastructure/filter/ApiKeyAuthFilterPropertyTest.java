package com.architectcgz.file.infrastructure.filter;

import com.architectcgz.file.common.context.AdminContext;
import com.architectcgz.file.infrastructure.config.AdminProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import net.jqwik.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ApiKeyAuthFilter 属性测试
 * 
 * Feature: file-service-optimization
 * 使用基于属性的测试验证管理员 API 认证的正确性属性
 */
class ApiKeyAuthFilterPropertyTest {

    /**
     * Feature: file-service-optimization, Property 33: 管理员 API 认证要求
     * 
     * 属性：对于任何管理员 API 请求，如果请求头中不包含有效的 X-Admin-Api-Key，
     * 系统应该拒绝请求并返回 401 未授权错误。
     * 
     * 验证需求：13.1
     */
    @Property(tries = 100)
    @Label("Property 33: 管理员 API 认证要求 - 无效或缺失的 API Key 应被拒绝")
    void adminApiAuthenticationRequired(
            @ForAll("adminApiRequests") AdminApiRequest request,
            @ForAll("invalidApiKeys") String invalidApiKey
    ) throws Exception {
        // Given: 创建 AdminProperties 配置和 filter
        AdminProperties adminProperties = createAdminProperties();
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(adminProperties);
        
        // 创建 mock request 和 response
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRequestURI(request.getUri());
        
        // 设置无效或缺失的 API Key
        if (invalidApiKey != null && !invalidApiKey.isEmpty()) {
            httpRequest.addHeader("X-Admin-Api-Key", invalidApiKey);
        }
        // 如果 invalidApiKey 为 null 或空，则不设置 header（模拟缺失的情况）
        
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        
        FilterChain mockFilterChain = mock(FilterChain.class);
        
        // When: 执行过滤器
        filter.doFilterInternal(httpRequest, httpResponse, mockFilterChain);
        
        // Then: 验证请求被拒绝
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, httpResponse.getStatus(),
                "Request without valid API key should return 401 Unauthorized");
        
        // 验证 filter chain 没有被调用（请求被拦截）
        verify(mockFilterChain, never()).doFilter(any(), any());
        
        // 验证响应包含错误信息
        String responseBody = httpResponse.getContentAsString();
        assertTrue(responseBody.contains("error"), 
                "Response should contain error information");
        assertTrue(responseBody.contains("UNAUTHORIZED") || 
                   responseBody.contains("Missing API key") || 
                   responseBody.contains("Invalid API key"),
                "Response should indicate authentication failure");
        
        // 验证 AdminContext 没有被设置
        assertNull(AdminContext.getAdminUser(),
                "Admin context should not be set for invalid requests");
    }

    /**
     * 验证有效的 API Key 允许请求通过
     */
    @Property(tries = 100)
    @Label("有效的 API Key 应允许请求通过")
    void validApiKeyAllowsAccess(
            @ForAll("adminApiRequests") AdminApiRequest request,
            @ForAll("validApiKeys") AdminProperties.ApiKeyConfig validKey
    ) throws Exception {
        // Given: 创建包含有效 API Key 的 AdminProperties
        AdminProperties adminProperties = new AdminProperties();
        List<AdminProperties.ApiKeyConfig> apiKeys = new ArrayList<>();
        apiKeys.add(validKey);
        adminProperties.setApiKeys(apiKeys);
        
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(adminProperties);
        
        // 创建 mock request 和 response
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRequestURI(request.getUri());
        httpRequest.addHeader("X-Admin-Api-Key", validKey.getKey());
        httpRequest.setRemoteAddr(request.getIpAddress());
        
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        
        FilterChain mockFilterChain = mock(FilterChain.class);
        
        // When: 执行过滤器
        filter.doFilterInternal(httpRequest, httpResponse, mockFilterChain);
        
        // Then: 验证请求被允许通过
        verify(mockFilterChain, times(1)).doFilter(any(), any());
        
        // 验证响应状态不是 401
        assertNotEquals(HttpServletResponse.SC_UNAUTHORIZED, httpResponse.getStatus(),
                "Request with valid API key should not return 401");
        
        // 验证 AdminContext 被正确设置（在 filter chain 执行期间）
        // 注意：由于 filter 在 finally 块中清理了 context，这里无法直接验证
        // 但我们可以验证 filter chain 被调用，说明认证成功
    }

    /**
     * 验证非管理员 API 不需要认证
     */
    @Property(tries = 50)
    @Label("非管理员 API 不需要认证")
    void nonAdminApiDoesNotRequireAuth(
            @ForAll("nonAdminApiRequests") String nonAdminUri
    ) throws Exception {
        // Given: 创建 AdminProperties 和 filter
        AdminProperties adminProperties = createAdminProperties();
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(adminProperties);
        
        // 创建 mock request 和 response（不设置 API Key）
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRequestURI(nonAdminUri);
        
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        
        FilterChain mockFilterChain = mock(FilterChain.class);
        
        // When: 执行过滤器
        filter.doFilterInternal(httpRequest, httpResponse, mockFilterChain);
        
        // Then: 验证请求被允许通过（不需要认证）
        verify(mockFilterChain, times(1)).doFilter(any(), any());
        
        // 验证响应状态不是 401
        assertNotEquals(HttpServletResponse.SC_UNAUTHORIZED, httpResponse.getStatus(),
                "Non-admin API should not require authentication");
    }

    /**
     * 验证 AdminContext 在请求处理后被清理
     */
    @Property(tries = 50)
    @Label("AdminContext 应在请求处理后被清理")
    void adminContextClearedAfterRequest(
            @ForAll("adminApiRequests") AdminApiRequest request,
            @ForAll("validApiKeys") AdminProperties.ApiKeyConfig validKey
    ) throws Exception {
        // Given: 创建包含有效 API Key 的 AdminProperties
        AdminProperties adminProperties = new AdminProperties();
        List<AdminProperties.ApiKeyConfig> apiKeys = new ArrayList<>();
        apiKeys.add(validKey);
        adminProperties.setApiKeys(apiKeys);
        
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(adminProperties);
        
        // 创建 mock request 和 response
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRequestURI(request.getUri());
        httpRequest.addHeader("X-Admin-Api-Key", validKey.getKey());
        httpRequest.setRemoteAddr(request.getIpAddress());
        
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        
        FilterChain mockFilterChain = mock(FilterChain.class);
        
        // When: 执行过滤器
        filter.doFilterInternal(httpRequest, httpResponse, mockFilterChain);
        
        // Then: 验证 AdminContext 在请求处理后被清理
        assertNull(AdminContext.getAdminUser(),
                "Admin user should be cleared after request");
        assertNull(AdminContext.getIpAddress(),
                "IP address should be cleared after request");
    }

    // ========== Arbitraries (数据生成器) ==========

    /**
     * 生成管理员 API 请求
     */
    @Provide
    Arbitrary<AdminApiRequest> adminApiRequests() {
        return Combinators.combine(
                adminApiUris(),
                ipAddresses()
        ).as(AdminApiRequest::new);
    }

    /**
     * 生成管理员 API URI
     */
    @Provide
    Arbitrary<String> adminApiUris() {
        return Arbitraries.of(
                "/api/v1/admin/tenants",
                "/api/v1/admin/tenants/blog",
                "/api/v1/admin/tenants/im/status",
                "/api/v1/admin/files",
                "/api/v1/admin/files/abc123",
                "/api/v1/admin/files/batch-delete",
                "/api/v1/admin/files/statistics",
                "/api/v1/admin/files/statistics/by-tenant"
        );
    }

    /**
     * 生成非管理员 API URI
     */
    @Provide
    Arbitrary<String> nonAdminApiRequests() {
        return Arbitraries.of(
                "/api/v1/files/upload",
                "/api/v1/files/instant-upload",
                "/api/v1/files/abc123:issue-access-ticket",
                "/api/v1/multipart/init",
                "/api/v1/multipart/upload",
                "/api/v1/presigned/upload",
                "/health",
                "/actuator/health"
        );
    }

    /**
     * 生成无效的 API Key
     * 包括：null、空字符串、错误的 key、随机字符串
     */
    @Provide
    Arbitrary<String> invalidApiKeys() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just(""),
                Arbitraries.just("   "),
                Arbitraries.just("invalid-key"),
                Arbitraries.just("wrong-api-key-123"),
                Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(50)
        );
    }

    /**
     * 生成有效的 API Key 配置
     */
    @Provide
    Arbitrary<AdminProperties.ApiKeyConfig> validApiKeys() {
        return Combinators.combine(
                Arbitraries.of("admin-console", "monitoring", "backup-service", "analytics"),
                Arbitraries.strings().alpha().numeric().ofLength(32),
                permissions()
        ).as((name, key, perms) -> {
            AdminProperties.ApiKeyConfig config = new AdminProperties.ApiKeyConfig();
            config.setName(name);
            config.setKey(key);
            config.setPermissions(perms);
            return config;
        });
    }

    /**
     * 生成权限列表
     */
    @Provide
    Arbitrary<List<String>> permissions() {
        return Arbitraries.of(
                List.of("READ", "WRITE", "DELETE"),
                List.of("READ"),
                List.of("READ", "WRITE"),
                List.of("ADMIN")
        );
    }

    /**
     * 生成 IP 地址
     */
    @Provide
    Arbitrary<String> ipAddresses() {
        return Combinators.combine(
                Arbitraries.integers().between(1, 255),
                Arbitraries.integers().between(0, 255),
                Arbitraries.integers().between(0, 255),
                Arbitraries.integers().between(1, 255)
        ).as((a, b, c, d) -> String.format("%d.%d.%d.%d", a, b, c, d));
    }

    // ========== Helper Methods ==========

    /**
     * 创建测试用的 AdminProperties
     */
    private AdminProperties createAdminProperties() {
        AdminProperties properties = new AdminProperties();
        List<AdminProperties.ApiKeyConfig> apiKeys = new ArrayList<>();
        
        // 添加一些测试用的有效 API Key
        AdminProperties.ApiKeyConfig key1 = new AdminProperties.ApiKeyConfig();
        key1.setName("test-admin");
        key1.setKey("test-key-12345678901234567890");
        key1.setPermissions(List.of("READ", "WRITE", "DELETE"));
        apiKeys.add(key1);
        
        AdminProperties.ApiKeyConfig key2 = new AdminProperties.ApiKeyConfig();
        key2.setName("test-monitoring");
        key2.setKey("monitoring-key-09876543210987654321");
        key2.setPermissions(List.of("READ"));
        apiKeys.add(key2);
        
        properties.setApiKeys(apiKeys);
        return properties;
    }

    // ========== Helper Classes ==========

    /**
     * 管理员 API 请求数据类
     */
    static class AdminApiRequest {
        private final String uri;
        private final String ipAddress;

        AdminApiRequest(String uri, String ipAddress) {
            this.uri = uri;
            this.ipAddress = ipAddress;
        }

        String getUri() {
            return uri;
        }

        String getIpAddress() {
            return ipAddress;
        }
    }
}
