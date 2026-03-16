package com.architectcgz.file.infrastructure.filter;

import com.architectcgz.file.common.context.AdminContext;
import com.architectcgz.file.infrastructure.config.AdminProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ApiKeyAuthFilter
 * Tests specific examples and edge cases for API key authentication
 */
@DisplayName("ApiKeyAuthFilter Unit Tests")
class ApiKeyAuthFilterTest {

    private ApiKeyAuthFilter filter;
    private AdminProperties adminProperties;
    private FilterChain mockFilterChain;
    
    private static final String VALID_API_KEY = "test-admin-key-12345678";
    private static final String VALID_API_KEY_NAME = "test-admin";
    private static final String ANOTHER_VALID_KEY = "monitoring-key-87654321";
    private static final String ANOTHER_KEY_NAME = "monitoring";
    
    @BeforeEach
    void setUp() {
        // Create AdminProperties with test API keys
        adminProperties = new AdminProperties();
        List<AdminProperties.ApiKeyConfig> apiKeys = new ArrayList<>();
        
        AdminProperties.ApiKeyConfig key1 = new AdminProperties.ApiKeyConfig();
        key1.setName(VALID_API_KEY_NAME);
        key1.setKey(VALID_API_KEY);
        key1.setPermissions(List.of("READ", "WRITE", "DELETE"));
        apiKeys.add(key1);
        
        AdminProperties.ApiKeyConfig key2 = new AdminProperties.ApiKeyConfig();
        key2.setName(ANOTHER_KEY_NAME);
        key2.setKey(ANOTHER_VALID_KEY);
        key2.setPermissions(List.of("READ"));
        apiKeys.add(key2);
        
        adminProperties.setApiKeys(apiKeys);
        
        filter = new ApiKeyAuthFilter(adminProperties);
        mockFilterChain = mock(FilterChain.class);
    }
    
    @AfterEach
    void tearDown() {
        // Ensure AdminContext is cleared after each test
        AdminContext.clear();
    }

    @Test
    @DisplayName("Valid API key should allow request to pass through")
    void testValidApiKeyPassesAuthentication() throws Exception {
        // Given: Request to admin API with valid API key
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/admin/tenants");
        request.addHeader("X-Admin-Api-Key", VALID_API_KEY);
        request.setRemoteAddr("192.168.1.100");
        
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        // When: Filter processes the request
        filter.doFilterInternal(request, response, mockFilterChain);
        
        // Then: Request should pass through
        verify(mockFilterChain, times(1)).doFilter(any(), any());
        assertNotEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }

    @Test
    @DisplayName("Invalid API key should be rejected with 401")
    void testInvalidApiKeyRejected() throws Exception {
        // Given: Request with invalid API key
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/admin/files");
        request.addHeader("X-Admin-Api-Key", "invalid-key-12345");
        
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        // When: Filter processes the request
        filter.doFilterInternal(request, response, mockFilterChain);
        
        // Then: Request should be rejected
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(mockFilterChain, never()).doFilter(any(), any());
        
        String responseBody = response.getContentAsString();
        assertTrue(responseBody.contains("Invalid API key"));
        assertTrue(responseBody.contains("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Missing API key should be rejected with 401")
    void testMissingApiKeyRejected() throws Exception {
        // Given: Request without API key header
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/admin/tenants/blog");
        // No X-Admin-Api-Key header set
        
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        // When: Filter processes the request
        filter.doFilterInternal(request, response, mockFilterChain);
        
        // Then: Request should be rejected
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(mockFilterChain, never()).doFilter(any(), any());
        
        String responseBody = response.getContentAsString();
        assertTrue(responseBody.contains("Missing API key"));
        assertTrue(responseBody.contains("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Empty API key should be rejected with 401")
    void testEmptyApiKeyRejected() throws Exception {
        // Given: Request with empty API key
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/admin/files/statistics");
        request.addHeader("X-Admin-Api-Key", "");
        
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        // When: Filter processes the request
        filter.doFilterInternal(request, response, mockFilterChain);
        
        // Then: Request should be rejected
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(mockFilterChain, never()).doFilter(any(), any());
        
        String responseBody = response.getContentAsString();
        assertTrue(responseBody.contains("Missing API key"));
    }

    @Test
    @DisplayName("Whitespace-only API key should be rejected with 401")
    void testWhitespaceApiKeyRejected() throws Exception {
        // Given: Request with whitespace-only API key
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/admin/files/batch-delete");
        request.addHeader("X-Admin-Api-Key", "   ");
        
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        // When: Filter processes the request
        filter.doFilterInternal(request, response, mockFilterChain);
        
        // Then: Request should be rejected
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(mockFilterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Non-admin API should not require authentication")
    void testNonAdminApiDoesNotRequireAuth() throws Exception {
        // Given: Request to non-admin API without API key
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/files/upload");
        // No API key header
        
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        // When: Filter processes the request
        filter.doFilterInternal(request, response, mockFilterChain);
        
        // Then: Request should pass through without authentication
        verify(mockFilterChain, times(1)).doFilter(any(), any());
        assertNotEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }

    @Test
    @DisplayName("Multiple valid API keys should all work")
    void testMultipleValidApiKeys() throws Exception {
        // Test first API key
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.setRequestURI("/api/v1/admin/tenants");
        request1.addHeader("X-Admin-Api-Key", VALID_API_KEY);
        
        MockHttpServletResponse response1 = new MockHttpServletResponse();
        
        filter.doFilterInternal(request1, response1, mockFilterChain);
        verify(mockFilterChain, times(1)).doFilter(any(), any());
        
        // Reset mock
        reset(mockFilterChain);
        
        // Test second API key
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.setRequestURI("/api/v1/admin/files");
        request2.addHeader("X-Admin-Api-Key", ANOTHER_VALID_KEY);
        
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        
        filter.doFilterInternal(request2, response2, mockFilterChain);
        verify(mockFilterChain, times(1)).doFilter(any(), any());
    }

    @Test
    @DisplayName("AdminContext should be set correctly for valid requests")
    void testAdminContextSetCorrectly() throws Exception {
        // Given: Request with valid API key
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/admin/tenants");
        request.addHeader("X-Admin-Api-Key", VALID_API_KEY);
        request.setRemoteAddr("10.0.0.5");
        
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        // Create a filter chain that captures the context
        final String[] capturedAdminUser = new String[1];
        final String[] capturedIpAddress = new String[1];
        
        FilterChain capturingChain = (req, res) -> {
            capturedAdminUser[0] = AdminContext.getAdminUser();
            capturedIpAddress[0] = AdminContext.getIpAddress();
        };
        
        // When: Filter processes the request
        filter.doFilterInternal(request, response, capturingChain);
        
        // Then: AdminContext should have been set during filter chain execution
        assertEquals(VALID_API_KEY_NAME, capturedAdminUser[0]);
        assertEquals("10.0.0.5", capturedIpAddress[0]);
        
        // And: AdminContext should be cleared after request
        assertNull(AdminContext.getAdminUser());
        assertNull(AdminContext.getIpAddress());
    }

    @Test
    @DisplayName("AdminContext should handle X-Forwarded-For header")
    void testXForwardedForHeader() throws Exception {
        // Given: Request with X-Forwarded-For header
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/admin/files");
        request.addHeader("X-Admin-Api-Key", VALID_API_KEY);
        request.addHeader("X-Forwarded-For", "203.0.113.1, 198.51.100.1");
        request.setRemoteAddr("192.168.1.1");
        
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        final String[] capturedIpAddress = new String[1];
        
        FilterChain capturingChain = (req, res) -> {
            capturedIpAddress[0] = AdminContext.getIpAddress();
        };
        
        // When: Filter processes the request
        filter.doFilterInternal(request, response, capturingChain);
        
        // Then: Should use first IP from X-Forwarded-For
        assertEquals("203.0.113.1", capturedIpAddress[0]);
    }

    @Test
    @DisplayName("AdminContext should be cleared even if filter chain throws exception")
    void testAdminContextClearedOnException() throws Exception {
        // Given: Request with valid API key
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/admin/tenants");
        request.addHeader("X-Admin-Api-Key", VALID_API_KEY);
        
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        // Filter chain that throws exception
        FilterChain throwingChain = (req, res) -> {
            throw new RuntimeException("Test exception");
        };
        
        // When: Filter processes the request and exception is thrown
        try {
            filter.doFilterInternal(request, response, throwingChain);
            fail("Expected exception to be thrown");
        } catch (RuntimeException e) {
            // Expected
        }
        
        // Then: AdminContext should still be cleared
        assertNull(AdminContext.getAdminUser());
        assertNull(AdminContext.getIpAddress());
    }

    @Test
    @DisplayName("Different admin API paths should all require authentication")
    void testVariousAdminApiPaths() throws Exception {
        String[] adminPaths = {
            "/api/v1/admin/tenants",
            "/api/v1/admin/tenants/blog",
            "/api/v1/admin/tenants/blog/status",
            "/api/v1/admin/files",
            "/api/v1/admin/files/abc123",
            "/api/v1/admin/files/batch-delete",
            "/api/v1/admin/files/statistics",
            "/api/v1/admin/files/statistics/by-tenant"
        };
        
        for (String path : adminPaths) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI(path);
            // No API key
            
            MockHttpServletResponse response = new MockHttpServletResponse();
            
            filter.doFilterInternal(request, response, mockFilterChain);
            
            assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus(),
                    "Path " + path + " should require authentication");
            
            // Reset for next iteration
            response = new MockHttpServletResponse();
        }
        
        // Verify filter chain was never called
        verify(mockFilterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Non-admin API paths should not require authentication")
    void testVariousNonAdminApiPaths() throws Exception {
        String[] nonAdminPaths = {
            "/api/v1/files/upload",
            "/api/v1/files/instant-upload",
            "/api/v1/files/abc123:issue-access-ticket",
            "/api/v1/multipart/init",
            "/api/v1/multipart/upload",
            "/api/v1/presigned/upload",
            "/health",
            "/actuator/health",
            "/api/v1/public/files"
        };
        
        for (String path : nonAdminPaths) {
            reset(mockFilterChain);
            
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI(path);
            // No API key
            
            MockHttpServletResponse response = new MockHttpServletResponse();
            
            filter.doFilterInternal(request, response, mockFilterChain);
            
            verify(mockFilterChain, times(1)).doFilter(any(), any());
            assertNotEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus(),
                    "Path " + path + " should not require authentication");
        }
    }

    @Test
    @DisplayName("Response should have correct content type and encoding")
    void testUnauthorizedResponseFormat() throws Exception {
        // Given: Request without API key
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/admin/tenants");
        
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        // When: Filter processes the request
        filter.doFilterInternal(request, response, mockFilterChain);
        
        // Then: Response should have correct format
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"), 
                "Content type should be application/json");
        assertEquals("UTF-8", response.getCharacterEncoding());
        
        String responseBody = response.getContentAsString();
        assertTrue(responseBody.startsWith("{"));
        assertTrue(responseBody.endsWith("}"));
        assertTrue(responseBody.contains("\"error\""));
        assertTrue(responseBody.contains("\"code\""));
        assertTrue(responseBody.contains("\"message\""));
    }
}
