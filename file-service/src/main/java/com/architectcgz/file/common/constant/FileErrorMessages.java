package com.architectcgz.file.common.constant;

/**
 * 文件服务错误消息常量
 * 集中管理错误消息，避免硬编码和重复
 */
public final class FileErrorMessages {

    /** 无权访问文件 */
    public static final String ACCESS_DENIED_PREFIX = "无权访问该文件: ";

    /** 无权修改文件访问级别 */
    public static final String MODIFY_ACCESS_DENIED_PREFIX = "无权修改该文件的访问级别: ";

    /** 更新文件访问级别失败 */
    public static final String UPDATE_ACCESS_LEVEL_FAILED_PREFIX = "更新文件访问级别失败: ";

    private FileErrorMessages() {
        // 工具类，禁止实例化
    }
}
