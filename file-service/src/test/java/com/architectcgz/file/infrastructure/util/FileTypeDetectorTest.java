package com.architectcgz.file.infrastructure.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileTypeDetector 单元测试
 */
class FileTypeDetectorTest {
    
    @Test
    void testDetectJpeg() {
        // JPEG 魔数: FF D8 FF
        byte[] jpegHeader = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        
        String detectedType = FileTypeDetector.detectFileType(jpegHeader);
        
        assertEquals("image/jpeg", detectedType);
    }
    
    @Test
    void testDetectPng() {
        // PNG 魔数: 89 50 4E 47 0D 0A 1A 0A
        byte[] pngHeader = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
        
        String detectedType = FileTypeDetector.detectFileType(pngHeader);
        
        assertEquals("image/png", detectedType);
    }
    
    @Test
    void testDetectGif() {
        // GIF 魔数: 47 49 46 38 (GIF8)
        byte[] gifHeader = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61}; // GIF89a
        
        String detectedType = FileTypeDetector.detectFileType(gifHeader);
        
        assertEquals("image/gif", detectedType);
    }
    
    @Test
    void testDetectPdf() {
        // PDF 魔数: 25 50 44 46 (%PDF)
        byte[] pdfHeader = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34}; // %PDF-1.4
        
        String detectedType = FileTypeDetector.detectFileType(pdfHeader);
        
        assertEquals("application/pdf", detectedType);
    }
    
    @Test
    void testDetectWebP() {
        // WebP 魔数: 52 49 46 46 xx xx xx xx 57 45 42 50 (RIFF....WEBP)
        byte[] webpHeader = {
            0x52, 0x49, 0x46, 0x46,  // RIFF
            0x00, 0x00, 0x00, 0x00,  // file size (placeholder)
            0x57, 0x45, 0x42, 0x50   // WEBP
        };
        
        String detectedType = FileTypeDetector.detectFileType(webpHeader);
        
        assertEquals("image/webp", detectedType);
    }
    
    @Test
    void testDetectMp4() {
        // MP4 魔数: 00 00 00 xx 66 74 79 70 (....ftyp)
        byte[] mp4Header = {
            0x00, 0x00, 0x00, 0x20,  // box size
            0x66, 0x74, 0x79, 0x70,  // ftyp
            0x69, 0x73, 0x6F, 0x6D   // isom
        };
        
        String detectedType = FileTypeDetector.detectFileType(mp4Header);
        
        assertEquals("video/mp4", detectedType);
    }
    
    @Test
    void testDetectZip() {
        // ZIP 魔数: 50 4B 03 04 (PK..)
        byte[] zipHeader = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00};
        
        String detectedType = FileTypeDetector.detectFileType(zipHeader);
        
        assertEquals("application/zip", detectedType);
    }
    
    @Test
    void testDetectUnknownType() {
        // 未知文件类型
        byte[] unknownHeader = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05};
        
        String detectedType = FileTypeDetector.detectFileType(unknownHeader);
        
        assertNull(detectedType);
    }
    
    @Test
    void testDetectWithNullHeader() {
        String detectedType = FileTypeDetector.detectFileType(null);
        
        assertNull(detectedType);
    }
    
    @Test
    void testDetectWithInsufficientData() {
        // 数据不足 4 字节
        byte[] shortHeader = {0x00, 0x01};
        
        String detectedType = FileTypeDetector.detectFileType(shortHeader);
        
        assertNull(detectedType);
    }
    
    @Test
    void testIsTypeMatch_ExactMatch() {
        assertTrue(FileTypeDetector.isTypeMatch("image/jpeg", "image/jpeg"));
        assertTrue(FileTypeDetector.isTypeMatch("image/png", "image/png"));
        assertTrue(FileTypeDetector.isTypeMatch("application/pdf", "application/pdf"));
    }
    
    @Test
    void testIsTypeMatch_JpegVariants() {
        // JPEG 可以或image/jpg 或image/jpeg
        assertTrue(FileTypeDetector.isTypeMatch("image/jpg", "image/jpeg"));
        assertTrue(FileTypeDetector.isTypeMatch("image/jpeg", "image/jpg"));
        assertTrue(FileTypeDetector.isTypeMatch("image/jpeg", "image/jpeg"));
    }
    
    @Test
    void testIsTypeMatch_WithCharset() {
        // Content-Type 可能包含 charset 参数
        assertTrue(FileTypeDetector.isTypeMatch("image/jpeg; charset=utf-8", "image/jpeg"));
        assertTrue(FileTypeDetector.isTypeMatch("application/pdf; charset=binary", "application/pdf"));
    }
    
    @Test
    void testIsTypeMatch_OfficeDocuments() {
        // Office 文档（DOCX/XLSX）检测为 ZIP
        assertTrue(FileTypeDetector.isTypeMatch(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/zip"
        ));
        assertTrue(FileTypeDetector.isTypeMatch(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/zip"
        ));
        assertTrue(FileTypeDetector.isTypeMatch(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/zip"
        ));
    }
    
    @Test
    void testIsTypeMatch_Mismatch() {
        assertFalse(FileTypeDetector.isTypeMatch("image/jpeg", "image/png"));
        assertFalse(FileTypeDetector.isTypeMatch("image/png", "application/pdf"));
        assertFalse(FileTypeDetector.isTypeMatch("video/mp4", "image/jpeg"));
    }
    
    @Test
    void testIsTypeMatch_NullValues() {
        assertFalse(FileTypeDetector.isTypeMatch(null, "image/jpeg"));
        assertFalse(FileTypeDetector.isTypeMatch("image/jpeg", null));
        assertFalse(FileTypeDetector.isTypeMatch(null, null));
    }
    
    @Test
    void testIsTypeMatch_CaseInsensitive() {
        assertTrue(FileTypeDetector.isTypeMatch("IMAGE/JPEG", "image/jpeg"));
        assertTrue(FileTypeDetector.isTypeMatch("image/jpeg", "IMAGE/JPEG"));
        assertTrue(FileTypeDetector.isTypeMatch("Application/PDF", "application/pdf"));
    }
    
    @Test
    void testDetectJpegWithDifferentMarkers() {
        // JPEG 可能有不同的标记（JFIF, EXIF或
        byte[] jpegJfif = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}; // JFIF
        byte[] jpegExif = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1}; // EXIF
        
        assertEquals("image/jpeg", FileTypeDetector.detectFileType(jpegJfif));
        assertEquals("image/jpeg", FileTypeDetector.detectFileType(jpegExif));
    }
    
    @Test
    void testDetectGif87a() {
        // GIF87a 版本
        byte[] gif87a = {0x47, 0x49, 0x46, 0x38, 0x37, 0x61}; // GIF87a
        
        String detectedType = FileTypeDetector.detectFileType(gif87a);
        
        assertEquals("image/gif", detectedType);
    }
    
    @Test
    void testDetectWithExtraData() {
        // 测试文件头后面有额外数据的情或
        byte[] jpegWithExtra = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
            0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00
        };
        
        String detectedType = FileTypeDetector.detectFileType(jpegWithExtra);
        
        assertEquals("image/jpeg", detectedType);
    }
    
    @Test
    void testFakeExtensionDetectiExcepException() {
        // 模拟伪造扩展名的情况：文件声称或JPEG，但实际或PNG
        byte[] pngHeader = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        
        String detectedType = FileTypeDetector.detectFileType(pngHeader);
        assertEquals("image/png", detectedType);
        
        // 验证类型不匹或
        assertFalse(FileTypeDetector.isTypeMatch("image/jpeg", detectedType));
    }
    
    @Test
    void testExecutableFileDetectiExcepException() {
        // 测试可执行文件（EXE）不会被误识别为图片
        // Windows EXE 魔数: 4D 5A (MZ)
        byte[] exeHeader = {0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00};
        
        String detectedType = FileTypeDetector.detectFileType(exeHeader);
        
        // 应该返回 null（不支持的类型）
        assertNull(detectedType);
    }
}
