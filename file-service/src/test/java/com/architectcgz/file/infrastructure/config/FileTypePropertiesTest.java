package com.architectcgz.file.infrastructure.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileTypeProperties 单元测试
 */
class FileTypePropertiesTest {
    
    private FileTypeProperties properties;
    
    @BeforeEach
    void setUp() {
        properties = new FileTypeProperties();
    }
    
    @Test
    void testIsAllowedContentType_Image() {
        assertTrue(properties.isAllowedContentType("image/jpeg"));
        assertTrue(properties.isAllowedContentType("image/png"));
        assertTrue(properties.isAllowedContentType("image/gif"));
        assertTrue(properties.isAllowedContentType("image/webp"));
        assertTrue(properties.isAllowedContentType("image/svg+xml"));
    }
    
    @Test
    void testIsAllowedContentType_Video() {
        assertTrue(properties.isAllowedContentType("video/mp4"));
        assertTrue(properties.isAllowedContentType("video/webm"));
        assertTrue(properties.isAllowedContentType("video/quicktime"));
    }
    
    @Test
    void testIsAllowedContentType_Document() {
        assertTrue(properties.isAllowedContentType("application/pdf"));
        assertTrue(properties.isAllowedContentType("application/msword"));
        assertTrue(properties.isAllowedContentType("text/plain"));
    }
    
    @Test
    void testIsAllowedContentType_NotAllowed() {
        assertFalse(properties.isAllowedContentType("application/x-executable"));
        assertFalse(properties.isAllowedContentType("application/x-msdownload"));
        assertFalse(properties.isAllowedContentType("application/zip"));
    }
    
    @Test
    void testIsAllowedContentType_CaseInsensitive() {
        assertTrue(properties.isAllowedContentType("IMAGE/JPEG"));
        assertTrue(properties.isAllowedContentType("Image/Png"));
        assertTrue(properties.isAllowedContentType("VIDEO/MP4"));
    }
    
    @Test
    void testIsAllowedContentType_NullOrEmpty() {
        assertFalse(properties.isAllowedContentType(null));
        assertFalse(properties.isAllowedContentType(""));
    }
    
    @Test
    void testisAllowedExtension() {
        assertTrue(properties.isAllowedExtension("jpg"));
        assertTrue(properties.isAllowedExtension("jpeg"));
        assertTrue(properties.isAllowedExtension("png"));
        assertTrue(properties.isAllowedExtension("gif"));
        assertTrue(properties.isAllowedExtension("webp"));
        assertTrue(properties.isAllowedExtension("svg"));
        assertTrue(properties.isAllowedExtension("mp4"));
        assertTrue(properties.isAllowedExtension("webm"));
        assertTrue(properties.isAllowedExtension("mov"));
        assertTrue(properties.isAllowedExtension("pdf"));
        assertTrue(properties.isAllowedExtension("doc"));
        assertTrue(properties.isAllowedExtension("docx"));
        assertTrue(properties.isAllowedExtension("txt"));
    }
    
    @Test
    void testisAllowedExtension_NotAllowed() {
        assertFalse(properties.isAllowedExtension("exe"));
        assertFalse(properties.isAllowedExtension("bat"));
        assertFalse(properties.isAllowedExtension("sh"));
        assertFalse(properties.isAllowedExtension("zip"));
    }
    
    @Test
    void testisAllowedExtension_CaseInsensitive() {
        assertTrue(properties.isAllowedExtension("JPG"));
        assertTrue(properties.isAllowedExtension("Png"));
        assertTrue(properties.isAllowedExtension("PDF"));
    }
    
    @Test
    void testisAllowedExtension_NullOrEmpty() {
        assertFalse(properties.isAllowedExtension(null));
        assertFalse(properties.isAllowedExtension(""));
    }
    
    @Test
    void testIsFileSizeAllowed() {
        // 默认最或100MB
        assertTrue(properties.isFileSizeAllowed(1024)); // 1KB
        assertTrue(properties.isFileSizeAllowed(1048576)); // 1MB
        assertTrue(properties.isFileSizeAllowed(10485760)); // 10MB
        assertTrue(properties.isFileSizeAllowed(104857600)); // 100MB (边界或
    }
    
    @Test
    void testIsFileSizeAllowed_TooLarge() {
        assertFalse(properties.isFileSizeAllowed(104857601)); // 100MB + 1 byte
        assertFalse(properties.isFileSizeAllowed(209715200)); // 200MB
    }
    
    @Test
    void testIsFileSizeAllowed_Invalid() {
        assertFalse(properties.isFileSizeAllowed(0));
        assertFalse(properties.isFileSizeAllowed(-1));
    }
    
    @Test
    void testGetAllAllowedTypes() {
        var allTypes = properties.getAllowedTypes().getAllAllowedTypes();
        
        // 验证包含所有类或
        assertTrue(allTypes.contains("image/jpeg"));
        assertTrue(allTypes.contains("video/mp4"));
        assertTrue(allTypes.contains("application/pdf"));
        
        // 验证总数
        int expectedCount = 
            properties.getAllowedTypes().getImages().size() +
            properties.getAllowedTypes().getVideos().size() +
            properties.getAllowedTypes().getDocuments().size();
        assertEquals(expectedCount, allTypes.size());
    }
    
    @Test
    void testDefaultValues() {
        assertEquals(104857600L, properties.getMaxFileSize()); // 100MB
        assertTrue(properties.isEnableMagicNumberCheck());
        assertFalse(properties.getAllowedExtensions().isEmpty());
        assertFalse(properties.getAllowedTypes().getImages().isEmpty());
        assertFalse(properties.getAllowedTypes().getVideos().isEmpty());
        assertFalse(properties.getAllowedTypes().getDocuments().isEmpty());
    }
}
