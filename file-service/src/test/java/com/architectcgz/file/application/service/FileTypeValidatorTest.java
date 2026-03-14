package com.architectcgz.file.application.service;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.infrastructure.config.FileTypeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * FileTypeValidator 单元测试
 */
@ExtendWith(MockitoExtension.class)
class FileTypeValidatorTest {
    
    @Mock
    private FileTypeProperties fileTypeProperties;

    private FileTypeValidator fileTypeValidator;
    
    @BeforeEach
    void setUp() {
        fileTypeValidator = new FileTypeValidator(fileTypeProperties);
    }
    
    // ========== 文件扩展名验证测或==========
    
    @Test
    void testValidateFileExtension_ValidExtension() {
        // Given
        String fileName = "test.jpg";
        when(fileTypeProperties.isAllowedExtension("jpg")).thenReturn(true);
        
        // When & Then
        assertDoesNotThrow(() -> fileTypeValidator.validateFileExtension(fileName));
        verify(fileTypeProperties).isAllowedExtension("jpg");
    }
    
    @Test
    void testValidateFileExtension_ValidExtensionUpperCase() {
        // Given
        String fileName = "test.JPG";
        when(fileTypeProperties.isAllowedExtension("jpg")).thenReturn(true);
        
        // When & Then
        assertDoesNotThrow(() -> fileTypeValidator.validateFileExtension(fileName));
        verify(fileTypeProperties).isAllowedExtension("jpg");
    }
    
    @Test
    void testValidateFileExtension_InvalidExtension() {
        // Given
        String fileName = "test.exe";
        when(fileTypeProperties.isAllowedExtension("exe")).thenReturn(false);
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
            () -> fileTypeValidator.validateFileExtension(fileName));
        assertTrue(exception.getMessage().contains("不支持的文件扩展名"));
        assertTrue(exception.getMessage().contains("exe"));
        assertEquals(FileServiceErrorCodes.EXTENSION_NOT_ALLOWED, exception.getCode());
    }
    
    @Test
    void testValidateFileExtension_NoExtension() {
        // Given
        String fileName = "testfile";
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
            () -> fileTypeValidator.validateFileExtension(fileName));
        assertEquals("文件必须有扩展名", exception.getMessage());
    }
    
    @Test
    void testValidateFileExtension_EmptyFilename() {
        // Given
        String fileName = "";
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
            () -> fileTypeValidator.validateFileExtension(fileName));
        assertEquals("文件名不能为空", exception.getMessage());
        assertEquals(FileServiceErrorCodes.FILENAME_EMPTY, exception.getCode());
    }
    
    @Test
    void testValidateFileExtension_NullFilename() {
        // Given
        String fileName = null;
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
            () -> fileTypeValidator.validateFileExtension(fileName));
        assertEquals("文件名不能为空", exception.getMessage());
    }
    
    @Test
    void testValidateFileExtension_FileNameEndsWithDot() {
        // Given
        String fileName = "test.";
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
            () -> fileTypeValidator.validateFileExtension(fileName));
        assertEquals("文件必须有扩展名", exception.getMessage());
    }
    
    @Test
    void testValidateFileExtension_MultipleDotsInFilename() {
        // Given
        String fileName = "my.test.file.jpg";
        when(fileTypeProperties.isAllowedExtension("jpg")).thenReturn(true);
        
        // When & Then
        assertDoesNotThrow(() -> fileTypeValidator.validateFileExtension(fileName));
        verify(fileTypeProperties).isAllowedExtension("jpg");
    }
    
    // ========== Content-Type 验证测试 ==========
    
    @Test
    void testValidateContentType_ValidContentType() {
        // Given
        String contentType = "image/jpeg";
        when(fileTypeProperties.isAllowedContentType("image/jpeg")).thenReturn(true);
        
        // When & Then
        assertDoesNotThrow(() -> fileTypeValidator.validateContentType(contentType));
        verify(fileTypeProperties).isAllowedContentType("image/jpeg");
    }
    
    @Test
    void testValidateContentType_ContentTypeWithCharset() {
        // Given
        String contentType = "text/plain; charset=utf-8";
        when(fileTypeProperties.isAllowedContentType("text/plain")).thenReturn(true);
        
        // When & Then
        assertDoesNotThrow(() -> fileTypeValidator.validateContentType(contentType));
        verify(fileTypeProperties).isAllowedContentType("text/plain");
    }
    
    @Test
    void testValidateContentType_InvalidContentType() {
        // Given
        String contentType = "application/x-executable";
        when(fileTypeProperties.isAllowedContentType("application/x-executable")).thenReturn(false);
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
            () -> fileTypeValidator.validateContentType(contentType));
        assertTrue(exception.getMessage().contains("不支持的文件类型"));
        assertTrue(exception.getMessage().contains("application/x-executable"));
        assertEquals(FileServiceErrorCodes.CONTENT_TYPE_NOT_ALLOWED, exception.getCode());
    }
    
    @Test
    void testValidateContentType_EmptyContentType() {
        // Given
        String contentType = "";
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
            () -> fileTypeValidator.validateContentType(contentType));
        assertEquals("Content-Type 不能为空", exception.getMessage());
    }
    
    @Test
    void testValidateContentType_NullContentType() {
        // Given
        String contentType = null;
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
            () -> fileTypeValidator.validateContentType(contentType));
        assertEquals("Content-Type 不能为空", exception.getMessage());
    }
    
    // ========== 文件大小验证测试 ==========
    
    @Test
    void testValidateFileSize_ValidSize() {
        // Given
        long fileSize = 1024 * 1024; // 1MB
        when(fileTypeProperties.isFileSizeAllowed(fileSize)).thenReturn(true);
        
        // When & Then
        assertDoesNotThrow(() -> fileTypeValidator.validateFileSize(fileSize));
        verify(fileTypeProperties).isFileSizeAllowed(fileSize);
    }
    
    @Test
    void testValidateFileSize_MaxSize() {
        // Given
        long fileSize = 104857600L; // 100MB
        when(fileTypeProperties.isFileSizeAllowed(fileSize)).thenReturn(true);
        
        // When & Then
        assertDoesNotThrow(() -> fileTypeValidator.validateFileSize(fileSize));
        verify(fileTypeProperties).isFileSizeAllowed(fileSize);
    }
    
    @Test
    void testValidateFileSize_ExceedsMaxSize() {
        // Given
        long fileSize = 104857601L; // 100MB + 1 byte
        when(fileTypeProperties.isFileSizeAllowed(fileSize)).thenReturn(false);
        when(fileTypeProperties.getMaxFileSize()).thenReturn(104857600L); // 100MB
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
            () -> fileTypeValidator.validateFileSize(fileSize));
        assertTrue(exception.getMessage().contains("文件大小超出限制"));
        assertTrue(exception.getMessage().contains("100 MB"));
    }
    
    @Test
    void testValidateFileSize_ZeroSize() {
        // Given
        long fileSize = 0;
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
            () -> fileTypeValidator.validateFileSize(fileSize));
        assertEquals("文件大小必须大于 0", exception.getMessage());
    }
    
    @Test
    void testValidateFileSize_NegativeSize() {
        // Given
        long fileSize = -1;
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
            () -> fileTypeValidator.validateFileSize(fileSize));
        assertEquals("文件大小必须大于 0", exception.getMessage());
    }
    
    // ========== 完整验证测试 ==========
    
    @Test
    void testValidateFile_AllValid() {
        // Given
        String fileName = "test.jpg";
        String contentType = "image/jpeg";
        long fileSize = 1024 * 1024; // 1MB
        
        when(fileTypeProperties.isAllowedExtension("jpg")).thenReturn(true);
        when(fileTypeProperties.isAllowedContentType("image/jpeg")).thenReturn(true);
        when(fileTypeProperties.isFileSizeAllowed(fileSize)).thenReturn(true);
        
        // When & Then
        assertDoesNotThrow(() -> fileTypeValidator.validateFile(fileName, contentType, fileSize));
        
        verify(fileTypeProperties).isAllowedExtension("jpg");
        verify(fileTypeProperties).isAllowedContentType("image/jpeg");
        verify(fileTypeProperties).isFileSizeAllowed(fileSize);
    }
    
    @Test
    void testValidateFile_InvalidExtension() {
        // Given
        String fileName = "test.exe";
        String contentType = "application/x-executable";
        long fileSize = 1024;
        
        when(fileTypeProperties.isAllowedExtension("exe")).thenReturn(false);
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
            () -> fileTypeValidator.validateFile(fileName, contentType, fileSize));
        assertTrue(exception.getMessage().contains("不支持的文件扩展名"));
    }
    
    @Test
    void testValidateFile_InvalidContentType() {
        // Given
        String fileName = "test.jpg";
        String contentType = "application/x-executable";
        long fileSize = 1024;
        
        when(fileTypeProperties.isAllowedExtension("jpg")).thenReturn(true);
        when(fileTypeProperties.isAllowedContentType("application/x-executable")).thenReturn(false);
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
            () -> fileTypeValidator.validateFile(fileName, contentType, fileSize));
        assertTrue(exception.getMessage().contains("不支持的文件类型"));
    }
    
    @Test
    void testValidateFile_InvalidSize() {
        // Given
        String fileName = "test.jpg";
        String contentType = "image/jpeg";
        long fileSize = 104857601L; // 100MB + 1 byte
        
        when(fileTypeProperties.isAllowedExtension("jpg")).thenReturn(true);
        when(fileTypeProperties.isAllowedContentType("image/jpeg")).thenReturn(true);
        when(fileTypeProperties.isFileSizeAllowed(fileSize)).thenReturn(false);
        when(fileTypeProperties.getMaxFileSize()).thenReturn(104857600L); // 100MB
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
            () -> fileTypeValidator.validateFile(fileName, contentType, fileSize));
        assertTrue(exception.getMessage().contains("文件大小超出限制"));
    }
    
    @Test
    void testValidateFile_ComplexFilename() {
        // Given
        String fileName = "my-test_file (1).jpg";
        String contentType = "image/jpeg";
        long fileSize = 1024;
        
        when(fileTypeProperties.isAllowedExtension("jpg")).thenReturn(true);
        when(fileTypeProperties.isAllowedContentType("image/jpeg")).thenReturn(true);
        when(fileTypeProperties.isFileSizeAllowed(fileSize)).thenReturn(true);
        
        // When & Then
        assertDoesNotThrow(() -> fileTypeValidator.validateFile(fileName, contentType, fileSize));
    }
    
    @Test
    void testValidateFile_ContentTypeWithParameters() {
        // Given
        String fileName = "test.txt";
        String contentType = "text/plain; charset=utf-8; boundary=something";
        long fileSize = 1024;
        
        when(fileTypeProperties.isAllowedExtension("txt")).thenReturn(true);
        when(fileTypeProperties.isAllowedContentType("text/plain")).thenReturn(true);
        when(fileTypeProperties.isFileSizeAllowed(fileSize)).thenReturn(true);
        
        // When & Then
        assertDoesNotThrow(() -> fileTypeValidator.validateFile(fileName, contentType, fileSize));
        verify(fileTypeProperties).isAllowedContentType("text/plain");
    }
    
    // ========== 文件魔数验证测试 ==========
    
    @Test
    void testValidateFileWithMagicNumber_ValidJpeg() {
        // Given
        String fileName = "test.jpg";
        String contentType = "image/jpeg";
        byte[] fileHeader = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        long fileSize = 1024 * 1024;
        
        when(fileTypeProperties.isAllowedExtension("jpg")).thenReturn(true);
        when(fileTypeProperties.isAllowedContentType("image/jpeg")).thenReturn(true);
        when(fileTypeProperties.isFileSizeAllowed(fileSize)).thenReturn(true);
        
        // When & Then
        assertDoesNotThrow(() -> fileTypeValidator.validateFileWithMagicNumber(
            fileName, contentType, fileHeader, fileSize));
    }
    
    @Test
    void testValidateFileWithMagicNumber_ValidPng() {
        // Given
        String fileName = "test.png";
        String contentType = "image/png";
        byte[] fileHeader = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x00};
        long fileSize = 1024 * 1024;
        
        when(fileTypeProperties.isAllowedExtension("png")).thenReturn(true);
        when(fileTypeProperties.isAllowedContentType("image/png")).thenReturn(true);
        when(fileTypeProperties.isFileSizeAllowed(fileSize)).thenReturn(true);
        
        // When & Then
        assertDoesNotThrow(() -> fileTypeValidator.validateFileWithMagicNumber(
            fileName, contentType, fileHeader, fileSize));
    }
    
    @Test
    void testValidateFileWithMagicNumber_TypeMismatch() {
        // Given - 声明为JPEG 但实际是 PNG
        String fileName = "fake.jpg";
        String contentType = "image/jpeg";
        byte[] fileHeader = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x00};
        long fileSize = 1024;
        
        when(fileTypeProperties.isAllowedExtension("jpg")).thenReturn(true);
        when(fileTypeProperties.isAllowedContentType("image/jpeg")).thenReturn(true);
        when(fileTypeProperties.isFileSizeAllowed(fileSize)).thenReturn(true);
        when(fileTypeProperties.isEnableMagicNumberCheck()).thenReturn(true);
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
            () -> fileTypeValidator.validateFileWithMagicNumber(
                fileName, contentType, fileHeader, fileSize));
        assertEquals("文件类型与内容不匹配", exception.getMessage());
    }
    
    @Test
    void testValidateFileWithMagicNumber_UnknownFileType() {
        // Given - 无法识别的文件类型
        String fileName = "unknown.dat";
        String contentType = "application/octet-stream";
        byte[] fileHeader = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B};
        long fileSize = 1024;
        
        when(fileTypeProperties.isAllowedExtension("dat")).thenReturn(true);
        when(fileTypeProperties.isAllowedContentType("application/octet-stream")).thenReturn(true);
        when(fileTypeProperties.isFileSizeAllowed(fileSize)).thenReturn(true);
        when(fileTypeProperties.isEnableMagicNumberCheck()).thenReturn(true);
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
            () -> fileTypeValidator.validateFileWithMagicNumber(
                fileName, contentType, fileHeader, fileSize));
        assertEquals("无法识别文件类型", exception.getMessage());
    }
    
    @Test
    void testValidateFileWithMagicNumber_NullFileHeader() {
        // Given
        String fileName = "test.jpg";
        String contentType = "image/jpeg";
        byte[] fileHeader = null;
        long fileSize = 1024;
        
        when(fileTypeProperties.isAllowedExtension("jpg")).thenReturn(true);
        when(fileTypeProperties.isAllowedContentType("image/jpeg")).thenReturn(true);
        when(fileTypeProperties.isFileSizeAllowed(fileSize)).thenReturn(true);
        when(fileTypeProperties.isEnableMagicNumberCheck()).thenReturn(true);
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
            () -> fileTypeValidator.validateFileWithMagicNumber(
                fileName, contentType, fileHeader, fileSize));
        assertEquals("文件头数据不足，无法验证文件类型", exception.getMessage());
    }
    
    @Test
    void testValidateFileWithMagicNumber_InsufficientFileHeader() {
        // Given - 文件头数据不足（少于 4 字节
        String fileName = "test.jpg";
        String contentType = "image/jpeg";
        byte[] fileHeader = {(byte) 0xFF, (byte) 0xD8};
        long fileSize = 1024;
        
        when(fileTypeProperties.isAllowedExtension("jpg")).thenReturn(true);
        when(fileTypeProperties.isAllowedContentType("image/jpeg")).thenReturn(true);
        when(fileTypeProperties.isFileSizeAllowed(fileSize)).thenReturn(true);
        when(fileTypeProperties.isEnableMagicNumberCheck()).thenReturn(true);
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
            () -> fileTypeValidator.validateFileWithMagicNumber(
                fileName, contentType, fileHeader, fileSize));
        assertEquals("文件头数据不足，无法验证文件类型", exception.getMessage());
    }
    
    @Test
    void testValidateFileWithMagicNumber_JpegVariant() {
        // Given - 声明或image/jpg，检测为 image/jpeg（应该匹配）
        String fileName = "test.jpg";
        String contentType = "image/jpg";
        byte[] fileHeader = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        long fileSize = 1024;
        
        when(fileTypeProperties.isAllowedExtension("jpg")).thenReturn(true);
        when(fileTypeProperties.isAllowedContentType("image/jpg")).thenReturn(true);
        when(fileTypeProperties.isFileSizeAllowed(fileSize)).thenReturn(true);
        
        // When & Then
        assertDoesNotThrow(() -> fileTypeValidator.validateFileWithMagicNumber(
            fileName, contentType, fileHeader, fileSize));
    }
    
    @Test
    void testValidateFileWithMagicNumber_InvalidExtensionBeforeMagicCheck() {
        // Given - 扩展名验证失败，不应该进行魔数检查
        String fileName = "test.exe";
        String contentType = "application/x-executable";
        byte[] fileHeader = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        long fileSize = 1024;
        
        when(fileTypeProperties.isAllowedExtension("exe")).thenReturn(false);
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
            () -> fileTypeValidator.validateFileWithMagicNumber(
                fileName, contentType, fileHeader, fileSize));
        assertTrue(exception.getMessage().contains("不支持的文件扩展名"));
    }
    
    @Test
    void testValidateFileWithMagicNumber_InvalidSizeBeforeMagicCheck() {
        // Given - 文件大小验证失败，不应该进行魔数检查
        String fileName = "test.jpg";
        String contentType = "image/jpeg";
        byte[] fileHeader = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        long fileSize = 104857601L; // 超过限制
        
        when(fileTypeProperties.isAllowedExtension("jpg")).thenReturn(true);
        when(fileTypeProperties.isAllowedContentType("image/jpeg")).thenReturn(true);
        when(fileTypeProperties.isFileSizeAllowed(fileSize)).thenReturn(false);
        when(fileTypeProperties.getMaxFileSize()).thenReturn(104857600L);
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
            () -> fileTypeValidator.validateFileWithMagicNumber(
                fileName, contentType, fileHeader, fileSize));
        assertTrue(exception.getMessage().contains("文件大小超出限制"));
    }
    
    @Test
    void testValidateFileWithMagicNumber_WebP() {
        // Given
        String fileName = "test.webp";
        String contentType = "image/webp";
        byte[] fileHeader = {
            0x52, 0x49, 0x46, 0x46,  // RIFF
            0x00, 0x00, 0x00, 0x00,  // file size (placeholder)
            0x57, 0x45, 0x42, 0x50   // WEBP
        };
        long fileSize = 1024;
        
        when(fileTypeProperties.isAllowedExtension("webp")).thenReturn(true);
        when(fileTypeProperties.isAllowedContentType("image/webp")).thenReturn(true);
        when(fileTypeProperties.isFileSizeAllowed(fileSize)).thenReturn(true);
        
        // When & Then
        assertDoesNotThrow(() -> fileTypeValidator.validateFileWithMagicNumber(
            fileName, contentType, fileHeader, fileSize));
    }
    
    @Test
    void testValidateFileWithMagicNumber_PDF() {
        // Given
        String fileName = "document.pdf";
        String contentType = "application/pdf";
        byte[] fileHeader = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x00, 0x00, 0x00, 0x00};
        long fileSize = 1024 * 1024;
        
        when(fileTypeProperties.isAllowedExtension("pdf")).thenReturn(true);
        when(fileTypeProperties.isAllowedContentType("application/pdf")).thenReturn(true);
        when(fileTypeProperties.isFileSizeAllowed(fileSize)).thenReturn(true);
        
        // When & Then
        assertDoesNotThrow(() -> fileTypeValidator.validateFileWithMagicNumber(
            fileName, contentType, fileHeader, fileSize));
    }
}
