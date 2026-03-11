package com.architectcgz.file.infrastructure.storage;

import com.architectcgz.file.domain.model.AccessLevel;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 存储服务接口 - 策略模式
 */
public interface StorageService {

    /**
     * 获取默认存储桶名称
     * 本地存储等不区分 bucket 的实现可返回固定占位值
     */
    default String getDefaultBucketName() {
        return null;
    }

    /**
     * 根据访问级别解析存储桶名称
     */
    default String getBucketName(AccessLevel accessLevel) {
        return getDefaultBucketName();
    }
    
    /**
     * 上传文件
     *
     * @param data 文件数据
     * @param path 存储路径
     * @return 访问URL
     */
    String upload(byte[] data, String path);
    
    /**
     * 上传文件并指定内容类或
     *
     * @param data 文件数据
     * @param path 存储路径
     * @param contentType 内容类型
     * @return 访问URL
     */
    String upload(byte[] data, String path, String contentType);

    /**
     * 从本地文件上传到存储，避免将整个文件加载到内存
     *
     * @param file 本地文件路径
     * @param storagePath 存储路径
     * @param contentType 内容类型
     * @return 访问URL
     */
    String uploadFromFile(Path file, String storagePath, String contentType);

    /**
     * 上传文件到公开存储桶
     *
     * @param data 文件数据
     * @param path 存储路径
     * @param contentType 内容类型
     * @return 访问URL
     */
    String uploadToPublicBucket(byte[] data, String path, String contentType);
    
    /**
     * 上传文件到私有存储桶
     *
     * @param data 文件数据
     * @param path 存储路径
     * @param contentType 内容类型
     * @return 访问URL
     */
    String uploadToPrivateBucket(byte[] data, String path, String contentType);
    
    /**
     * 根据访问级别上传文件到对应的存储桶
     *
     * @param data 文件数据
     * @param path 存储路径
     * @param contentType 内容类型
     * @param accessLevel 访问级别
     * @return 访问URL
     */
    String uploadByAccessLevel(byte[] data, String path, String contentType, AccessLevel accessLevel);
    
    /**
     * 删除文件
     *
     * @param path 存储路径
     */
    void delete(String path);

    /**
     * 删除指定存储桶中的文件
     *
     * @param bucketName 存储桶名称
     * @param path 存储路径
     */
    default void delete(String bucketName, String path) {
        delete(path);
    }
    
    /**
     * 获取文件访问URL
     *
     * @param path 存储路径
     * @return 访问URL
     */
    String getUrl(String path);

    /**
     * 获取指定存储桶中的文件访问 URL
     *
     * @param bucketName 存储桶名称
     * @param path 存储路径
     * @return 访问 URL
     */
    default String getUrl(String bucketName, String path) {
        return getUrl(path);
    }
    
    /**
     * 获取公开文件的访问URL
     *
     * @param path 存储路径
     * @return 公开访问URL
     */
    String getPublicUrl(String path);

    /**
     * 获取指定存储桶中的公开文件访问 URL
     *
     * @param bucketName 存储桶名称
     * @param path 存储路径
     * @return 公开访问 URL
     */
    default String getPublicUrl(String bucketName, String path) {
        return getPublicUrl(path);
    }
    
    /**
     * 生成私有文件的预签名URL
     *
     * @param path 存储路径
     * @param expiration URL过期时间
     * @return 预签名URL
     */
    String generatePresignedUrl(String path, Duration expiration);

    /**
     * 生成指定存储桶中文件的预签名 URL
     *
     * @param bucketName 存储桶名称
     * @param path 存储路径
     * @param expiration 过期时间
     * @return 预签名 URL
     */
    default String generatePresignedUrl(String bucketName, String path, Duration expiration) {
        return generatePresignedUrl(path, expiration);
    }
    
    /**
     * 检查文件是否存或
     *
     * @param path 存储路径
     * @return 是否存在
     */
    boolean exists(String path);

    /**
     * 检查指定存储桶中的文件是否存在
     *
     * @param bucketName 存储桶名称
     * @param path 存储路径
     * @return 是否存在
     */
    default boolean exists(String bucketName, String path) {
        return exists(path);
    }

    /**
     * 获取存储对象的元数据（文件大小、内容类型）
     * 用于预签名上传确认等场景，从存储服务获取真实的文件元信息
     *
     * @param path 存储路径
     * @return 对象元数据，包含 fileSize 和 contentType
     * @throws com.architectcgz.file.common.exception.BusinessException 文件不存在或查询失败时抛出
     */
    ObjectMetadata getObjectMetadata(String path);

    /**
     * 获取指定存储桶中的对象元数据
     *
     * @param bucketName 存储桶名称
     * @param path 存储路径
     * @return 对象元数据
     */
    default ObjectMetadata getObjectMetadata(String bucketName, String path) {
        return getObjectMetadata(path);
    }
}
