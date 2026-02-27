package com.architectcgz.file.infrastructure.cache;

/**
 * 文件服务 Redis Key 定义
 * 
 * 命名规范：{service}:{id}:{entity}:{field}
 * 示例：file:01JGXXX:url
 *
 * @author File Service Team
 */
public final class FileRedisKeys {

    private static final String PREFIX = "file";

    private FileRedisKeys() {
        // 工具类，禁止实例化
    }

    // ==================== 文件缓存 ====================

    /**
     * 文件 URL 缓存
     * Key: file:{fileId}:url
     */
    public static String fileUrl(String fileId) {
        if (fileId == null || fileId.trim().isEmpty()) {
            throw new IllegalArgumentException("fileId 不能为 null 或空字符串");
        }
        return PREFIX + ":" + fileId + ":url";
    }
}
