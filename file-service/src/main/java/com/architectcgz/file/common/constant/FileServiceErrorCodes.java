package com.architectcgz.file.common.constant;

/**
 * 文件服务稳定业务错误码。
 *
 * <p>用于接口响应与日志排障，避免以中文错误消息作为唯一定位依据。
 */
public final class FileServiceErrorCodes {

    private FileServiceErrorCodes() {
        // 工具类禁止实例化
    }

    public static final String BUSINESS_ERROR = "BUSINESS_ERROR";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String MISSING_REQUEST_HEADER = "MISSING_REQUEST_HEADER";
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    public static final String TENANT_NOT_FOUND = "TENANT_NOT_FOUND";
    public static final String TENANT_SUSPENDED = "TENANT_SUSPENDED";
    public static final String QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String FILE_NOT_FOUND = "FILE_NOT_FOUND";

    public static final String UPLOAD_TASK_NOT_FOUND = "UPLOAD_TASK_NOT_FOUND";
    public static final String UPLOAD_TASK_EXPIRED = "UPLOAD_TASK_EXPIRED";
    public static final String UPLOAD_TASK_FILE_HASH_MISSING = "UPLOAD_TASK_FILE_HASH_MISSING";
    public static final String WAIT_PART_UPLOAD_INTERRUPTED = "WAIT_PART_UPLOAD_INTERRUPTED";
    public static final String WAIT_PART_UPLOAD_TIMEOUT = "WAIT_PART_UPLOAD_TIMEOUT";
    public static final String WAIT_DEDUP_UPLOAD_INTERRUPTED = "WAIT_DEDUP_UPLOAD_INTERRUPTED";
    public static final String WAIT_DEDUP_UPLOAD_TIMEOUT = "WAIT_DEDUP_UPLOAD_TIMEOUT";
    public static final String TASK_STATUS_INVALID = "TASK_STATUS_INVALID";
    public static final String FILE_SIZE_MISMATCH = "FILE_SIZE_MISMATCH";
    public static final String PART_COUNT_EXCEEDED = "PART_COUNT_EXCEEDED";
    public static final String PART_NUMBER_INVALID = "PART_NUMBER_INVALID";
    public static final String PART_INFO_EMPTY = "PART_INFO_EMPTY";
    public static final String TASK_ID_EMPTY = "TASK_ID_EMPTY";
    public static final String PART_NUMBER_EMPTY = "PART_NUMBER_EMPTY";
    public static final String PART_NUMBER_DUPLICATED = "PART_NUMBER_DUPLICATED";
    public static final String PART_ALREADY_UPLOADED = "PART_ALREADY_UPLOADED";
    public static final String PARTS_INCOMPLETE = "PARTS_INCOMPLETE";
    public static final String PART_NOT_FOUND_IN_STORAGE = "PART_NOT_FOUND_IN_STORAGE";
    public static final String PART_ETAG_MISMATCH = "PART_ETAG_MISMATCH";
    public static final String PART_SYNC_FAILED = "PART_SYNC_FAILED";
    public static final String PART_DB_WRITE_FAILED = "PART_DB_WRITE_FAILED";
    public static final String PART_BATCH_INSERT_FAILED = "PART_BATCH_INSERT_FAILED";

    public static final String FILE_ALREADY_EXISTS = "FILE_ALREADY_EXISTS";
    public static final String FILE_NOT_UPLOADED = "FILE_NOT_UPLOADED";
    public static final String STORAGE_OBJECT_NOT_FOUND = "STORAGE_OBJECT_NOT_FOUND";
    public static final String UNSUPPORTED_ACCESS_LEVEL = "UNSUPPORTED_ACCESS_LEVEL";
    public static final String UPDATE_ACCESS_LEVEL_FAILED = "UPDATE_ACCESS_LEVEL_FAILED";
    public static final String UPDATE_STORAGE_BINDING_FAILED = "UPDATE_STORAGE_BINDING_FAILED";
    public static final String STORAGE_REFERENCE_DECREMENT_FAILED = "STORAGE_REFERENCE_DECREMENT_FAILED";

    public static final String FILENAME_EMPTY = "FILENAME_EMPTY";
    public static final String EXTENSION_REQUIRED = "EXTENSION_REQUIRED";
    public static final String EXTENSION_NOT_ALLOWED = "EXTENSION_NOT_ALLOWED";
    public static final String CONTENT_TYPE_EMPTY = "CONTENT_TYPE_EMPTY";
    public static final String CONTENT_TYPE_NOT_ALLOWED = "CONTENT_TYPE_NOT_ALLOWED";
    public static final String FILE_SIZE_MUST_POSITIVE = "FILE_SIZE_MUST_POSITIVE";
    public static final String FILE_SIZE_EXCEEDED = "FILE_SIZE_EXCEEDED";
    public static final String FILE_HEADER_INSUFFICIENT = "FILE_HEADER_INSUFFICIENT";
    public static final String FILE_TYPE_UNRECOGNIZED = "FILE_TYPE_UNRECOGNIZED";
    public static final String FILE_TYPE_CONTENT_MISMATCH = "FILE_TYPE_CONTENT_MISMATCH";

    public static final String FILE_UPLOAD_FAILED = "FILE_UPLOAD_FAILED";
    public static final String IMAGE_UPLOAD_FAILED = "IMAGE_UPLOAD_FAILED";
    public static final String FILE_DELETE_FAILED = "FILE_DELETE_FAILED";
    public static final String FILE_COPY_FAILED = "FILE_COPY_FAILED";
    public static final String FILE_HASH_FAILED = "FILE_HASH_FAILED";
    public static final String FILE_METADATA_FAILED = "FILE_METADATA_FAILED";

    public static final String IMAGE_READ_FAILED = "IMAGE_READ_FAILED";
    public static final String IMAGE_PROCESS_FAILED = "IMAGE_PROCESS_FAILED";
    public static final String THUMBNAIL_GENERATE_FAILED = "THUMBNAIL_GENERATE_FAILED";
    public static final String WEBP_CONVERT_FAILED = "WEBP_CONVERT_FAILED";
    public static final String IMAGE_DIMENSIONS_FAILED = "IMAGE_DIMENSIONS_FAILED";
    public static final String IMAGE_COMPRESS_FAILED = "IMAGE_COMPRESS_FAILED";
    public static final String IMAGE_RESIZE_FAILED = "IMAGE_RESIZE_FAILED";

    public static final String LOCAL_DIR_CREATE_FAILED = "LOCAL_DIR_CREATE_FAILED";

    public static final String S3_CLIENT_ERROR = "S3_CLIENT_ERROR";
    public static final String S3_UPLOAD_PUBLIC_FAILED = "S3_UPLOAD_PUBLIC_FAILED";
    public static final String S3_UPLOAD_PRIVATE_FAILED = "S3_UPLOAD_PRIVATE_FAILED";
    public static final String S3_PRESIGN_GET_FAILED = "S3_PRESIGN_GET_FAILED";
    public static final String S3_PRESIGN_PUT_FAILED = "S3_PRESIGN_PUT_FAILED";
    public static final String S3_PRESIGN_DOWNLOAD_FAILED = "S3_PRESIGN_DOWNLOAD_FAILED";
    public static final String S3_PRESIGN_PART_FAILED = "S3_PRESIGN_PART_FAILED";
    public static final String S3_EXISTS_CHECK_FAILED = "S3_EXISTS_CHECK_FAILED";
    public static final String S3_CONFIG_INVALID = "S3_CONFIG_INVALID";
    public static final String S3_CLIENT_BUILD_FAILED = "S3_CLIENT_BUILD_FAILED";
    public static final String S3_PRESIGNER_CONFIG_INVALID = "S3_PRESIGNER_CONFIG_INVALID";
    public static final String S3_PRESIGNER_BUILD_FAILED = "S3_PRESIGNER_BUILD_FAILED";
    public static final String S3_INIT_FAILED = "S3_INIT_FAILED";
    public static final String S3_BUCKET_CREATE_FAILED = "S3_BUCKET_CREATE_FAILED";
    public static final String S3_MULTIPART_CREATE_FAILED = "S3_MULTIPART_CREATE_FAILED";
    public static final String S3_PART_UPLOAD_FAILED = "S3_PART_UPLOAD_FAILED";
    public static final String S3_MULTIPART_COMPLETE_FAILED = "S3_MULTIPART_COMPLETE_FAILED";
    public static final String S3_MULTIPART_ABORT_FAILED = "S3_MULTIPART_ABORT_FAILED";
    public static final String S3_LIST_PARTS_FAILED = "S3_LIST_PARTS_FAILED";
}
