package com.architectcgz.file.infrastructure.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * File type detector
 * Detect actual file type via magic number to prevent extension spoofing
 */
@Slf4j
public class FileTypeDetector {
    
    // JPEG 魔数: FF D8 FF
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    
    // PNG 魔数: 89 50 4E 47 0D 0A 1A 0A
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    
    // GIF 魔数: 47 49 46 38 (GIF8)
    private static final byte[] GIF_MAGIC = {0x47, 0x49, 0x46, 0x38};
    
    // PDF 魔数: 25 50 44 46 (%PDF)
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46};
    
    // WebP 魔数: 52 49 46 46 xx xx xx xx 57 45 42 50 (RIFF....WEBP)
    private static final byte[] WEBP_RIFF = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_WEBP = {0x57, 0x45, 0x42, 0x50};
    
    // MP4 魔数: 00 00 00 xx 66 74 79 70 (....ftyp)
    private static final byte[] MP4_FTYP = {0x66, 0x74, 0x79, 0x70};
    
    // ZIP 魔数: 50 4B 03 04 (PK..)
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    
    // DOCX/XLSX/PPTX 也是 ZIP 格式，需要进一步检查
    private static final byte[] DOCX_MAGIC = ZIP_MAGIC;
    
    /**
     * 检测文件类型
     * 
     * @param fileHeader 文件头字节数组（至少 12 字节）
     * @return 检测到的 MIME 类型，如果无法识别返回 null
     */
    public static String detectFileType(byte[] fileHeader) {
        if (fileHeader == null || fileHeader.length < 4) {
            log.warn("文件头数据不足，无法检测文件类型");
            return null;
        }
        
        // JPEG
        if (startsWith(fileHeader, JPEG_MAGIC)) {
            log.debug("检测到 JPEG 文件");
            return "image/jpeg";
        }
        
        // PNG
        if (startsWith(fileHeader, PNG_MAGIC)) {
            log.debug("检测到 PNG 文件");
            return "image/png";
        }
        
        // GIF
        if (startsWith(fileHeader, GIF_MAGIC)) {
            log.debug("检测到 GIF 文件");
            return "image/gif";
        }
        
        // PDF
        if (startsWith(fileHeader, PDF_MAGIC)) {
            log.debug("检测到 PDF 文件");
            return "application/pdf";
        }
        
        // WebP (需要检查 RIFF 和 WEBP 标识)
        if (fileHeader.length >= 12 && 
            startsWith(fileHeader, WEBP_RIFF) && 
            matchesAt(fileHeader, 8, WEBP_WEBP)) {
            log.debug("检测到 WebP 文件");
            return "image/webp";
        }
        
        // MP4 (检查 ftyp box)
        if (fileHeader.length >= 8 && matchesAt(fileHeader, 4, MP4_FTYP)) {
            log.debug("检测到 MP4 文件");
            return "video/mp4";
        }
        
        // ZIP/DOCX/XLSX (ZIP 格式)
        if (startsWith(fileHeader, ZIP_MAGIC)) {
            log.debug("检测到 ZIP 或 Office 文档");
            // 简单返回 ZIP，实际应用中可以进一步检查 ZIP 内容判断是否为 Office 文档
            return "application/zip";
        }
        
        log.warn("无法识别文件类型，魔数: {}", bytesToHex(Arrays.copyOf(fileHeader, Math.min(12, fileHeader.length))));
        return null;
    }
    
    /**
     * 检查文件类型是否匹配
     * 
     * @param declaredType 声明的 Content-Type
     * @param detectedType 检测到的文件类型
     * @return 是否匹配
     */
    public static boolean isTypeMatch(String declaredType, String detectedType) {
        if (declaredType == null || detectedType == null) {
            return false;
        }
        
        // 移除可能的参数（如 charset）
        String declaredMime = declaredType.split(";")[0].trim().toLowerCase();
        String detectedMime = detectedType.toLowerCase();
        
        // 完全匹配
        if (declaredMime.equals(detectedMime)) {
            return true;
        }
        
        // 特殊情况：JPEG 可能声明为 image/jpg 或 image/jpeg
        if ((declaredMime.equals("image/jpg") || declaredMime.equals("image/jpeg")) &&
            (detectedMime.equals("image/jpg") || detectedMime.equals("image/jpeg"))) {
            return true;
        }
        
        // 特殊情况：Office 文档（DOCX/XLSX）检测为 ZIP
        if (detectedMime.equals("application/zip") && 
            (declaredMime.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
             declaredMime.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") ||
             declaredMime.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation"))) {
            return true;
        }
        
        log.warn("文件类型不匹配: 声明={}, 检测={}", declaredMime, detectedMime);
        return false;
    }
    
    /**
     * 检查字节数组是否以指定魔数开头
     * 
     * @param data 数据
     * @param magic 魔数
     * @return 是否匹配
     */
    private static boolean startsWith(byte[] data, byte[] magic) {
        if (data.length < magic.length) {
            return false;
        }
        
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 检查字节数组在指定位置是否匹配魔数
     * 
     * @param data 数据
     * @param offset 偏移量
     * @param magic 魔数
     * @return 是否匹配
     */
    private static boolean matchesAt(byte[] data, int offset, byte[] magic) {
        if (data.length < offset + magic.length) {
            return false;
        }
        
        for (int i = 0; i < magic.length; i++) {
            if (data[offset + i] != magic[i]) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 将字节数组转换为十六进制字符串（用于日志）
     * 
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}
