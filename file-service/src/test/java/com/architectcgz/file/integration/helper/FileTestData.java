package com.architectcgz.file.integration.helper;

import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * 测试数据生成工具类
 * 用于创建各种类型的测试文件
 */
public class FileTestData {
    
    private static final Random RANDOM = new Random();
    
    /**
     * 创建文本文件
     * 
     * @param filename 文件名
     * @param content 文件内容
     * @return MockMultipartFile
     */
    public static MockMultipartFile createTextFile(String filename, String content) {
        return new MockMultipartFile(
            "file",
            filename,
            "text/plain",
            content.getBytes(StandardCharsets.UTF_8)
        );
    }
    
    /**
     * 创建默认文本文件
     * 
     * @param filename 文件名
     * @return MockMultipartFile
     */
    public static MockMultipartFile createTextFile(String filename) {
        String content = "This is a test file for RustFS integration testing.\n" +
                        "Line 2: Testing file upload and storage.\n" +
                        "Line 3: Verifying content integrity.";
        return createTextFile(filename, content);
    }
    
    /**
     * 创建图片文件（JPEG格式）
     * 生成一个简单的JPEG文件头和数据
     * 
     * @param filename 文件名
     * @return MockMultipartFile
     */
    public static MockMultipartFile createImageFile(String filename) {
        // 创建一个最小的JPEG文件
        // JPEG文件以 FF D8 开始，以 FF D9 结束
        byte[] jpegData = createMinimalJpegData();
        
        return new MockMultipartFile(
            "file",
            filename,
            "image/jpeg",
            jpegData
        );
    }
    
    /**
     * 创建PNG图片文件
     * 
     * @param filename 文件名
     * @return MockMultipartFile
     */
    public static MockMultipartFile createPngImageFile(String filename) {
        // 创建一个最小的PNG文件
        byte[] pngData = createMinimalPngData();
        
        return new MockMultipartFile(
            "file",
            filename,
            "image/png",
            pngData
        );
    }
    
    /**
     * 创建大文件
     * 
     * @param filename 文件名
     * @param sizeInMB 文件大小（MB）
     * @return MockMultipartFile
     */
    public static MockMultipartFile createLargeFile(String filename, long sizeInMB) {
        long sizeInBytes = sizeInMB * 1024 * 1024;
        byte[] data = generateRandomBytes((int) sizeInBytes);
        
        return new MockMultipartFile(
            "file",
            filename,
            "application/octet-stream",
            data
        );
    }
    
    /**
     * 生成随机字节数据
     * 
     * @param size 字节数
     * @return 随机字节数组
     */
    public static byte[] generateRandomBytes(int size) {
        byte[] data = new byte[size];
        RANDOM.nextBytes(data);
        return data;
    }
    
    /**
     * 创建二进制文件
     * 
     * @param filename 文件名
     * @param size 文件大小（字节）
     * @return MockMultipartFile
     */
    public static MockMultipartFile createBinaryFile(String filename, int size) {
        byte[] data = generateRandomBytes(size);
        
        return new MockMultipartFile(
            "file",
            filename,
            "application/octet-stream",
            data
        );
    }
    
    /**
     * 创建最小的JPEG文件数据
     * 包含JPEG文件头和结束标记
     * 
     * @return JPEG字节数组
     */
    private static byte[] createMinimalJpegData() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            // JPEG SOI (Start of Image) marker
            baos.write(0xFF);
            baos.write(0xD8);
            
            // JFIF APP0 marker
            baos.write(0xFF);
            baos.write(0xE0);
            baos.write(0x00);
            baos.write(0x10); // Length
            baos.write("JFIF".getBytes(StandardCharsets.US_ASCII));
            baos.write(0x00); // Null terminator
            baos.write(0x01); // Version 1
            baos.write(0x01); // Version 1
            baos.write(0x00); // Density units
            baos.write(0x00);
            baos.write(0x01); // X density
            baos.write(0x00);
            baos.write(0x01); // Y density
            baos.write(0x00); // Thumbnail width
            baos.write(0x00); // Thumbnail height
            
            // Add some random data to make it more realistic
            byte[] randomData = generateRandomBytes(100);
            baos.write(randomData);
            
            // JPEG EOI (End of Image) marker
            baos.write(0xFF);
            baos.write(0xD9);
            
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create JPEG data", e);
        }
    }
    
    /**
     * 创建最小的PNG文件数据
     * 包含PNG文件头和基本块
     * 
     * @return PNG字节数组
     */
    private static byte[] createMinimalPngData() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            // PNG signature
            baos.write(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            
            // IHDR chunk (Image Header)
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x0D); // Length: 13 bytes
            baos.write("IHDR".getBytes(StandardCharsets.US_ASCII));
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x01); // Width: 1
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x01); // Height: 1
            baos.write(0x08); // Bit depth: 8
            baos.write(0x02); // Color type: RGB
            baos.write(0x00); // Compression: deflate
            baos.write(0x00); // Filter: adaptive
            baos.write(0x00); // Interlace: none
            // CRC (simplified - not a real CRC)
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00);
            
            // IEND chunk (Image End)
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00); // Length: 0
            baos.write("IEND".getBytes(StandardCharsets.US_ASCII));
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00); // CRC
            
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create PNG data", e);
        }
    }
}
