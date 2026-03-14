package com.architectcgz.file.application.service.upload.image;

import com.architectcgz.file.infrastructure.config.ImageProcessingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片上传格式解析器。
 */
@Component
@RequiredArgsConstructor
public class UploadImageFormatResolver {

    private final ImageProcessingProperties imageProcessingProperties;

    public String resolveProcessedExtension(MultipartFile file) {
        if (imageProcessingProperties.isConvertToWebp()) {
            return "webp";
        }
        String extension = resolveExtension(file.getOriginalFilename());
        return switch (extension) {
            case "jpg", "jpeg" -> "jpg";
            case "png" -> "png";
            case "gif" -> "gif";
            case "webp" -> "webp";
            default -> "jpg";
        };
    }

    public String resolveProcessedContentType(MultipartFile file, String extension) {
        if (imageProcessingProperties.isConvertToWebp()) {
            return "image/webp";
        }
        return switch (extension) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> file.getContentType();
        };
    }

    private String resolveExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
