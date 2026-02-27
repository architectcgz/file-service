package com.architectcgz.file.infrastructure.storage;

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LocalStorageService 单元测试
 * 
 * 测试本地存储服务功能
 */
@DisplayName("LocalStorageService 测试")
class LocalStorageServiceTest {

    private LocalStorageService storageService;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("upload-test");
        storageService = new LocalStorageService();
        ReflectionTestUtils.setField(storageService, "basePath", tempDir.toString());
        ReflectionTestUtils.setField(storageService, "baseUrl", "http://localhost:8089/files");
        storageService.init();
    }

    @AfterEach
    void tearDown() throws IOException {
        // 清理临时目录
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // ignore
                        }
                    });
        }
    }

    @Nested
    @DisplayName("上传文件")
    class UploadFile {

        @Test
        @DisplayName("应该成功上传文件")
        void shouldUploadFileSuccessfully() {
            // Given
            byte[] data = "test content".getBytes();
            String path = "test/file.txt";

            // When
            String url = storageService.upload(data, path);

            // Then
            assertNotNull(url);
            assertTrue(url.contains("http://localhost:8089/files"));
            assertTrue(url.contains(path));
            assertTrue(storageService.exists(path));
        }

        @Test
        @DisplayName("应该自动创建父目录")
        void shouldCreateParentDirectories() {
            // Given
            byte[] data = "test content".getBytes();
            String path = "deep/nested/directory/file.txt";

            // When
            String url = storageService.upload(data, path);

            // Then
            assertNotNull(url);
            assertTrue(storageService.exists(path));
        }

        @Test
        @DisplayName("应该支持指定内容类型")
        void shouldSupportContentType() {
            // Given
            byte[] data = "test content".getBytes();
            String path = "test/file.txt";
            String contentType = "text/plain";

            // When
            String url = storageService.upload(data, path, contentType);

            // Then
            assertNotNull(url);
            assertTrue(storageService.exists(path));
        }
    }

    @Nested
    @DisplayName("删除文件")
    class DeleteFile {

        @Test
        @DisplayName("应该成功删除文件")
        void shouldDeleteFileSuccessfully() {
            // Given
            byte[] data = "test content".getBytes();
            String path = "test/file.txt";
            storageService.upload(data, path);
            assertTrue(storageService.exists(path));

            // When
            storageService.delete(path);

            // Then
            assertFalse(storageService.exists(path));
        }

        @Test
        @DisplayName("删除不存在的文件不应该抛出异常")
        void shouldNotThrowExceptionWhenDeletingNonExistentFile() {
            // Given
            String path = "non/existent/file.txt";

            // When & Then
            assertDoesNotThrow(() -> storageService.delete(path));
        }
    }

    @Nested
    @DisplayName("获取URL")
    class GetUrl {

        @Test
        @DisplayName("应该返回正确的URL")
        void shouldReturnCorrectUrl() {
            // Given
            String path = "images/test.jpg";

            // When
            String url = storageService.getUrl(path);

            // Then
            assertEquals("http://localhost:8089/files/images/test.jpg", url);
        }
    }

    @Nested
    @DisplayName("检查文件存在")
    class FileExists {

        @Test
        @DisplayName("存在的文件应该返回true")
        void shouldReturnTrueForExistingFile() {
            // Given
            byte[] data = "test content".getBytes();
            String path = "test/file.txt";
            storageService.upload(data, path);

            // When
            boolean exists = storageService.exists(path);

            // Then
            assertTrue(exists);
        }

        @Test
        @DisplayName("不存在的文件应该返回false")
        void shouldReturnFalseForNonExistentFile() {
            // Given
            String path = "non/existent/file.txt";

            // When
            boolean exists = storageService.exists(path);

            // Then
            assertFalse(exists);
        }
    }
}
