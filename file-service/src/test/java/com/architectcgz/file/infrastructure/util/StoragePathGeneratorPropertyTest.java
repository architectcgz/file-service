package com.architectcgz.file.infrastructure.util;

import net.jqwik.api.*;

import java.time.LocalDate;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StoragePathGenerator 属性测试
 * 
 * Feature: file-service-optimization
 * 使用基于属性的测试验证存储路径生成的正确性属性
 */
class StoragePathGeneratorPropertyTest {

    private final StoragePathGenerator pathGenerator = new StoragePathGenerator();

    /**
     * Feature: file-service-optimization, Property 29: 存储路径格式正确性
     * 
     * 属性：对于任何文件上传操作，生成的存储路径应该符合格式 
     * `{tenantId}/{year}/{month}/{day}/{userId}/{type}/{fileId}.{ext}`，
     * 其中日期部分使用上传时的日期，type 根据 MIME 类型确定。
     * 
     * 验证需求：11.1
     */
    @Property(tries = 100)
    @Label("Property 29: 存储路径格式正确性 - 路径符合指定格式")
    void storagePathFormatCorrectness(
            @ForAll("tenantIds") String tenantId,
            @ForAll("userIds") String userId,
            @ForAll("fileTypes") String fileType,
            @ForAll("fileNames") String fileName
    ) {
        // When: 生成存储路径
        String path = pathGenerator.generateStoragePath(tenantId, userId, fileType, fileName);
        
        // Then: 验证路径不为空
        assertNotNull(path, "Generated path should not be null");
        assertFalse(path.isEmpty(), "Generated path should not be empty");
        
        // 验证路径格式：{tenantId}/{year}/{month}/{day}/{userId}/{type}/{fileId}.{ext}
        String[] parts = path.split("/");
        assertTrue(parts.length >= 7, 
                "Path should have at least 7 parts: " + path);
        
        // 验证各部分内容
        assertEquals(tenantId, parts[0], 
                "First part should be tenantId");
        
        // 验证日期部分使用当前日期
        LocalDate now = LocalDate.now();
        assertEquals(String.valueOf(now.getYear()), parts[1], 
                "Year should match current year");
        assertEquals(String.format("%02d", now.getMonthValue()), parts[2], 
                "Month should match current month with zero padding");
        assertEquals(String.format("%02d", now.getDayOfMonth()), parts[3], 
                "Day should match current day with zero padding");
        
        assertEquals(userId, parts[4], 
                "Fifth part should be userId");
        assertEquals(fileType, parts[5], 
                "Sixth part should be fileType");
        
        // 验证文件名部分：{fileId}.{ext}
        String fileNamePart = parts[6];
        assertTrue(fileNamePart.contains("."), 
                "File name should contain extension separator");
        
        // 验证 UUID 格式（UUIDv7 格式）
        String fileIdWithExt = fileNamePart;
        int lastDotIndex = fileIdWithExt.lastIndexOf('.');
        if (lastDotIndex > 0) {
            String fileId = fileIdWithExt.substring(0, lastDotIndex);
            // UUID 格式：8-4-4-4-12 个十六进制字符
            Pattern uuidPattern = Pattern.compile(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            assertTrue(uuidPattern.matcher(fileId).matches(), 
                    "File ID should be a valid UUID: " + fileId);
        }
        
        // 验证扩展名是小写
        String extension = pathGenerator.getExtension(fileName);
        if (!extension.isEmpty()) {
            assertTrue(path.endsWith("." + extension.toLowerCase()), 
                    "Extension should be lowercase: " + path);
        }
    }

    /**
     * Feature: file-service-optimization, Property 29: 存储路径格式正确性（使用 inferFileType）
     * 
     * 属性：对于任何文件上传操作，当使用 MIME 类型推断文件类型时，
     * 生成的存储路径中的 type 部分应该正确反映 MIME 类型的分类。
     * 
     * 验证需求：11.1
     */
    @Property(tries = 100)
    @Label("Property 29: 存储路径格式正确性 - MIME 类型推断")
    void storagePathFormatWithInferredType(
            @ForAll("tenantIds") String tenantId,
            @ForAll("userIds") String userId,
            @ForAll("contentTypes") String contentType,
            @ForAll("fileNames") String fileName
    ) {
        // Given: 根据 MIME 类型推断文件类型
        String inferredType = pathGenerator.inferFileType(contentType);
        
        // When: 生成存储路径
        String path = pathGenerator.generateStoragePath(tenantId, userId, inferredType, fileName);
        
        // Then: 验证路径包含推断的文件类型
        assertTrue(path.contains("/" + inferredType + "/"), 
                "Path should contain inferred file type: " + inferredType);
        
        // 验证 MIME 类型推断的正确性
        if (contentType != null) {
            if (contentType.startsWith("image/")) {
                assertEquals("images", inferredType, 
                        "Image MIME types should map to 'images'");
            } else if (contentType.startsWith("video/")) {
                assertEquals("videos", inferredType, 
                        "Video MIME types should map to 'videos'");
            } else if (contentType.startsWith("audio/")) {
                assertEquals("audios", inferredType, 
                        "Audio MIME types should map to 'audios'");
            } else {
                assertEquals("files", inferredType, 
                        "Other MIME types should map to 'files'");
            }
        } else {
            assertEquals("files", inferredType, 
                    "Null MIME type should map to 'files'");
        }
    }

    /**
     * Feature: file-service-optimization, Property 29: 存储路径格式正确性（路径唯一性）
     * 
     * 属性：对于任何相同的输入参数，连续生成的存储路径应该是唯一的，
     * 因为每个路径包含唯一的 UUID。
     * 
     * 验证需求：11.1
     */
    @Property(tries = 100)
    @Label("Property 29: 存储路径格式正确性 - 路径唯一性")
    void storagePathUniqueness(
            @ForAll("tenantIds") String tenantId,
            @ForAll("userIds") String userId,
            @ForAll("fileTypes") String fileType,
            @ForAll("fileNames") String fileName
    ) {
        // When: 连续生成两个路径
        String path1 = pathGenerator.generateStoragePath(tenantId, userId, fileType, fileName);
        String path2 = pathGenerator.generateStoragePath(tenantId, userId, fileType, fileName);
        
        // Then: 路径应该不同（因为 UUID 不同）
        assertNotEquals(path1, path2, 
                "Consecutive paths should be unique due to different UUIDs");
        
        // 但是除了 UUID 部分，其他部分应该相同
        String path1WithoutUuid = path1.substring(0, path1.lastIndexOf('/') + 1);
        String path2WithoutUuid = path2.substring(0, path2.lastIndexOf('/') + 1);
        assertEquals(path1WithoutUuid, path2WithoutUuid, 
                "Path prefix (without UUID) should be the same");
    }

    /**
     * Feature: file-service-optimization, Property 29: 存储路径格式正确性（扩展名处理）
     * 
     * 属性：对于任何文件名，生成的存储路径中的扩展名应该是小写的，
     * 并且与原始文件名的扩展名匹配（忽略大小写）。
     * 
     * 验证需求：11.1
     */
    @Property(tries = 100)
    @Label("Property 29: 存储路径格式正确性 - 扩展名处理")
    void storagePathExtensionHandling(
            @ForAll("tenantIds") String tenantId,
            @ForAll("userIds") String userId,
            @ForAll("fileTypes") String fileType,
            @ForAll("fileNamesWithExtension") String fileName
    ) {
        // Given: 提取原始扩展名
        String originalExtension = pathGenerator.getExtension(fileName);
        
        // When: 生成存储路径
        String path = pathGenerator.generateStoragePath(tenantId, userId, fileType, fileName);
        
        // Then: 验证扩展名处理
        if (!originalExtension.isEmpty()) {
            // 路径应该以小写扩展名结尾
            assertTrue(path.endsWith("." + originalExtension.toLowerCase()), 
                    "Path should end with lowercase extension: " + originalExtension.toLowerCase());
            
            // 扩展名应该是小写
            String pathExtension = path.substring(path.lastIndexOf('.') + 1);
            assertEquals(pathExtension, pathExtension.toLowerCase(), 
                    "Extension in path should be lowercase");
            
            // 扩展名应该匹配原始扩展名（忽略大小写）
            assertEquals(originalExtension.toLowerCase(), pathExtension, 
                    "Extension should match original (case-insensitive)");
        }
    }

    // ========== Arbitraries (数据生成器) ==========

    /**
     * 生成租户 ID
     * 格式：3-20 个字符，包含字母、数字、连字符和下划线
     */
    @Provide
    Arbitrary<String> tenantIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars('-', '_')
                .ofMinLength(3)
                .ofMaxLength(20)
                .filter(s -> !s.isEmpty() && Character.isLetterOrDigit(s.charAt(0)));
    }

    /**
     * 生成用户 ID
     * 格式：1-20 个字符，包含字母和数字
     */
    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(20)
                .filter(s -> !s.isEmpty());
    }

    /**
     * 生成文件类型
     * 常见的文件类型目录名
     */
    @Provide
    Arbitrary<String> fileTypes() {
        return Arbitraries.of("images", "files", "videos", "audios", "thumbnails", "documents");
    }

    /**
     * 生成文件名
     * 包含各种扩展名的文件名
     */
    @Provide
    Arbitrary<String> fileNames() {
        Arbitrary<String> baseName = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('-', '_', '.')
                .ofMinLength(1)
                .ofMaxLength(50);
        
        Arbitrary<String> extension = Arbitraries.of(
                "jpg", "JPG", "png", "PNG", "gif", "GIF",
                "pdf", "PDF", "txt", "TXT", "doc", "docx",
                "mp4", "MP4", "avi", "mov",
                "mp3", "MP3", "wav", "ogg",
                "zip", "tar", "gz",
                "" // 无扩展名的情况
        );
        
        return Combinators.combine(baseName, extension)
                .as((name, ext) -> ext.isEmpty() ? name : name + "." + ext);
    }

    /**
     * 生成带扩展名的文件名（确保有扩展名）
     */
    @Provide
    Arbitrary<String> fileNamesWithExtension() {
        Arbitrary<String> baseName = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('-', '_')
                .ofMinLength(1)
                .ofMaxLength(50);
        
        Arbitrary<String> extension = Arbitraries.of(
                "jpg", "JPG", "png", "PNG", "gif", "GIF",
                "pdf", "PDF", "txt", "TXT", "doc", "docx",
                "mp4", "MP4", "avi", "mov",
                "mp3", "MP3", "wav", "ogg"
        );
        
        return Combinators.combine(baseName, extension)
                .as((name, ext) -> name + "." + ext);
    }

    /**
     * 生成内容类型（MIME 类型）
     */
    @Provide
    Arbitrary<String> contentTypes() {
        return Arbitraries.of(
                "image/jpeg",
                "image/png",
                "image/gif",
                "image/webp",
                "image/svg+xml",
                "video/mp4",
                "video/mpeg",
                "video/quicktime",
                "video/x-msvideo",
                "audio/mpeg",
                "audio/wav",
                "audio/ogg",
                "audio/webm",
                "application/pdf",
                "application/zip",
                "application/x-tar",
                "text/plain",
                "text/html",
                "text/csv",
                "application/json",
                "application/octet-stream",
                null // 测试 null 情况
        );
    }
}
