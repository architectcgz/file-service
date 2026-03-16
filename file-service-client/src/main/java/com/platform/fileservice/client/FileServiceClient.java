package com.platform.fileservice.client;

import com.platform.fileservice.client.exception.FileServiceException;
import com.platform.fileservice.client.model.*;

import java.io.File;
import java.io.InputStream;

/**
 * 文件服务客户端接口
 * 
 * 提供与文件服务平台交互的方法，包括：
 * - 图片和文件上传
 * - 文件访问和检索
 * - 文件删除
 * - 大文件分片上传
 * - 秒传（去重）
 * - 预签名URL生成
 * 
 * 此接口继承AutoCloseable以确保正确的资源管理。
 * 客户端不再使用时应关闭以释放HTTP连接。
 * 
 * @author File Service Team
 * @version 1.0
 */
public interface FileServiceClient extends AutoCloseable {
    
    // ==================== 图片上传方法 ====================
    
    /**
     * 上传图片文件
     * 
     * @param imageFile 要上传的图片文件
     * @return 包含文件元数据和访问URL的响应
     * @throws FileServiceException 如果上传失败
     */
    FileUploadResponse uploadImage(File imageFile) throws FileServiceException;
    
    /**
     * 从输入流上传图片
     * 
     * @param inputStream 包含图片数据的输入流
     * @param fileName 原始文件名
     * @param fileSize 文件大小（字节）
     * @return 包含文件元数据和访问URL的响应
     * @throws FileServiceException 如果上传失败
     */
    FileUploadResponse uploadImage(InputStream inputStream, String fileName, long fileSize) 
            throws FileServiceException;
    
    // ==================== 文件上传方法 ====================
    
    /**
     * 使用默认访问级别（PUBLIC）上传文件
     * 
     * @param file 要上传的文件
     * @return 包含文件元数据和访问URL的响应
     * @throws FileServiceException 如果上传失败
     */
    FileUploadResponse uploadFile(File file) throws FileServiceException;
    
    /**
     * 从输入流使用默认访问级别（PUBLIC）上传文件
     * 
     * @param inputStream 包含文件数据的输入流
     * @param fileName 原始文件名
     * @param fileSize 文件大小（字节）
     * @param contentType 文件的MIME类型
     * @return 包含文件元数据和访问URL的响应
     * @throws FileServiceException 如果上传失败
     */
    FileUploadResponse uploadFile(InputStream inputStream, String fileName, 
                                  long fileSize, String contentType) throws FileServiceException;
    
    /**
     * 使用指定访问级别上传文件
     * 
     * @param file 要上传的文件
     * @param accessLevel 访问级别（PUBLIC或PRIVATE）
     * @return 包含文件元数据和访问URL的响应
     * @throws FileServiceException 如果上传失败
     */
    FileUploadResponse uploadFile(File file, AccessLevel accessLevel) throws FileServiceException;
    
    /**
     * 从输入流使用指定访问级别上传文件
     * 
     * @param inputStream 包含文件数据的输入流
     * @param fileName 原始文件名
     * @param fileSize 文件大小（字节）
     * @param contentType 文件的MIME类型
     * @param accessLevel 访问级别（PUBLIC或PRIVATE）
     * @return 包含文件元数据和访问URL的响应
     * @throws FileServiceException 如果上传失败
     */
    FileUploadResponse uploadFile(InputStream inputStream, String fileName, 
                                  long fileSize, String contentType, AccessLevel accessLevel) 
            throws FileServiceException;
    
    // ==================== 文件访问方法 ====================
    
    /**
     * 获取文件的访问URL
     * 
     * 对于公开文件，返回直接URL。
     * 对于私有文件，返回带过期时间的预签名URL。
     * 
     * @param fileId 唯一文件标识符
     * @return 文件访问URL
     * @throws FileServiceException 如果检索失败
     */
    String getFileUrl(String fileId) throws FileServiceException;
    
    /**
     * 获取文件的详细信息
     * 
     * @param fileId 唯一文件标识符
     * @return 包含完整文件元数据的响应
     * @throws FileServiceException 如果检索失败
     */
    FileDetailResponse getFileDetail(String fileId) throws FileServiceException;
    
    // ==================== 文件删除方法 ====================
    
    /**
     * 删除文件
     * 
     * 只能删除属于当前租户的文件。
     * 
     * @param fileId 唯一文件标识符
     * @throws FileServiceException 如果删除失败
     */
    void deleteFile(String fileId) throws FileServiceException;
    
    // ==================== 分片上传方法 ====================
    
    /**
     * 为大文件初始化分片上传会话
     * 
     * @param request 包含文件元数据的分片初始化请求
     * @return 包含任务ID和上传参数的响应
     * @throws FileServiceException 如果初始化失败
     */
    MultipartInitResponse initMultipartUpload(MultipartInitRequest request) 
            throws FileServiceException;
    
    /**
     * 上传分片上传的单个分片/块
     * 
     * @param taskId 分片上传任务标识符
     * @param partNumber 分片编号（从1开始的索引）
     * @param data 包含分片数据的输入流
     * @param size 分片大小（字节）
     * @return 包含分片编号和ETag的已上传分片
     * @throws FileServiceException 如果上传失败
     */
    MultipartUploadPart uploadPart(String taskId, int partNumber, 
                                   InputStream data, long size) throws FileServiceException;
    
    /**
     * 在所有分片上传完成后完成分片上传
     * 
     * @param taskId 分片上传任务标识符
     * @param fileHash 完整文件的哈希值用于验证
     * @return 包含文件元数据和访问URL的响应
     * @throws FileServiceException 如果完成失败
     */
    FileUploadResponse completeMultipartUpload(String taskId, String fileHash) 
            throws FileServiceException;
    
    /**
     * 取消正在进行的分片上传
     * 
     * @param taskId 分片上传任务标识符
     * @throws FileServiceException 如果取消失败
     */
    void cancelMultipartUpload(String taskId) throws FileServiceException;
    
    // ==================== 秒传方法 ====================
    
    /**
     * 检查是否已存在相同哈希值的文件（秒传/去重）
     * 
     * 如果文件存在，返回现有文件元数据而无需重新上传。
     * 如果文件不存在，指示需要上传。
     * 
     * @param request 包含文件哈希和元数据的秒传检查请求
     * @return 指示文件是否存在的响应，如果存在则提供元数据
     * @throws FileServiceException 如果检查失败
     */
    InstantUploadCheckResponse checkInstantUpload(InstantUploadCheckRequest request) 
            throws FileServiceException;
    
    // ==================== 预签名URL方法 ====================
    
    /**
     * 获取单文件上传会话及对应的预签名上传URL
     * 
     * 这允许客户端直接上传到S3而无需通过文件服务，
     * 可以提高大文件的性能。
     * 
     * @param request 包含文件元数据的预签名URL请求
     * @return 包含上传会话ID、预签名上传URL和过期时间的响应
     * @throws FileServiceException 如果URL生成失败
     */
    PresignedUploadResponse getPresignedUploadUrl(PresignedUploadRequest request) 
            throws FileServiceException;
    
    /**
     * 确认文件已使用预签名URL成功上传
     * 
     * 必须在上传到预签名URL后调用此方法，
     * 以在文件服务数据库中注册文件。
     * 
     * @param uploadSessionId 来自预签名URL响应的上传会话标识符
     * @param fileHash 已上传文件的哈希值，用于校验调用参数与会话一致
     * @return 包含文件元数据和访问URL的响应
     * @throws FileServiceException 如果确认失败
     */
    FileUploadResponse confirmPresignedUpload(String uploadSessionId, String fileHash) 
            throws FileServiceException;
    
    // ==================== 资源管理 ====================
    
    /**
     * 关闭客户端并释放所有资源
     * 
     * 此方法关闭HTTP客户端并释放连接池资源。
     * 调用close()后，客户端不应再用于进一步操作。
     */
    @Override
    void close();
}
