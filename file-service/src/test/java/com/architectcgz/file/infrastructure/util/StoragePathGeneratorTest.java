package com.architectcgz.file.infrastructure.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StoragePathGenerator 单元测试
 */
class StoragePathGeneratorTest {

    private StoragePathGenerator pathGenerator;

    @BeforeEach
    void setUp() {
        pathGenerator = new StoragePathGenerator();
    }

    @Test
    void testGenerateStoragePath_ValidInput() {
        // Given
        String appId = "blog";
        String userId = "12345";
        String fileType = "images";
        String originalFilename = "test-image.jpg";

        // When
        String path = pathGenerator.generateStoragePath(appId, userId, fileType, originalFilename);

        // Then
        assertNotNull(path);
        assertTrue(path.endsWith(".jpg"));
        assertTrue(path.contains("/12345/"));
        assertTrue(path.startsWith("blog/"));
        assertTrue(path.contains("/images/"));

        // 验证路径格式：{appId}/{year}/{month}/{day}/{userId}/{type}/{fileId}.{ext}
        LocalDate now = LocalDate.now();
        String expectedPrefix = String.format("%s/%d/%02d/%02d/%s/%s/",
                appId,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                userId,
                fileType
        );
        assertTrue(path.startsWith(expectedPrefix));

        // 验证 UUID 格式：36个字符，包含4个连字符
        String fileIdPart = path.substring(expectedPrefix.length(), path.lastIndexOf('.'));
        assertEquals(36, fileIdPart.length());
        assertTrue(fileIdPart.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    void testGenerateStoragePath_DifferentExtensions() {
        // Given
        String appId = "blog";
        String userId = "12345";
        String fileType = "images";

        // When & Then
        String jpgPath = pathGenerator.generateStoragePath(appId, userId, fileType, "image.jpg");
        assertTrue(jpgPath.endsWith(".jpg"));

        String pngPath = pathGenerator.generateStoragePath(appId, userId, fileType, "image.PNG");
        assertTrue(pngPath.endsWith(".png")); // 应该转换为小写

        String pdfPath = pathGenerator.generateStoragePath(appId, userId, "files", "document.pdf");
        assertTrue(pdfPath.endsWith(".pdf"));

        String mp4Path = pathGenerator.generateStoragePath(appId, userId, "videos", "video.mp4");
        assertTrue(mp4Path.endsWith(".mp4"));
    }

    @Test
    void testGenerateStoragePath_NoExtension() {
        // Given
        String appId = "blog";
        String userId = "12345";
        String fileType = "files";
        String originalFilename = "file-without-extension";

        // When
        String path = pathGenerator.generateStoragePath(appId, userId, fileType, originalFilename);

        // Then
        assertNotNull(path);
        assertTrue(path.endsWith("."));
    }

    @Test
    void testGenerateStoragePath_MultipleDotsInFileName() {
        // Given
        String appId = "blog";
        String userId = "12345";
        String fileType = "images";
        String originalFilename = "my.file.name.with.dots.jpg";

        // When
        String path = pathGenerator.generateStoragePath(appId, userId, fileType, originalFilename);

        // Then
        assertNotNull(path);
        assertTrue(path.endsWith(".jpg"));
    }

    @Test
    void testGenerateStoragePath_UniquePaths() {
        // Given
        String appId = "blog";
        String userId = "12345";
        String fileType = "images";
        String originalFilename = "test.jpg";
        Set<String> paths = new HashSet<>();

        // When - 生成多个路径
        for (int i = 0; i < 100; i++) {
            String path = pathGenerator.generateStoragePath(appId, userId, fileType, originalFilename);
            paths.add(path);
        }

        // Then - 所有路径应该是唯一的（UUIDv7 保证唯一性）
        assertEquals(100, paths.size());
    }

    @Test
    void testGetExtension_ValidFileName() {
        // When & Then
        assertEquals("jpg", pathGenerator.getExtension("image.jpg"));
        assertEquals("png", pathGenerator.getExtension("image.PNG"));
        assertEquals("pdf", pathGenerator.getExtension("document.pdf"));
        assertEquals("mp4", pathGenerator.getExtension("video.mp4"));
        assertEquals("webp", pathGenerator.getExtension("image.webp"));
    }

    @Test
    void testGetExtension_NoExtension() {
        // When & Then
        assertEquals("", pathGenerator.getExtension("file-without-extension"));
        assertEquals("", pathGenerator.getExtension("file."));
    }

    @Test
    void testGetExtension_NullOrEmpty() {
        // When & Then
        assertEquals("", pathGenerator.getExtension(null));
        assertEquals("", pathGenerator.getExtension(""));
    }

    @Test
    void testGetExtension_MultipleDotsInFileName() {
        // When & Then
        assertEquals("jpg", pathGenerator.getExtension("my.file.name.jpg"));
        assertEquals("gz", pathGenerator.getExtension("archive.tar.gz")); // 只取最后一个扩展名
    }

    @Test
    void testGetExtension_CaseInsensitive() {
        // When & Then
        assertEquals("jpg", pathGenerator.getExtension("IMAGE.JPG"));
        assertEquals("png", pathGenerator.getExtension("Image.PNG"));
        assertEquals("pdf", pathGenerator.getExtension("Document.PDF"));
    }

    @Test
    void testGenerateStoragePathWithExtension_ValidInput() {
        // Given
        String appId = "blog";
        String userId = "12345";
        String fileType = "images";
        String extension = "jpg";

        // When
        String path = pathGenerator.generateStoragePathWithExtension(appId, userId, fileType, extension);

        // Then
        assertNotNull(path);
        assertTrue(path.endsWith(".jpg"));
        assertTrue(path.contains("/12345/"));
        assertTrue(path.startsWith("blog/"));
        assertTrue(path.contains("/images/"));

        // 验证路径格式
        LocalDate now = LocalDate.now();
        String expectedPrefix = String.format("%s/%d/%02d/%02d/%s/%s/",
                appId,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                userId,
                fileType
        );
        assertTrue(path.startsWith(expectedPrefix));
    }

    @Test
    void testGenerateStoragePathWithExtension_UppercaseExtension() {
        // Given
        String appId = "blog";
        String userId = "12345";
        String fileType = "images";
        String extension = "PNG";

        // When
        String path = pathGenerator.generateStoragePathWithExtension(appId, userId, fileType, extension);

        // Then
        assertTrue(path.endsWith(".png")); // 应该转换为小写
    }

    @Test
    void testGenerateStoragePathWithExtension_UniquePaths() {
        // Given
        String appId = "blog";
        String userId = "12345";
        String fileType = "images";
        String extension = "jpg";
        Set<String> paths = new HashSet<>();

        // When - 生成多个路径
        for (int i = 0; i < 100; i++) {
            String path = pathGenerator.generateStoragePathWithExtension(appId, userId, fileType, extension);
            paths.add(path);
        }

        // Then - 所有路径应该是唯一的
        assertEquals(100, paths.size());
    }

    @Test
    void testPathFormat_MatchesExpectedPattern() {
        // Given
        String appId = "blog";
        String userId = "12345";
        String fileType = "images";
        String originalFilename = "test.jpg";

        // When
        String path = pathGenerator.generateStoragePath(appId, userId, fileType, originalFilename);

        // Then - 验证路径格式：{appId}/{year}/{month}/{day}/{userId}/{type}/{uuid}.{ext}
        Pattern pattern = Pattern.compile("[a-z0-9_-]+/\\d{4}/\\d{2}/\\d{2}/[a-z0-9_-]+/[a-z]+/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.[a-z0-9]+");
        assertTrue(pattern.matcher(path).matches(), "Path should match expected format: " + path);
    }

    @Test
    void testPathFormat_DateComponents() {
        // Given
        String appId = "blog";
        String userId = "12345";
        String fileType = "images";
        String originalFilename = "test.jpg";
        LocalDate now = LocalDate.now();

        // When
        String path = pathGenerator.generateStoragePath(appId, userId, fileType, originalFilename);

        // Then - 验证日期组件
        String[] parts = path.split("/");
        assertTrue(parts.length >= 7);
        assertEquals(appId, parts[0]);
        assertEquals(String.valueOf(now.getYear()), parts[1]);
        assertEquals(String.format("%02d", now.getMonthValue()), parts[2]);
        assertEquals(String.format("%02d", now.getDayOfMonth()), parts[3]);
        assertEquals(userId, parts[4]);
        assertEquals(fileType, parts[5]);
    }

    @Test
    void testPathFormat_DifferentUserIds() {
        // Given
        String appId = "blog";
        String fileType = "images";
        String originalFilename = "test.jpg";

        // When & Then
        String path1 = pathGenerator.generateStoragePath(appId, "1", fileType, originalFilename);
        assertTrue(path1.contains("/1/"));

        String path2 = pathGenerator.generateStoragePath(appId, "999999", fileType, originalFilename);
        assertTrue(path2.contains("/999999/"));

        String path3 = pathGenerator.generateStoragePath(appId, "12345", fileType, originalFilename);
        assertTrue(path3.contains("/12345/"));
    }

    @Test
    void testPathFormat_DifferentAppIds() {
        // Given
        String userId = "12345";
        String fileType = "images";
        String originalFilename = "test.jpg";

        // When & Then
        String blogPath = pathGenerator.generateStoragePath("blog", userId, fileType, originalFilename);
        assertTrue(blogPath.startsWith("blog/"));

        String imPath = pathGenerator.generateStoragePath("im", userId, fileType, originalFilename);
        assertTrue(imPath.startsWith("im/"));

        String customPath = pathGenerator.generateStoragePath("custom-app", userId, fileType, originalFilename);
        assertTrue(customPath.startsWith("custom-app/"));
    }

    @Test
    void testPathFormat_DifferentFileTypes() {
        // Given
        String appId = "blog";
        String userId = "12345";
        String originalFilename = "test.jpg";

        // When & Then
        String imagesPath = pathGenerator.generateStoragePath(appId, userId, "images", originalFilename);
        assertTrue(imagesPath.contains("/images/"));

        String filesPath = pathGenerator.generateStoragePath(appId, userId, "files", originalFilename);
        assertTrue(filesPath.contains("/files/"));

        String videosPath = pathGenerator.generateStoragePath(appId, userId, "videos", originalFilename);
        assertTrue(videosPath.contains("/videos/"));

        String thumbnailsPath = pathGenerator.generateStoragePath(appId, userId, "thumbnails", originalFilename);
        assertTrue(thumbnailsPath.contains("/thumbnails/"));
    }

    @Test
    void testInferFileType_Images() {
        // When & Then
        assertEquals("images", pathGenerator.inferFileType("image/jpeg"));
        assertEquals("images", pathGenerator.inferFileType("image/png"));
        assertEquals("images", pathGenerator.inferFileType("image/gif"));
        assertEquals("images", pathGenerator.inferFileType("image/webp"));
    }

    @Test
    void testInferFileType_Videos() {
        // When & Then
        assertEquals("videos", pathGenerator.inferFileType("video/mp4"));
        assertEquals("videos", pathGenerator.inferFileType("video/mpeg"));
        assertEquals("videos", pathGenerator.inferFileType("video/quicktime"));
    }

    @Test
    void testInferFileType_Audios() {
        // When & Then
        assertEquals("audios", pathGenerator.inferFileType("audio/mpeg"));
        assertEquals("audios", pathGenerator.inferFileType("audio/wav"));
        assertEquals("audios", pathGenerator.inferFileType("audio/ogg"));
    }

    @Test
    void testInferFileType_Files() {
        // When & Then
        assertEquals("files", pathGenerator.inferFileType("application/pdf"));
        assertEquals("files", pathGenerator.inferFileType("application/zip"));
        assertEquals("files", pathGenerator.inferFileType("text/plain"));
        assertEquals("files", pathGenerator.inferFileType(null));
        assertEquals("files", pathGenerator.inferFileType("unknown/type"));
    }
}
