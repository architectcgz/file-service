package com.architectcgz.file.common.constants;

/**
 * 文件服务错误消息常量
 *
 * 统一管理文件删除、存储操作相关的错误消息，
 * 避免硬编码字符串散落在业务代码中
 */
public final class FileErrorMessages {

    private FileErrorMessages() {
        // 工具类禁止实例化
    }

    /** 文件归属校验失败：文件不属于当前应用 */
    public static final String FILE_APP_MISMATCH = "无权访问该文件：文件不属于当前应用";

    /** 用户无权删除该文件 */
    public static final String FILE_DELETE_FORBIDDEN = "无权删除该文件";

    /** 数据库删除文件记录失败 */
    public static final String FILE_RECORD_DELETE_FAILED = "删除文件记录失败";
}
