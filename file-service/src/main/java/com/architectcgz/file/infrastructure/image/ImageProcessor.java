package com.architectcgz.file.infrastructure.image;

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
import java.io.IOException;
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
                throw new BusinessException(FileServiceErrorMessages.IMAGE_READ_FAILED);
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
            String outputFormat = config.isConvertToWebP() ? "webp" : "jpg";
            
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
            throw new BusinessException(String.format(FileServiceErrorMessages.IMAGE_PROCESS_FAILED, e.getMessage()));
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
            throw new BusinessException(String.format(FileServiceErrorMessages.THUMBNAIL_GENERATE_FAILED, e.getMessage()));
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
            throw new BusinessException(String.format(FileServiceErrorMessages.WEBP_CONVERT_FAILED, e.getMessage()));
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
                throw new BusinessException(FileServiceErrorMessages.IMAGE_READ_FAILED);
            }
            return new int[]{image.getWidth(), image.getHeight()};
        } catch (IOException e) {
            log.error("Failed to get image dimensions", e);
            throw new BusinessException(String.format(FileServiceErrorMessages.IMAGE_DIMENSIONS_FAILED, e.getMessage()));
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
            throw new BusinessException(String.format(FileServiceErrorMessages.IMAGE_COMPRESS_FAILED, e.getMessage()));
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
            throw new BusinessException(String.format(FileServiceErrorMessages.IMAGE_RESIZE_FAILED, e.getMessage()));
        }
    }
}
