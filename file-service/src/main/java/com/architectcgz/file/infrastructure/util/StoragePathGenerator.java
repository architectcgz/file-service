package com.architectcgz.file.infrastructure.util;

import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 存储路径生成工具
 * <p>
 * 生成格式：{tenantId}/{year}/{month}/{day}/{userId}/{type}/{fileId}.{ext}
 * 示例：blog/2026/01/16/12345/images/01912345-6789-7abc-def0-123456789abc.jpg
 * </p>
 */
@Component
public class StoragePathGenerator {

    /**
     * 生成存储路径（新格式，支持租户ID）
     *
     * @param tenantId     租户ID（应用ID）
     * @param userId       用户ID
     * @param fileType     文件类型 (images, files, videos, thumbnails, etc.)
     * @param originalFilename 原始文件名
     * @return 存储路径，格式：{tenantId}/{year}/{month}/{day}/{userId}/{type}/{fileId}.{ext}
     */
    public String generateStoragePath(String tenantId, String userId, String fileType, String originalFilename) {
        LocalDate now = LocalDate.now();
        String fileId = UuidCreator.getTimeOrderedEpoch().toString();
        String extension = getExtension(originalFilename);

        return String.format("%s/%d/%02d/%02d/%s/%s/%s.%s",
                tenantId,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                userId,
                fileType,
                fileId,
                extension
        );
    }

    /**
     * 生成存储路径（兼容旧接口，appId作为tenantId）
     *
     * @param appId        应用ID（作为租户ID）
     * @param userId       用户ID
     * @param fileType     文件类型 (images, files, videos, thumbnails, etc.)
     * @param originalFilename 原始文件名
     * @return 存储路径，格式：{appId}/{year}/{month}/{day}/{userId}/{type}/{fileId}.{ext}
     * @deprecated 使用 {@link #generateStoragePath(String, String, String, String)} 代替
     */
    @Deprecated
    public String generateStoragePathWithAppId(String appId, String userId, String fileType, String originalFilename) {
        return generateStoragePath(appId, userId, fileType, originalFilename);
    }

    /**
     * 提取文件扩展名
     *
     * @param fileName 文件名
     * @return 扩展名（小写，不含点）
     */
    public String getExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }

        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(lastDotIndex + 1).toLowerCase();
    }

    /**
     * 生成带自定义扩展名的存储路径
     *
     * @param tenantId  租户ID
     * @param userId    用户ID
     * @param fileType  文件类型 (images, files, videos, thumbnails, etc.)
     * @param extension 文件扩展名（不含点）
     * @return 存储路径
     */
    public String generateStoragePathWithExtension(String tenantId, String userId, String fileType, String extension) {
        LocalDate now = LocalDate.now();
        String fileId = UuidCreator.getTimeOrderedEpoch().toString();

        return String.format("%s/%d/%02d/%02d/%s/%s/%s.%s",
                tenantId,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                userId,
                fileType,
                fileId,
                extension.toLowerCase()
        );
    }

    /**
     * 从内容类型推断文件类型目录
     *
     * @param contentType MIME类型
     * @return 文件类型目录名 (images, videos, audios, files)
     */
    public String inferFileType(String contentType) {
        if (contentType == null) {
            return "files";
        }

        if (contentType.startsWith("image/")) {
            return "images";
        } else if (contentType.startsWith("video/")) {
            return "videos";
        } else if (contentType.startsWith("audio/")) {
            return "audios";
        } else {
            return "files";
        }
    }
}
