package com.architectcgz.file.infrastructure.image;

import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.ImageProcessConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ImageProcessor 单元测试
 * 
 * 测试图片处理功能：压缩、缩略图生成、格式转或
 */
@DisplayName("ImageProcessor 测试")
class ImageProcessorTest {

    private ImageProcessor imageProcessor;

    @BeforeEach
    void setUp() {
        imageProcessor = new ImageProcessor();
    }

    /**
     * 创建测试用的图片数据
     */
    private byte[] createTestImage(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.BLUE);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.WHITE);
        g2d.drawString("Test", width / 2 - 20, height / 2);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    @Nested
    @DisplayName("图片处理")
    class ProcessImage {

        @Test
        @DisplayName("应该成功处理图片并压缩")
        void shouldProcessImageSuccessfully() throws IOException {
            // Given
            byte[] originalImage = createTestImage(800, 600);
            ImageProcessConfig config = ImageProcessConfig.builder()
                    .maxWidth(400)
                    .maxHeight(300)
                    .quality(0.8)
                    .convertToWebP(false)
                    .build();

            // When
            byte[] processedImage = imageProcessor.process(originalImage, config);

            // Then
            assertNotNull(processedImage);
            assertTrue(processedImage.length > 0);
        }

        @Test
        @DisplayName("应该保持宽高比")
        void shouldMaintainAspectRatio() throws IOException {
            // Given
            byte[] originalImage = createTestImage(1000, 500); // 2:1 ratio
            ImageProcessConfig config = ImageProcessConfig.builder()
                    .maxWidth(400)
                    .maxHeight(400)
                    .quality(0.8)
                    .convertToWebP(false)
                    .build();

            // When
            byte[] processedImage = imageProcessor.process(originalImage, config);
            int[] dimensions = imageProcessor.getImageDimensions(processedImage);

            // Then
            // 原始比例 2:1，限制为 400x400，应该缩放为 400x200
            assertEquals(400, dimensions[0]);
            assertEquals(200, dimensions[1]);
        }

        @Test
        @DisplayName("小于最大尺寸的图片不应该放大")
        void shouldNotEnlargeSmallImages() throws IOException {
            // Given
            byte[] originalImage = createTestImage(200, 150);
            ImageProcessConfig config = ImageProcessConfig.builder()
                    .maxWidth(400)
                    .maxHeight(300)
                    .quality(0.8)
                    .convertToWebP(false)
                    .build();

            // When
            byte[] processedImage = imageProcessor.process(originalImage, config);
            int[] dimensions = imageProcessor.getImageDimensions(processedImage);

            // Then
            assertEquals(200, dimensions[0]);
            assertEquals(150, dimensions[1]);
        }

        @Test
        @DisplayName("无效图片数据应该抛出异常")
        void shouldThrowExceptionForInvalidImageData() {
            // Given
            byte[] invalidData = "not an image".getBytes();
            ImageProcessConfig config = ImageProcessConfig.defaultConfig();

            // When & Then
            assertThrows(BusinessException.class, () ->
                    imageProcessor.process(invalidData, config));
        }
    }

    @Nested
    @DisplayName("缩略图生成")
    class GenerateThumbnail {

        @Test
        @DisplayName("应该成功生成缩略图")
        void shouldGenerateThumbnailSuccessfully() throws IOException {
            // Given
            byte[] originalImage = createTestImage(800, 600);

            // When
            byte[] thumbnail = imageProcessor.generateThumbnail(originalImage, 200, 200);

            // Then
            assertNotNull(thumbnail);
            assertTrue(thumbnail.length > 0);
            assertTrue(thumbnail.length < originalImage.length);
        }

        @Test
        @DisplayName("缩略图尺寸应该正确")
        void shouldGenerateCorrectThumbnailSize() throws IOException {
            // Given
            byte[] originalImage = createTestImage(800, 600);

            // When
            byte[] thumbnail = imageProcessor.generateThumbnail(originalImage, 100, 100);
            int[] dimensions = imageProcessor.getImageDimensions(thumbnail);

            // Then
            // 保持宽高比，800:600 = 4:3，缩放到 100x100 应该或100x75
            assertTrue(dimensions[0] <= 100);
            assertTrue(dimensions[1] <= 100);
        }

        @Test
        @DisplayName("无效图片数据应该抛出异常")
        void shouldThrowExceptionForInvalidImageData() {
            // Given
            byte[] invalidData = "not an image".getBytes();

            // When & Then
            assertThrows(BusinessException.class, () ->
                    imageProcessor.generateThumbnail(invalidData, 200, 200));
        }
    }

    @Nested
    @DisplayName("获取图片尺寸")
    class GetImageDimensions {

        @Test
        @DisplayName("应该正确获取图片尺寸")
        void shouldGetCorrectDimensions() throws IOException {
            // Given
            byte[] image = createTestImage(640, 480);

            // When
            int[] dimensions = imageProcessor.getImageDimensions(image);

            // Then
            assertEquals(640, dimensions[0]);
            assertEquals(480, dimensions[1]);
        }

        @Test
        @DisplayName("无效图片数据应该抛出异常")
        void shouldThrowExceptionForInvalidImageData() {
            // Given
            byte[] invalidData = "not an image".getBytes();

            // When & Then
            assertThrows(BusinessException.class, () ->
                    imageProcessor.getImageDimensions(invalidData));
        }
    }

    @Nested
    @DisplayName("图片压缩")
    class CompressImage {

        @Test
        @DisplayName("应该成功压缩图片")
        void shouldCompressImageSuccessfully() throws IOException {
            // Given
            byte[] originalImage = createTestImage(800, 600);

            // When
            byte[] compressedImage = imageProcessor.compress(originalImage, 0.5);

            // Then
            assertNotNull(compressedImage);
            assertTrue(compressedImage.length > 0);
        }

        @Test
        @DisplayName("低质量压缩应该产生更小的文件")
        void shouldProduceSmallerFileWithLowerQuality() throws IOException {
            // Given
            byte[] originalImage = createTestImage(800, 600);

            // When
            byte[] highQuality = imageProcessor.compress(originalImage, 0.9);
            byte[] lowQuality = imageProcessor.compress(originalImage, 0.3);

            // Then
            assertTrue(lowQuality.length < highQuality.length);
        }
    }

    @Nested
    @DisplayName("调整图片尺寸")
    class ResizeImage {

        @Test
        @DisplayName("应该成功调整图片尺寸")
        void shouldResizeImageSuccessfully() throws IOException {
            // Given
            byte[] originalImage = createTestImage(800, 600);

            // When
            byte[] resizedImage = imageProcessor.resize(originalImage, 400, 300);
            int[] dimensions = imageProcessor.getImageDimensions(resizedImage);

            // Then
            assertNotNull(resizedImage);
            assertTrue(dimensions[0] <= 400);
            assertTrue(dimensions[1] <= 300);
        }
    }
}
