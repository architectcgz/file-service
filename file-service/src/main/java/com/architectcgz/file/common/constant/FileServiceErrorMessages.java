package com.architectcgz.file.common.constant;

/**
 * 文件服务错误消息常量类
 *
 * 收敛所有 Service 层重复使用的错误消息字符串，
 * 避免硬编码散落在各处导致不一致和维护困难。
 *
 * 带格式占位符的常量需配合 String.format() 使用。
 */
public final class FileServiceErrorMessages {

    private FileServiceErrorMessages() {
        // 工具类禁止实例化
    }

    // ==================== 上传任务相关 ====================

    /** 上传任务不存在 */
    public static final String UPLOAD_TASK_NOT_FOUND = "上传任务不存在";

    /** 上传任务不存在（带 taskId），配合 String.format 使用 */
    public static final String UPLOAD_TASK_NOT_FOUND_WITH_ID = "上传任务不存在: taskId=%s";

    /** 任务状态不正确，配合 String.format 使用 */
    public static final String TASK_STATUS_INVALID = "任务状态不正确: %s";

    /** 上传任务已过期 */
    public static final String UPLOAD_TASK_EXPIRED = "上传任务已过期";

    /** 分片号无效，配合 String.format 使用 */
    public static final String PART_NUMBER_INVALID = "分片号无效: %d";

    /** 分片已上传，请勿重复提交 */
    public static final String PART_ALREADY_UPLOADED = "分片已上传，请勿重复提交";

    /** 文件过大，分片数超过限制，配合 String.format 使用 */
    public static final String PART_COUNT_EXCEEDED = "文件过大，分片数超过限制: %d";

    /** 分片数量不匹配，配合 String.format 使用 */
    public static final String PART_COUNT_MISMATCH = "分片数量不匹配，期望:%d, 实际: %d";

    /** 分片未全部上传，配合 String.format 使用 */
    public static final String PARTS_INCOMPLETE = "分片未全部上传，已上传:%d, 总数: %d";

    /** 文件大小不匹配，无法续传 */
    public static final String FILE_SIZE_MISMATCH = "文件大小不匹配，无法续传";

    // ==================== 文件访问与权限相关 ====================

    /** 无权操作该上传任务 */
    public static final String ACCESS_DENIED_UPLOAD_TASK = "无权操作该上传任务";

    /** 无权查看该上传任务 */
    public static final String ACCESS_DENIED_VIEW_UPLOAD_TASK = "无权查看该上传任务";

    /** 无权访问该文件，配合 String.format 使用 */
    public static final String ACCESS_DENIED_FILE = "无权访问该文件: %s";

    /** 无权修改该文件的访问级别，配合 String.format 使用 */
    public static final String ACCESS_DENIED_UPDATE_ACCESS_LEVEL = "无权修改该文件的访问级别: %s";

    /** 无权删除该文件 */
    public static final String ACCESS_DENIED_DELETE_FILE = "无权删除该文件";

    /** 文件不属于该应用 */
    public static final String FILE_NOT_BELONG_TO_APP = "文件不属于该应用";

    /** 文件已存在，无需重复上传 */
    public static final String FILE_ALREADY_EXISTS = "文件已存在，无需重复上传";

    /** 文件不存在，请先上传文件 */
    public static final String FILE_NOT_UPLOADED = "文件不存在，请先上传文件";

    /** 更新文件访问级别失败，配合 String.format 使用 */
    public static final String UPDATE_ACCESS_LEVEL_FAILED = "更新文件访问级别失败: %s";

    /** 存储对象不存在 */
    public static final String STORAGE_OBJECT_NOT_FOUND = "存储对象不存在";

    // ==================== 文件验证相关 ====================

    /** 文件名不能为空 */
    public static final String FILENAME_EMPTY = "文件名不能为空";

    /** 文件必须有扩展名 */
    public static final String EXTENSION_REQUIRED = "文件必须有扩展名";

    /** 不支持的文件扩展名，配合 String.format 使用 */
    public static final String EXTENSION_NOT_ALLOWED = "不支持的文件扩展名: %s";

    /** Content-Type 不能为空 */
    public static final String CONTENT_TYPE_EMPTY = "Content-Type 不能为空";

    /** 不支持的文件类型，配合 String.format 使用 */
    public static final String CONTENT_TYPE_NOT_ALLOWED = "不支持的文件类型: %s";

    /** 文件大小必须大于 0 */
    public static final String FILE_SIZE_MUST_POSITIVE = "文件大小必须大于 0";

    /** 文件大小超出限制，配合 String.format 使用 */
    public static final String FILE_SIZE_EXCEEDED = "文件大小超出限制，最大允许 %d MB";

    /** 文件头数据不足，无法验证文件类型 */
    public static final String FILE_HEADER_INSUFFICIENT = "文件头数据不足，无法验证文件类型";

    /** 无法识别文件类型 */
    public static final String FILE_TYPE_UNRECOGNIZED = "无法识别文件类型";

    /** 文件类型与内容不匹配 */
    public static final String FILE_TYPE_CONTENT_MISMATCH = "文件类型与内容不匹配";

    // ==================== 文件操作失败相关 ====================

    /** 文件上传失败，配合 String.format 使用 */
    public static final String FILE_UPLOAD_FAILED = "文件上传失败: %s";

    /** 图片上传失败，配合 String.format 使用 */
    public static final String IMAGE_UPLOAD_FAILED = "图片上传失败: %s";

    /** 文件删除失败，配合 String.format 使用 */
    public static final String FILE_DELETE_FAILED = "文件删除失败: %s";

    /** 文件哈希计算失败 */
    public static final String FILE_HASH_FAILED = "文件哈希计算失败";

    // ==================== 图片处理相关 ====================

    /** 无法读取图片数据 */
    public static final String IMAGE_READ_FAILED = "无法读取图片数据";

    /** 图片处理失败，配合 String.format 使用 */
    public static final String IMAGE_PROCESS_FAILED = "图片处理失败: %s";

    /** 缩略图生成失败，配合 String.format 使用 */
    public static final String THUMBNAIL_GENERATE_FAILED = "缩略图生成失败: %s";

    /** WebP 转换失败，配合 String.format 使用 */
    public static final String WEBP_CONVERT_FAILED = "WebP转换失败: %s";

    /** 获取图片尺寸失败，配合 String.format 使用 */
    public static final String IMAGE_DIMENSIONS_FAILED = "获取图片尺寸失败: %s";

    /** 图片压缩失败，配合 String.format 使用 */
    public static final String IMAGE_COMPRESS_FAILED = "图片压缩失败: %s";

    /** 图片调整尺寸失败，配合 String.format 使用 */
    public static final String IMAGE_RESIZE_FAILED = "图片调整尺寸失败: %s";

    // ==================== S3 存储相关 ====================

    /** S3 客户端错误，配合 String.format 使用 */
    public static final String S3_CLIENT_ERROR = "S3 客户端错误: %s";

    /** 文件上传到公开存储桶失败，配合 String.format 使用 */
    public static final String S3_UPLOAD_PUBLIC_FAILED = "文件上传到公开存储桶失败: %s";

    /** 文件上传到私有存储桶失败，配合 String.format 使用 */
    public static final String S3_UPLOAD_PRIVATE_FAILED = "文件上传到私有存储桶失败: %s";

    /** 生成预签名URL失败，配合 String.format 使用 */
    public static final String S3_PRESIGN_GET_FAILED = "生成预签名URL失败: %s";

    /** 生成预签名上传URL失败，配合 String.format 使用 */
    public static final String S3_PRESIGN_PUT_FAILED = "生成预签名上传URL 失败: %s";

    /** 生成预签名下载URL失败，配合 String.format 使用 */
    public static final String S3_PRESIGN_DOWNLOAD_FAILED = "生成预签名下载URL 失败: %s";

    /** 生成预签名分片上传URL失败，配合 String.format 使用 */
    public static final String S3_PRESIGN_PART_FAILED = "生成预签名分片上传URL失败: %s";

    /** 检查文件是否存在失败，配合 String.format 使用 */
    public static final String S3_EXISTS_CHECK_FAILED = "检查文件是否存在失败: %s";

    /** S3 配置无效，配合 String.format 使用 */
    public static final String S3_CONFIG_INVALID = "S3 配置无效: %s";

    /** S3 客户端创建失败，配合 String.format 使用 */
    public static final String S3_CLIENT_BUILD_FAILED = "S3 客户端创建失败: %s";

    /** S3 预签名器配置无效，配合 String.format 使用 */
    public static final String S3_PRESIGNER_CONFIG_INVALID = "S3 预签名器配置无效: %s";

    /** S3 预签名器创建失败，配合 String.format 使用 */
    public static final String S3_PRESIGNER_BUILD_FAILED = "S3 预签名器创建失败: %s";

    /** S3 存储初始化失败，配合 String.format 使用 */
    public static final String S3_INIT_FAILED = "S3 存储初始化失败: %s";

    /** 创建 S3 存储桶失败，配合 String.format 使用 */
    public static final String S3_BUCKET_CREATE_FAILED = "创建 S3 存储桶失败: %s";

    /** 创建分片上传任务失败，配合 String.format 使用 */
    public static final String S3_MULTIPART_CREATE_FAILED = "创建分片上传任务失败: %s";

    /** 上传分片失败，配合 String.format 使用 */
    public static final String S3_PART_UPLOAD_FAILED = "上传分片失败: %s";

    /** 完成分片上传失败，配合 String.format 使用 */
    public static final String S3_MULTIPART_COMPLETE_FAILED = "完成分片上传失败: %s";

    /** 中止分片上传失败，配合 String.format 使用 */
    public static final String S3_MULTIPART_ABORT_FAILED = "中止分片上传失败: %s";

    /** 查询已上传分片失败，配合 String.format 使用 */
    public static final String S3_LIST_PARTS_FAILED = "查询已上传分片失败: %s";

    // ==================== 本地存储相关 ====================

    /** 创建上传目录失败，配合 String.format 使用 */
    public static final String LOCAL_DIR_CREATE_FAILED = "Failed to create upload directory: %s";
}
