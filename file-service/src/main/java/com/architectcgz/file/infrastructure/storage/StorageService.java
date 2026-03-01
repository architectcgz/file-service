package com.architectcgz.file.infrastructure.storage;

import com.architectcgz.file.domain.model.AccessLevel;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 存储服务接口 - 策略模式
 */
public interface StorageService {
    
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
     * 获取文件访问URL
     *
     * @param path 存储路径
     * @return 访问URL
     */
    String getUrl(String path);
    
    /**
     * 获取公开文件的访问URL
     *
     * @param path 存储路径
     * @return 公开访问URL
     */
    String getPublicUrl(String path);
    
    /**
     * 生成私有文件的预签名URL
     *
     * @param path 存储路径
     * @param expiration URL过期时间
     * @return 预签名URL
     */
    String generatePresignedUrl(String path, Duration expiration);
    
    /**
     * 检查文件是否存或
     *
     * @param path 存储路径
     * @return 是否存在
     */
    boolean exists(String path);

    /**
     * 获取存储对象的元数据（文件大小、内容类型）
     * 用于预签名上传确认等场景，从存储服务获取真实的文件元信息
     *
     * @param path 存储路径
     * @return 对象元数据，包含 fileSize 和 contentType
     * @throws com.architectcgz.file.common.exception.BusinessException 文件不存在或查询失败时抛出
     */
    ObjectMetadata getObjectMetadata(String path);
}
