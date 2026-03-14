package com.architectcgz.file.integration.helper;

import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
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
        byte[] jpegData = createMinimalImageData("jpg");
        
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
        byte[] pngData = createMinimalImageData("png");
        
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
    private static byte[] createMinimalImageData(String format) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, 0x3366CC);
            if (!ImageIO.write(image, format, baos)) {
                throw new IOException("No ImageIO writer for format: " + format);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create image data for format: " + format, e);
        }
    }
}
