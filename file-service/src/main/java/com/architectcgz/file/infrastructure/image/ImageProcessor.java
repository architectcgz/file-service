package com.architectcgz.file.infrastructure.image;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.ImageProcessConfig;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * 图片处理或
 * 
 * 功能或
 * - 图片压缩（调整尺寸和质量或
 * - 缩略图生或
 * - WebP格式转换
 */
@Slf4j
@Component
public class ImageProcessor {
    
    /**
     * 处理图片（压缩、调整大小、转换格式）
     *
     * @param imageData 原始图片数据
     * @param config 处理配置
     * @return 处理后的图片数据
     */
    public byte[] process(byte[] imageData, ImageProcessConfig config) {
        try {
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageData));
            if (originalImage == null) {
                throw new BusinessException(FileServiceErrorCodes.IMAGE_READ_FAILED, FileServiceErrorMessages.IMAGE_READ_FAILED);
            }
            
            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            
            // 计算目标尺寸
            int targetWidth = originalWidth;
            int targetHeight = originalHeight;
            
            if (originalWidth > config.getMaxWidth() || originalHeight > config.getMaxHeight()) {
                double widthRatio = (double) config.getMaxWidth() / originalWidth;
                double heightRatio = (double) config.getMaxHeight() / originalHeight;
                double ratio = Math.min(widthRatio, heightRatio);
                
                targetWidth = (int) (originalWidth * ratio);
                targetHeight = (int) (originalHeight * ratio);
            }
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            // 确定输出格式
            String outputFormat = config.isConvertToWebP() ? "webp" : normalizeOutputFormat(detectImageFormat(imageData));
            
            Thumbnails.of(new ByteArrayInputStream(imageData))
                    .size(targetWidth, targetHeight)
                    .outputQuality(config.getQuality())
                    .outputFormat(outputFormat)
                    .toOutputStream(outputStream);
            
            byte[] result = outputStream.toByteArray();
            log.debug("Image processed: {}x{} -> {}x{}, size: {} -> {} bytes, format: {}",
                    originalWidth, originalHeight, targetWidth, targetHeight,
                    imageData.length, result.length, outputFormat);
            
            return result;
        } catch (IOException e) {
            log.error("Failed to process image", e);
            throw new BusinessException(
                    FileServiceErrorCodes.IMAGE_PROCESS_FAILED,
                    String.format(FileServiceErrorMessages.IMAGE_PROCESS_FAILED, e.getMessage())
            );
        }
    }
    
    /**
     * 生成缩略或
     *
     * @param imageData 原始图片数据
     * @param width 缩略图宽度
     * @param height 缩略图高度
     * @return 缩略图数或
     */
    public byte[] generateThumbnail(byte[] imageData, int width, int height) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            Thumbnails.of(new ByteArrayInputStream(imageData))
                    .size(width, height)
                    .outputQuality(0.8)
                    .outputFormat("jpg")
                    .toOutputStream(outputStream);
            
            byte[] result = outputStream.toByteArray();
            log.debug("Thumbnail generated: {}x{}, size: {} bytes", width, height, result.length);
            
            return result;
        } catch (IOException e) {
            log.error("Failed to generate thumbnail", e);
            throw new BusinessException(
                    FileServiceErrorCodes.THUMBNAIL_GENERATE_FAILED,
                    String.format(FileServiceErrorMessages.THUMBNAIL_GENERATE_FAILED, e.getMessage())
            );
        }
    }
    
    /**
     * 转换为WebP格式
     *
     * @param imageData 原始图片数据
     * @param quality 压缩质量 (0.0 - 1.0)
     * @return WebP格式图片数据
     */
    public byte[] convertToWebP(byte[] imageData, double quality) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            Thumbnails.of(new ByteArrayInputStream(imageData))
                    .scale(1.0)
                    .outputQuality(quality)
                    .outputFormat("webp")
                    .toOutputStream(outputStream);
            
            return outputStream.toByteArray();
        } catch (IOException e) {
            log.error("Failed to convert image to WebP", e);
            throw new BusinessException(
                    FileServiceErrorCodes.WEBP_CONVERT_FAILED,
                    String.format(FileServiceErrorMessages.WEBP_CONVERT_FAILED, e.getMessage())
            );
        }
    }
    
    /**
     * 获取图片尺寸
     *
     * @param imageData 图片数据
     * @return 尺寸数组 [width, height]
     */
    public int[] getImageDimensions(byte[] imageData) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            if (image == null) {
                throw new BusinessException(FileServiceErrorCodes.IMAGE_READ_FAILED, FileServiceErrorMessages.IMAGE_READ_FAILED);
            }
            return new int[]{image.getWidth(), image.getHeight()};
        } catch (IOException e) {
            log.error("Failed to get image dimensions", e);
            throw new BusinessException(
                    FileServiceErrorCodes.IMAGE_DIMENSIONS_FAILED,
                    String.format(FileServiceErrorMessages.IMAGE_DIMENSIONS_FAILED, e.getMessage())
            );
        }
    }
    
    /**
     * 检测图片格或
     *
     * @param imageData 图片数据
     * @return 图片格式（如 "jpeg", "png", "gif", "webp"或
     */
    public String detectImageFormat(byte[] imageData) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(imageData))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                return reader.getFormatName().toLowerCase();
            }
            return "unknown";
        } catch (IOException e) {
            log.error("Failed to detect image format", e);
            return "unknown";
        }
    }
    
    /**
     * 压缩图片（仅调整质量，不改变尺寸或
     *
     * @param imageData 原始图片数据
     * @param quality 压缩质量 (0.0 - 1.0)
     * @return 压缩后的图片数据
     */
    public byte[] compress(byte[] imageData, double quality) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            Thumbnails.of(new ByteArrayInputStream(imageData))
                    .scale(1.0)
                    .outputQuality(quality)
                    .outputFormat("jpg")
                    .toOutputStream(outputStream);
            
            byte[] result = outputStream.toByteArray();
            log.debug("Image compressed: {} -> {} bytes (quality: {})", 
                    imageData.length, result.length, quality);
            
            return result;
        } catch (IOException e) {
            log.error("Failed to compress image", e);
            throw new BusinessException(
                    FileServiceErrorCodes.IMAGE_COMPRESS_FAILED,
                    String.format(FileServiceErrorMessages.IMAGE_COMPRESS_FAILED, e.getMessage())
            );
        }
    }
    
    /**
     * 调整图片尺寸
     *
     * @param imageData 原始图片数据
     * @param width 目标宽度
     * @param height 目标高度
     * @return 调整后的图片数据
     */
    public byte[] resize(byte[] imageData, int width, int height) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            Thumbnails.of(new ByteArrayInputStream(imageData))
                    .size(width, height)
                    .keepAspectRatio(true)
                    .outputFormat("jpg")
                    .toOutputStream(outputStream);

            byte[] result = outputStream.toByteArray();
            log.debug("Image resized to {}x{}: {} bytes", width, height, result.length);

            return result;
        } catch (IOException e) {
            log.error("Failed to resize image", e);
            throw new BusinessException(
                    FileServiceErrorCodes.IMAGE_RESIZE_FAILED,
                    String.format(FileServiceErrorMessages.IMAGE_RESIZE_FAILED, e.getMessage())
            );
        }
    }

    /**
     * 基于文件处理图片（压缩、调整大小、转换格式），避免内存中持有完整 byte[]
     * 读取源文件 -> 处理 -> 写入目标文件，内存中仅保留 BufferedImage 和流式缓冲区
     *
     * @param sourceFile 源图片文件路径
     * @param outputFile 输出图片文件路径
     * @param config 处理配置
     * @return 处理后文件的字节数
     */
    public long processToFile(Path sourceFile, Path outputFile, ImageProcessConfig config) {
        try {
            File source = sourceFile.toFile();
            BufferedImage originalImage = ImageIO.read(source);
            if (originalImage == null) {
                throw new BusinessException(FileServiceErrorCodes.IMAGE_READ_FAILED, FileServiceErrorMessages.IMAGE_READ_FAILED);
            }

            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            // 释放 BufferedImage 引用，后续由 Thumbnailator 重新读取文件
            originalImage = null;

            // 计算目标尺寸
            int targetWidth = originalWidth;
            int targetHeight = originalHeight;

            if (originalWidth > config.getMaxWidth() || originalHeight > config.getMaxHeight()) {
                double widthRatio = (double) config.getMaxWidth() / originalWidth;
                double heightRatio = (double) config.getMaxHeight() / originalHeight;
                double ratio = Math.min(widthRatio, heightRatio);

                targetWidth = (int) (originalWidth * ratio);
                targetHeight = (int) (originalHeight * ratio);
            }

            String outputFormat = config.isConvertToWebP() ? "webp" : detectOutputFormat(source);

            Thumbnails.of(source)
                    .size(targetWidth, targetHeight)
                    .outputQuality(config.getQuality())
                    .outputFormat(outputFormat)
                    .toFile(outputFile.toFile());

            long resultSize = java.nio.file.Files.size(outputFile);
            log.debug("Image processed to file: {}x{} -> {}x{}, size: {} -> {} bytes, format: {}",
                    originalWidth, originalHeight, targetWidth, targetHeight,
                    java.nio.file.Files.size(sourceFile), resultSize, outputFormat);

            return resultSize;
        } catch (IOException e) {
            log.error("Failed to process image to file: {}", sourceFile, e);
            throw new BusinessException(
                    FileServiceErrorCodes.IMAGE_PROCESS_FAILED,
                    String.format(FileServiceErrorMessages.IMAGE_PROCESS_FAILED, e.getMessage())
            );
        }
    }

    /**
     * 基于文件生成缩略图，避免内存中持有完整 byte[]
     *
     * @param sourceFile 源图片文件路径
     * @param outputFile 输出缩略图文件路径
     * @param width 缩略图宽度
     * @param height 缩略图高度
     * @param quality 缩略图压缩质量（0.0-1.0），由外部配置注入，禁止硬编码
     * @return 缩略图文件的字节数
     */
    public long generateThumbnailToFile(Path sourceFile, Path outputFile, int width, int height, double quality) {
        try {
            Thumbnails.of(sourceFile.toFile())
                    .size(width, height)
                    .outputQuality(quality)
                    .outputFormat("jpg")
                    .toFile(outputFile.toFile());

            long resultSize = java.nio.file.Files.size(outputFile);
            log.debug("Thumbnail generated to file: {}x{}, size: {} bytes", width, height, resultSize);

            return resultSize;
        } catch (IOException e) {
            log.error("Failed to generate thumbnail to file: {}", sourceFile, e);
            throw new BusinessException(
                    FileServiceErrorCodes.THUMBNAIL_GENERATE_FAILED,
                    String.format(FileServiceErrorMessages.THUMBNAIL_GENERATE_FAILED, e.getMessage())
            );
        }
    }

    private String detectOutputFormat(File sourceFile) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(sourceFile)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                return normalizeOutputFormat(reader.getFormatName());
            }
        } catch (IOException e) {
            log.warn("Failed to detect image format from file: {}", sourceFile, e);
        }
        return "jpg";
    }

    private String normalizeOutputFormat(String format) {
        if (format == null || format.isBlank()) {
            return "jpg";
        }
        String normalized = format.toLowerCase();
        return switch (normalized) {
            case "jpeg", "jpg" -> "jpg";
            case "png" -> "png";
            case "gif" -> "gif";
            case "webp" -> "webp";
            default -> "jpg";
        };
    }
}
