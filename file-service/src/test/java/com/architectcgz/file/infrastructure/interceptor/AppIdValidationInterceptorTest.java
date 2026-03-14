package com.architectcgz.file.infrastructure.interceptor;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AppIdValidationInterceptor 单元测试
 */
class AppIdValidationInterceptorTest {
    
    private AppIdValidationInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    
    @BeforeEach
    void setUp() {
        interceptor = new AppIdValidationInterceptor();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }
    
    @Test
    void testValidAppId() throws Exception {
        // Given
        request.addHeader("X-App-Id", "blog");
        
        // When
        boolean result = interceptor.preHandle(request, response, null);
        
        // Then
        assertTrue(result);
        assertEquals("blog", request.getAttribute("appId"));
        assertEquals(200, response.getStatus());
    }
    
    @Test
    void testValidAppIdWithHyphen() throws Exception {
        // Given
        request.addHeader("X-App-Id", "blog-service");
        
        // When
        boolean result = interceptor.preHandle(request, response, null);
        
        // Then
        assertTrue(result);
        assertEquals("blog-service", request.getAttribute("appId"));
    }
    
    @Test
    void testValidAppIdWithUnderscore() throws Exception {
        // Given
        request.addHeader("X-App-Id", "blog_service");
        
        // When
        boolean result = interceptor.preHandle(request, response, null);
        
        // Then
        assertTrue(result);
        assertEquals("blog_service", request.getAttribute("appId"));
    }
    
    @Test
    void testMissingAppId() throws Exception {
        // When
        boolean result = interceptor.preHandle(request, response, null);
        
        // Then
        assertFalse(result);
        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("X-App-Id header is required"));
        assertTrue(response.getContentAsString().contains("\"errorCode\":\"" + FileServiceErrorCodes.MISSING_REQUEST_HEADER + "\""));
    }
    
    @Test
    void testEmptyAppId() throws Exception {
        // Given
        request.addHeader("X-App-Id", "");
        
        // When
        boolean result = interceptor.preHandle(request, response, null);
        
        // Then
        assertFalse(result);
        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("X-App-Id header is required"));
        assertTrue(response.getContentAsString().contains("\"errorCode\":\"" + FileServiceErrorCodes.MISSING_REQUEST_HEADER + "\""));
    }
    
    @Test
    void testAppIdTooLong() throws Exception {
        // Given - 33 characters
        String longAppId = "a".repeat(33);
        request.addHeader("X-App-Id", longAppId);
        
        // When
        boolean result = interceptor.preHandle(request, response, null);
        
        // Then
        assertFalse(result);
        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("exceeds maximum length"));
        assertTrue(response.getContentAsString().contains("\"errorCode\":\"" + FileServiceErrorCodes.VALIDATION_ERROR + "\""));
    }
    
    @Test
    void testAppIdMaxLength() throws Exception {
        // Given - exactly 32 characters
        String maxLengthAppId = "a".repeat(32);
        request.addHeader("X-App-Id", maxLengthAppId);
        
        // When
        boolean result = interceptor.preHandle(request, response, null);
        
        // Then
        assertTrue(result);
        assertEquals(maxLengthAppId, request.getAttribute("appId"));
    }
    
    @Test
    void testInvalidAppIdWithUpperCase() throws Exception {
        // Given
        request.addHeader("X-App-Id", "Blog");
        
        // When
        boolean result = interceptor.preHandle(request, response, null);
        
        // Then
        assertFalse(result);
        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid X-App-Id format"));
        assertTrue(response.getContentAsString().contains("\"errorCode\":\"" + FileServiceErrorCodes.VALIDATION_ERROR + "\""));
    }
    
    @Test
    void testInvalidAppIdWithSpecialChars() throws Exception {
        // Given
        request.addHeader("X-App-Id", "blog@service");
        
        // When
        boolean result = interceptor.preHandle(request, response, null);
        
        // Then
        assertFalse(result);
        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid X-App-Id format"));
        assertTrue(response.getContentAsString().contains("\"errorCode\":\"" + FileServiceErrorCodes.VALIDATION_ERROR + "\""));
    }
    
    @Test
    void testInvalidAppIdWithSpaces() throws Exception {
        // Given
        request.addHeader("X-App-Id", "blog service");
        
        // When
        boolean result = interceptor.preHandle(request, response, null);
        
        // Then
        assertFalse(result);
        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid X-App-Id format"));
        assertTrue(response.getContentAsString().contains("\"errorCode\":\"" + FileServiceErrorCodes.VALIDATION_ERROR + "\""));
    }
    
    @Test
    void testValidAppIdWithNumbers() throws Exception {
        // Given
        request.addHeader("X-App-Id", "blog123");
        
        // When
        boolean result = interceptor.preHandle(request, response, null);
        
        // Then
        assertTrue(result);
        assertEquals("blog123", request.getAttribute("appId"));
    }
}
