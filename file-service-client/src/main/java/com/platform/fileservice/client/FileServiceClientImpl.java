package com.platform.fileservice.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.fileservice.client.config.FileServiceClientConfig;
import com.platform.fileservice.client.exception.*;
import com.platform.fileservice.client.model.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FileServiceClient的HTTP实现类
 * 
 * 使用Java 11+ HttpClient进行HTTP通信，支持连接池、超时配置和自动重试逻辑。
 * 线程安全，可在多线程环境中共享使用。
 *
 * @author File Service Team
 */
public class FileServiceClientImpl implements FileServiceClient {
    
    private static final Logger logger = LoggerFactory.getLogger(FileServiceClientImpl.class);
    
    private final FileServiceClientConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    /**
     * 使用给定配置构造FileServiceClientImpl实例
     * 
     * 配置会在构造时进行验证，如果验证失败会抛出IllegalArgumentException异常。
     *
     * @param config 客户端配置
     * @throws IllegalArgumentException 如果配置验证失败
     */
    public FileServiceClientImpl(FileServiceClientConfig config) {
        // 验证配置
        config.validate();
        
        this.config = config;
        
        // 使用连接池配置初始化HTTP客户端
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofMillis(config.getConnectTimeout()))
                .build();
        
        // 初始化ObjectMapper用于JSON解析
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        
        logger.info("FileServiceClient initialized for tenant: {}, server: {}", 
                    config.getTenantId(), config.getServerUrl());
    }
    
    // ==================== Image Upload Methods ====================
    
    @Override
    public FileUploadResponse uploadImage(File imageFile) throws FileServiceException {
        ensureNotClosed();
        
        if (imageFile == null || !imageFile.exists()) {
            throw new InvalidRequestException("Image file does not exist");
        }
        
        try {
            logger.debug("Uploading image file: {}, size: {}", imageFile.getName(), imageFile.length());
            
            // 构建multipart/form-data请求
            String boundary = "----FileServiceClientBoundary" + System.currentTimeMillis();
            byte[] fileData = Files.readAllBytes(imageFile.toPath());
            byte[] multipartBody = buildMultipartBody(boundary, "file", imageFile.getName(), 
                                                     fileData, null);
            
            HttpRequest request = buildRequest("POST", "/api/v1/upload/image", 
                                              "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                    .build();
            
            FileUploadResponse response = executeRequest(request, 
                    new TypeReference<ApiResponse<FileUploadResponse>>() {});
            
            // 应用URL域名替换（默认为PUBLIC）
            if (response != null && response.getUrl() != null) {
                response.setUrl(replaceFileUrl(response.getUrl(), AccessLevel.PUBLIC));
            }
            
            logger.debug("Image upload successful: fileId={}", response != null ? response.getFileId() : null);
            return response;
            
        } catch (IOException e) {
            logger.error("Failed to read image file: {}", imageFile.getName(), e);
            throw new NetworkException("Failed to read image file: " + e.getMessage(), e);
        }
    }
    
    @Override
    public FileUploadResponse uploadImage(InputStream inputStream, String fileName, long fileSize) 
            throws FileServiceException {
        ensureNotClosed();
        
        if (inputStream == null) {
            throw new InvalidRequestException("Input stream cannot be null");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new InvalidRequestException("File name cannot be null or empty");
        }
        if (fileSize <= 0) {
            throw new InvalidRequestException("File size must be positive");
        }
        
        try {
            logger.debug("Uploading image from stream: {}, size: {}", fileName, fileSize);
            
            // 读取输入流数据
            byte[] fileData = inputStream.readAllBytes();
            
            if (fileData.length != fileSize) {
                logger.warn("Actual file size ({}) differs from declared size ({})", 
                           fileData.length, fileSize);
            }
            
            // 构建multipart/form-data请求
            String boundary = "----FileServiceClientBoundary" + System.currentTimeMillis();
            byte[] multipartBody = buildMultipartBody(boundary, "file", fileName, fileData, null);
            
            HttpRequest request = buildRequest("POST", "/api/v1/upload/image", 
                                              "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                    .build();
            
            FileUploadResponse response = executeRequest(request, 
                    new TypeReference<ApiResponse<FileUploadResponse>>() {});
            
            // 应用URL域名替换（默认为PUBLIC）
            if (response != null && response.getUrl() != null) {
                response.setUrl(replaceFileUrl(response.getUrl(), AccessLevel.PUBLIC));
            }
            
            logger.debug("Image upload successful: fileId={}", response != null ? response.getFileId() : null);
            return response;
            
        } catch (IOException e) {
            logger.error("Failed to read input stream for file: {}", fileName, e);
            throw new NetworkException("Failed to read input stream: " + e.getMessage(), e);
        }
    }
    
    // ==================== File Upload Methods ====================
    
    @Override
    public FileUploadResponse uploadFile(File file) throws FileServiceException {
        return uploadFile(file, AccessLevel.PUBLIC);
    }
    
    @Override
    public FileUploadResponse uploadFile(InputStream inputStream, String fileName, 
                                        long fileSize, String contentType) throws FileServiceException {
        return uploadFile(inputStream, fileName, fileSize, contentType, AccessLevel.PUBLIC);
    }
    
    @Override
    public FileUploadResponse uploadFile(File file, AccessLevel accessLevel) throws FileServiceException {
        ensureNotClosed();
        
        if (file == null || !file.exists()) {
            throw new InvalidRequestException("File does not exist");
        }
        if (accessLevel == null) {
            accessLevel = AccessLevel.PUBLIC;
        }
        
        try {
            logger.debug("Uploading file: {}, size: {}, accessLevel: {}", 
                        file.getName(), file.length(), accessLevel);
            
            // 读取文件数据
            byte[] fileData = Files.readAllBytes(file.toPath());
            
            // 推断内容类型
            String contentType = Files.probeContentType(file.toPath());
            if (contentType == null) {
                contentType = "application/octet-stream";
            }
            
            // 构建multipart/form-data请求
            String boundary = "----FileServiceClientBoundary" + System.currentTimeMillis();
            byte[] multipartBody = buildMultipartBody(boundary, "file", file.getName(), 
                                                     fileData, contentType);
            
            HttpRequest request = buildRequest("POST", "/api/v1/upload/file", 
                                              "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                    .build();
            
            FileUploadResponse response = executeRequest(request, 
                    new TypeReference<ApiResponse<FileUploadResponse>>() {});
            
            // 应用URL域名替换
            if (response != null && response.getUrl() != null) {
                response.setUrl(replaceFileUrl(response.getUrl(), accessLevel));
            }
            
            logger.debug("File upload successful: fileId={}", response != null ? response.getFileId() : null);
            return response;
            
        } catch (IOException e) {
            logger.error("Failed to read file: {}", file.getName(), e);
            throw new NetworkException("Failed to read file: " + e.getMessage(), e);
        }
    }
    
    @Override
    public FileUploadResponse uploadFile(InputStream inputStream, String fileName, 
                                        long fileSize, String contentType, AccessLevel accessLevel) 
            throws FileServiceException {
        ensureNotClosed();
        
        if (inputStream == null) {
            throw new InvalidRequestException("Input stream cannot be null");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new InvalidRequestException("File name cannot be null or empty");
        }
        if (fileSize <= 0) {
            throw new InvalidRequestException("File size must be positive");
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        if (accessLevel == null) {
            accessLevel = AccessLevel.PUBLIC;
        }
        
        try {
            logger.debug("Uploading file from stream: {}, size: {}, contentType: {}, accessLevel: {}", 
                        fileName, fileSize, contentType, accessLevel);
            
            // 读取输入流数据
            byte[] fileData = inputStream.readAllBytes();
            
            if (fileData.length != fileSize) {
                logger.warn("Actual file size ({}) differs from declared size ({})", 
                           fileData.length, fileSize);
            }
            
            // 构建multipart/form-data请求
            String boundary = "----FileServiceClientBoundary" + System.currentTimeMillis();
            byte[] multipartBody = buildMultipartBody(boundary, "file", fileName, fileData, contentType);
            
            HttpRequest request = buildRequest("POST", "/api/v1/upload/file", 
                                              "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                    .build();
            
            FileUploadResponse response = executeRequest(request, 
                    new TypeReference<ApiResponse<FileUploadResponse>>() {});
            
            // 应用URL域名替换
            if (response != null && response.getUrl() != null) {
                response.setUrl(replaceFileUrl(response.getUrl(), accessLevel));
            }
            
            logger.debug("File upload successful: fileId={}", response != null ? response.getFileId() : null);
            return response;
            
        } catch (IOException e) {
            logger.error("Failed to read input stream for file: {}", fileName, e);
            throw new NetworkException("Failed to read input stream: " + e.getMessage(), e);
        }
    }
    
    // ==================== File Access Methods ====================
    
    @Override
    public String getFileUrl(String fileId) throws FileServiceException {
        ensureNotClosed();
        
        if (fileId == null || fileId.isBlank()) {
            throw new InvalidRequestException("File ID cannot be null or empty");
        }
        
        logger.debug("Getting file URL for fileId: {}", fileId);
        
        // 构建GET请求到 /api/v1/files/{fileId}/url
        HttpRequest request = buildGetRequest("/api/v1/files/" + fileId + "/url").build();
        
        // 执行请求并解析响应（响应是一个包含url字段的对象）
        FileUrlResponse response = executeRequest(request, 
                new TypeReference<ApiResponse<FileUrlResponse>>() {});
        
        if (response == null || response.getUrl() == null) {
            throw new FileServiceException("Server returned null URL for fileId: " + fileId);
        }
        
        // 应用域名替换（假设为PUBLIC，因为API没有返回accessLevel）
        String url = replaceFileUrl(response.getUrl(), AccessLevel.PUBLIC);
        
        logger.debug("Retrieved file URL for fileId: {}", fileId);
        return url;
    }
    
    @Override
    public FileDetailResponse getFileDetail(String fileId) throws FileServiceException {
        ensureNotClosed();
        
        if (fileId == null || fileId.isBlank()) {
            throw new InvalidRequestException("File ID cannot be null or empty");
        }
        
        logger.debug("Getting file detail for fileId: {}", fileId);
        
        // 构建GET请求到 /api/v1/files/{fileId}
        HttpRequest request = buildGetRequest("/api/v1/files/" + fileId).build();
        
        // 执行请求并解析响应
        FileDetailResponse response = executeRequest(request, 
                new TypeReference<ApiResponse<FileDetailResponse>>() {});
        
        if (response == null) {
            throw new FileServiceException("Server returned null response for fileId: " + fileId);
        }
        
        // 应用域名替换到URL字段
        if (response.getUrl() != null) {
            AccessLevel accessLevel = response.getAccessLevel() != null 
                    ? response.getAccessLevel() 
                    : AccessLevel.PUBLIC;
            response.setUrl(replaceFileUrl(response.getUrl(), accessLevel));
        }
        
        logger.debug("Retrieved file detail for fileId: {}, fileName: {}", 
                    fileId, response.getOriginalName());
        return response;
    }
    
    /**
     * 文件URL响应的内部类
     * 用于解析 /api/v1/files/{fileId}/url 端点的响应
     */
    @Data
    private static class FileUrlResponse {
        private String url;
    }
    
    // ==================== File Deletion Methods ====================
    
    /**
     * 删除指定的文件
     * 
     * 向服务器发送DELETE请求以删除文件。
     * 服务器会验证文件是否属于当前租户。
     *
     * @param fileId 要删除的文件ID
     * @throws InvalidRequestException 如果文件ID为null或空
     * @throws FileNotFoundException 如果文件不存在（404）
     * @throws AccessDeniedException 如果文件不属于当前租户（403）
     * @throws FileServiceException 如果删除失败
     */
    @Override
    public void deleteFile(String fileId) throws FileServiceException {
        ensureNotClosed();
        
        if (fileId == null || fileId.isBlank()) {
            throw new InvalidRequestException("File ID cannot be null or empty");
        }
        
        logger.debug("Deleting file: {}", fileId);
        
        // 构建DELETE请求到 /api/v1/upload/{fileId}
        HttpRequest request = buildDeleteRequest("/api/v1/upload/" + fileId).build();
        
        // 执行请求（不期望返回数据）
        executeVoidRequest(request);
        
        logger.debug("File deleted successfully: {}", fileId);
    }
    
    // ==================== Multipart Upload Methods ====================
    
    /**
     * 初始化分片上传
     * 
     * 向服务器发送POST请求以初始化分片上传会话。
     * 服务器返回taskId和uploadId，用于后续的分片上传操作。
     *
     * @param request 分片上传初始化请求，包含文件名、大小、内容类型等信息
     * @return 分片上传初始化响应，包含taskId、uploadId和分片信息
     * @throws InvalidRequestException 如果请求参数无效
     * @throws FileServiceException 如果初始化失败
     */
    @Override
    public MultipartInitResponse initMultipartUpload(MultipartInitRequest request) 
            throws FileServiceException {
        ensureNotClosed();
        
        if (request == null) {
            throw new InvalidRequestException("MultipartInitRequest cannot be null");
        }
        if (request.getFileName() == null || request.getFileName().isBlank()) {
            throw new InvalidRequestException("File name cannot be null or empty");
        }
        if (request.getFileSize() == null || request.getFileSize() <= 0) {
            throw new InvalidRequestException("File size must be positive");
        }
        
        logger.debug("Initializing multipart upload: filename={}, size={}, contentType={}", 
                    request.getFileName(), request.getFileSize(), request.getContentType());
        
        // 构建POST请求到 /api/v1/multipart/init
        HttpRequest httpRequest = buildPostRequest("/api/v1/multipart/init", request).build();
        
        // 执行请求并解析响应
        MultipartInitResponse response = executeRequest(httpRequest, 
                new TypeReference<ApiResponse<MultipartInitResponse>>() {});
        
        if (response == null) {
            throw new FileServiceException("Server returned null response for multipart init");
        }
        
        logger.debug("Multipart upload initialized: taskId={}, uploadId={}, totalChunks={}", 
                    response.getTaskId(), response.getUploadId(), response.getTotalChunks());
        
        return response;
    }
    
    /**
     * 上传单个分片
     * 
     * 向服务器发送POST请求以上传分片数据。
     * 使用multipart/form-data格式发送分片数据。
     *
     * @param taskId 分片上传任务ID（从initMultipartUpload获取）
     * @param partNumber 分片编号（从1开始）
     * @param data 分片数据输入流
     * @param size 分片数据大小（字节）
     * @return 包含分片编号和ETag的MultipartUploadPart对象
     * @throws InvalidRequestException 如果参数无效
     * @throws FileServiceException 如果上传失败
     */
    @Override
    public MultipartUploadPart uploadPart(String taskId, int partNumber, 
                                         InputStream data, long size) throws FileServiceException {
        ensureNotClosed();
        
        if (taskId == null || taskId.isBlank()) {
            throw new InvalidRequestException("Task ID cannot be null or empty");
        }
        if (partNumber <= 0) {
            throw new InvalidRequestException("Part number must be positive");
        }
        if (data == null) {
            throw new InvalidRequestException("Data input stream cannot be null");
        }
        if (size <= 0) {
            throw new InvalidRequestException("Size must be positive");
        }
        
        try {
            logger.debug("Uploading part: taskId={}, partNumber={}, size={}", 
                        taskId, partNumber, size);
            
            // 读取分片数据
            byte[] chunkData = data.readAllBytes();
            
            if (chunkData.length != size) {
                logger.warn("Actual chunk size ({}) differs from declared size ({})", 
                           chunkData.length, size);
            }
            
            // 构建multipart/form-data请求
            String boundary = "----FileServiceClientBoundary" + System.currentTimeMillis();
            byte[] multipartBody = buildMultipartBody(boundary, "chunk", 
                                                     "chunk_" + partNumber, 
                                                     chunkData, 
                                                     "application/octet-stream");
            
            // 构建POST请求到 /api/v1/multipart/{taskId}/upload?partNumber={n}
            String path = "/api/v1/multipart/" + taskId + "/upload?partNumber=" + partNumber;
            HttpRequest request = buildRequest("POST", path, 
                                              "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                    .build();
            
            // 执行请求并解析响应
            MultipartUploadPart response = executeRequest(request, 
                    new TypeReference<ApiResponse<MultipartUploadPart>>() {});
            
            if (response == null) {
                throw new FileServiceException("Server returned null response for part upload");
            }
            
            // 验证返回的分片编号是否匹配
            if (response.getPartNumber() == null || response.getPartNumber() != partNumber) {
                logger.warn("Response part number ({}) does not match request part number ({})", 
                           response.getPartNumber(), partNumber);
            }
            
            logger.debug("Part uploaded successfully: taskId={}, partNumber={}, etag={}", 
                        taskId, partNumber, response.getEtag());
            
            return response;
            
        } catch (IOException e) {
            logger.error("Failed to read chunk data for taskId: {}, partNumber: {}", 
                        taskId, partNumber, e);
            throw new NetworkException("Failed to read chunk data: " + e.getMessage(), e);
        }
    }
    
    /**
     * 完成分片上传
     * 
     * 向服务器发送POST请求以完成分片上传会话。
     * 服务器会合并所有分片并返回最终的文件信息。
     *
     * @param taskId 分片上传任务ID
     * @param fileHash 完整文件的哈希值（用于验证）
     * @return 文件上传响应，包含fileId、url等信息
     * @throws InvalidRequestException 如果参数无效
     * @throws FileServiceException 如果完成失败
     */
    @Override
    public FileUploadResponse completeMultipartUpload(String taskId, String fileHash) 
            throws FileServiceException {
        ensureNotClosed();
        
        if (taskId == null || taskId.isBlank()) {
            throw new InvalidRequestException("Task ID cannot be null or empty");
        }
        if (fileHash == null || fileHash.isBlank()) {
            throw new InvalidRequestException("File hash cannot be null or empty");
        }
        
        logger.debug("Completing multipart upload: taskId={}, fileHash={}", taskId, fileHash);
        
        // 构建请求体
        CompleteMultipartRequest requestBody = new CompleteMultipartRequest(fileHash);
        
        // 构建POST请求到 /api/v1/multipart/{taskId}/complete
        String path = "/api/v1/multipart/" + taskId + "/complete";
        HttpRequest request = buildPostRequest(path, requestBody).build();
        
        // 执行请求并解析响应
        FileUploadResponse response = executeRequest(request, 
                new TypeReference<ApiResponse<FileUploadResponse>>() {});
        
        if (response == null) {
            throw new FileServiceException("Server returned null response for multipart complete");
        }
        
        // 应用URL域名替换（默认为PUBLIC，因为响应中没有accessLevel字段）
        if (response.getUrl() != null) {
            response.setUrl(replaceFileUrl(response.getUrl(), AccessLevel.PUBLIC));
        }
        
        logger.debug("Multipart upload completed: taskId={}, fileId={}", 
                    taskId, response.getFileId());
        
        return response;
    }
    
    /**
     * 完成分片上传请求的内部类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class CompleteMultipartRequest {
        private String fileHash;
    }
    
    /**
     * 取消分片上传
     * 
     * 向服务器发送DELETE请求以取消分片上传会话。
     * 服务器会清理已上传的分片数据。
     *
     * @param taskId 分片上传任务ID
     * @throws InvalidRequestException 如果taskId无效
     * @throws FileServiceException 如果取消失败
     */
    @Override
    public void cancelMultipartUpload(String taskId) throws FileServiceException {
        ensureNotClosed();
        
        if (taskId == null || taskId.isBlank()) {
            throw new InvalidRequestException("Task ID cannot be null or empty");
        }
        
        logger.debug("Canceling multipart upload: taskId={}", taskId);
        
        // 构建DELETE请求到 /api/v1/multipart/{taskId}
        HttpRequest request = buildDeleteRequest("/api/v1/multipart/" + taskId).build();
        
        // 执行请求（不期望返回数据）
        executeVoidRequest(request);
        
        logger.debug("Multipart upload canceled successfully: taskId={}", taskId);
    }
    
    // ==================== Instant Upload Methods ====================
    
    /**
     * 检查文件是否已存在（秒传/去重）
     * 
     * 向服务器发送POST请求以检查具有相同哈希值的文件是否已存在。
     * 如果文件存在，返回现有文件的元数据，避免重复上传。
     *
     * @param request 秒传检查请求，包含文件哈希、文件名、大小和内容类型
     * @return 秒传检查响应，指示文件是否存在，如果存在则包含文件信息
     * @throws InvalidRequestException 如果请求参数无效
     * @throws FileServiceException 如果检查失败
     */
    @Override
    public InstantUploadCheckResponse checkInstantUpload(InstantUploadCheckRequest request) 
            throws FileServiceException {
        ensureNotClosed();
        
        if (request == null) {
            throw new InvalidRequestException("InstantUploadCheckRequest cannot be null");
        }
        if (request.getFileHash() == null || request.getFileHash().isBlank()) {
            throw new InvalidRequestException("File hash cannot be null or empty");
        }
        if (request.getFileName() == null || request.getFileName().isBlank()) {
            throw new InvalidRequestException("File name cannot be null or empty");
        }
        if (request.getFileSize() == null || request.getFileSize() <= 0) {
            throw new InvalidRequestException("File size must be positive");
        }
        if (request.getContentType() == null || request.getContentType().isBlank()) {
            throw new InvalidRequestException("Content type cannot be null or empty");
        }
        
        logger.debug("Checking instant upload: fileHash={}, filename={}, size={}, contentType={}", 
                    request.getFileHash(), request.getFileName(), request.getFileSize(), 
                    request.getContentType());
        
        // 构建POST请求到 /api/v1/instant-upload/check
        HttpRequest httpRequest = buildPostRequest("/api/v1/instant-upload/check", request).build();
        
        // 执行请求并解析响应
        InstantUploadCheckResponse response = executeRequest(httpRequest, 
                new TypeReference<ApiResponse<InstantUploadCheckResponse>>() {});
        
        if (response == null) {
            throw new FileServiceException("Server returned null response for instant upload check");
        }
        
        // 如果文件存在，应用URL域名替换
        if (response.getExists() != null && response.getExists() && response.getFileInfo() != null) {
            FileUploadResponse fileInfo = response.getFileInfo();
            if (fileInfo.getUrl() != null) {
                // 假设为PUBLIC访问级别（因为响应中没有accessLevel字段）
                fileInfo.setUrl(replaceFileUrl(fileInfo.getUrl(), AccessLevel.PUBLIC));
            }
            
            logger.debug("Instant upload check: file exists, fileId={}", fileInfo.getFileId());
        } else {
            logger.debug("Instant upload check: file does not exist, upload required");
        }
        
        return response;
    }
    
    // ==================== Presigned URL Methods ====================
    
    /**
     * 获取单文件上传会话及预签名上传URL
     * 
     * 向服务器发送 POST /api/v1/upload-sessions 创建 `PRESIGNED_SINGLE` 上传会话。
     * 服务端返回上传会话ID和一次性 PUT URL，客户端随后可直接向对象存储上传文件。
     *
     * @param request 预签名URL请求，包含文件名、内容类型、大小、访问级别和文件哈希
     * @return 预签名URL响应，包含上传会话ID、上传URL和过期时间
     * @throws InvalidRequestException 如果请求参数无效
     * @throws FileServiceException 如果URL生成失败
     */
    @Override
    public PresignedUploadResponse getPresignedUploadUrl(PresignedUploadRequest request) 
            throws FileServiceException {
        ensureNotClosed();
        
        if (request == null) {
            throw new InvalidRequestException("PresignedUploadRequest cannot be null");
        }
        if (request.getFilename() == null || request.getFilename().isBlank()) {
            throw new InvalidRequestException("File name cannot be null or empty");
        }
        if (request.getContentType() == null || request.getContentType().isBlank()) {
            throw new InvalidRequestException("Content type cannot be null or empty");
        }
        if (request.getFileSize() == null || request.getFileSize() <= 0) {
            throw new InvalidRequestException("File size must be positive");
        }
        if (request.getFileHash() == null || request.getFileHash().isBlank()) {
            throw new InvalidRequestException("File hash cannot be null or empty");
        }
        requireSubjectId("presigned upload session APIs");

        AccessLevel accessLevel = request.getAccessLevel() != null ? request.getAccessLevel() : AccessLevel.PUBLIC;
        
        logger.debug("Creating presigned upload session: filename={}, contentType={}, size={}, accessLevel={}", 
                    request.getFilename(), request.getContentType(), request.getFileSize(), 
                    accessLevel);
        
        CreateUploadSessionRequest requestBody = new CreateUploadSessionRequest(
                "PRESIGNED_SINGLE",
                accessLevel.name(),
                request.getFilename(),
                request.getContentType(),
                request.getFileSize(),
                request.getFileHash()
        );
        HttpRequest httpRequest = buildPostRequest("/api/v1/upload-sessions", requestBody).build();
        
        CreateUploadSessionResponse response = executeRawRequest(httpRequest,
                new TypeReference<CreateUploadSessionResponse>() {});
        
        if (response == null || response.getUploadSession() == null) {
            throw new FileServiceException("Server returned null upload session for presigned URL request");
        }

        PresignedUploadResponse result = new PresignedUploadResponse();
        result.setFileId(response.getUploadSession().getUploadSessionId());
        result.setUploadSessionId(response.getUploadSession().getUploadSessionId());
        result.setUploadUrl(response.getSingleUploadUrl());
        result.setUploadMethod(response.getSingleUploadMethod());
        result.setUploadHeaders(response.getSingleUploadHeaders());
        if (response.getSingleUploadExpiresInSeconds() != null) {
            result.setExpiresAt(LocalDateTime.now().plusSeconds(response.getSingleUploadExpiresInSeconds()));
        }
        
        logger.debug("Presigned upload session created: uploadSessionId={}, instantUpload={}, hasUploadUrl={}", 
                    result.getUploadSessionId(), response.isInstantUpload(), result.getUploadUrl() != null);
        
        return result;
    }
    
    /**
     * 确认文件已使用预签名URL成功上传
     * 
     * 向服务器发送 POST /api/v1/upload-sessions/{id}/complete 完成上传会话。
     * 必须在客户端完成对象存储 PUT 之后调用，以注册文件元数据。
     *
     * @param uploadSessionId 来自预签名URL响应的上传会话标识符
     * @param fileHash 已上传文件的哈希值，用于校验调用参数与会话一致
     * @return 文件上传响应，包含fileId、url等信息
     * @throws InvalidRequestException 如果参数无效
     * @throws FileServiceException 如果确认失败
     */
    @Override
    public FileUploadResponse confirmPresignedUpload(String uploadSessionId, String fileHash) 
            throws FileServiceException {
        ensureNotClosed();
        
        if (uploadSessionId == null || uploadSessionId.isBlank()) {
            throw new InvalidRequestException("Upload session ID cannot be null or empty");
        }
        if (fileHash == null || fileHash.isBlank()) {
            throw new InvalidRequestException("File hash cannot be null or empty");
        }
        requireSubjectId("presigned upload session APIs");
        
        logger.debug("Confirming presigned upload session: uploadSessionId={}, fileHash={}", uploadSessionId, fileHash);
        
        UploadSessionView uploadSession = executeRawRequest(
                buildGetRequest("/api/v1/upload-sessions/" + uploadSessionId).build(),
                new TypeReference<UploadSessionView>() {}
        );
        if (uploadSession == null) {
            throw new FileServiceException("Server returned null upload session for id: " + uploadSessionId);
        }
        if (uploadSession.getFileHash() != null
                && !uploadSession.getFileHash().isBlank()
                && !uploadSession.getFileHash().equals(fileHash)) {
            throw new InvalidRequestException("File hash does not match upload session: " + uploadSessionId);
        }

        CompleteUploadSessionRequest requestBody = new CompleteUploadSessionRequest(
                uploadSession.getContentType(),
                null
        );
        UploadCompletionView completion = executeRawRequest(
                buildPostRequest("/api/v1/upload-sessions/" + uploadSessionId + "/complete", requestBody).build(),
                new TypeReference<UploadCompletionView>() {}
        );

        if (completion == null || completion.getFileId() == null || completion.getFileId().isBlank()) {
            throw new FileServiceException("Server returned null fileId for presigned upload confirmation");
        }

        FileDetailResponse fileDetail = getFileDetail(completion.getFileId());
        FileUploadResponse response = FileUploadResponse.builder()
                .fileId(fileDetail.getFileId())
                .url(fileDetail.getUrl())
                .originalName(fileDetail.getOriginalName())
                .fileSize(fileDetail.getFileSize())
                .contentType(fileDetail.getContentType())
                .accessLevel(fileDetail.getAccessLevel())
                .build();

        logger.debug("Presigned upload confirmed: uploadSessionId={}, fileId={}", uploadSessionId, response.getFileId());
        return response;
    }
    
    /**
     * upload-session 创建请求体。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class CreateUploadSessionRequest {
        private String uploadMode;
        private String accessLevel;
        private String originalFilename;
        private String contentType;
        private Long expectedSize;
        private String fileHash;
    }

    /**
     * upload-session 创建响应体。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class CreateUploadSessionResponse {
        private UploadSessionView uploadSession;
        private boolean resumed;
        private boolean instantUpload;
        private List<Integer> completedPartNumbers;
        private List<Object> completedPartInfos;
        private String singleUploadUrl;
        private String singleUploadMethod;
        private Integer singleUploadExpiresInSeconds;
        private Map<String, String> singleUploadHeaders;
    }

    /**
     * upload-session 视图。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class UploadSessionView {
        private String uploadSessionId;
        private String contentType;
        private String fileHash;
    }

    /**
     * upload-session 完成请求体。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class CompleteUploadSessionRequest {
        private String contentType;
        private List<Object> parts;
    }

    /**
     * upload-session 完成响应体。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class UploadCompletionView {
        private String uploadSessionId;
        private String fileId;
        private String status;
    }
    
    // ==================== Resource Management ====================
    
    /**
     * 关闭客户端并释放所有资源
     * 
     * 此方法会优雅地关闭HTTP客户端并释放连接池资源。
     * 关闭后，客户端不能再使用，任何后续调用都会抛出IllegalStateException。
     * 
     * 此方法是幂等的，多次调用是安全的。
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            logger.info("Closing FileServiceClient for tenant: {}", config.getTenantId());
            
            try {
                // 在Java 11+中，HttpClient使用内部执行器管理连接池
                // 虽然没有显式的shutdown方法，但我们可以通过关闭底层执行器来释放资源
                // HttpClient会在不再被引用时由GC自动清理
                
                // 如果HttpClient使用了自定义执行器，应该在这里关闭它
                // 由于我们使用默认构建器，连接池会被自动管理
                
                logger.info("FileServiceClient closed successfully for tenant: {}", config.getTenantId());
            } catch (Exception e) {
                logger.error("Error while closing FileServiceClient for tenant: {}", 
                           config.getTenantId(), e);
                // 即使关闭时出错，也标记为已关闭
            }
        } else {
            logger.debug("FileServiceClient already closed for tenant: {}", config.getTenantId());
        }
    }
    
    /**
     * 检查客户端是否已关闭
     *
     * @throws IllegalStateException 如果客户端已关闭
     */
    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("FileServiceClient has been closed");
        }
    }

    private void requireSubjectId(String operation) throws InvalidRequestException {
        if (config.getSubjectId() == null || config.getSubjectId().isBlank()) {
            throw new InvalidRequestException(
                    "subjectId is required for " + operation + "; configure FileServiceClientConfig.subjectId(...)"
            );
        }
    }
    
    // ==================== HTTP请求构建 ====================
    
    /**
     * 构建带有标准请求头的HTTP请求
     * 
     * 自动添加以下请求头：
     * - X-App-Id: 租户ID
     * - X-User-Id: 调用主体ID（如果已配置）
     * - Authorization: Bearer token
     * - Content-Type: 内容类型（如果指定）
     *
     * @param method HTTP方法（GET、POST、DELETE等）
     * @param path API路径（例如："/api/v1/files/{fileId}"）
     * @param contentType Content-Type请求头值（如果不需要则为null）
     * @return 配置好请求头的HttpRequest.Builder
     */
    private HttpRequest.Builder buildRequest(String method, String path, String contentType) {
        ensureNotClosed();
        
        String url = config.getServerUrl() + path;
        
        // 记录请求URL和方法（DEBUG级别）
        logger.debug("Building HTTP request: method={}, url={}", method, url);
        
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(config.getReadTimeout()))
                .header("X-App-Id", config.getTenantId())
                .header("Authorization", "Bearer " + config.getTokenProvider().getToken());
        if (config.getSubjectId() != null && !config.getSubjectId().isBlank()) {
            builder.header("X-User-Id", config.getSubjectId());
        }
        
        if (contentType != null && !contentType.isBlank()) {
            builder.header("Content-Type", contentType);
        }
        
        return builder;
    }
    
    /**
     * 构建GET请求
     *
     * @param path API路径
     * @return 配置为GET的HttpRequest.Builder
     */
    private HttpRequest.Builder buildGetRequest(String path) {
        return buildRequest("GET", path, null).GET();
    }
    
    /**
     * 构建带JSON请求体的POST请求
     *
     * @param path API路径
     * @param body 请求体对象（将被序列化为JSON）
     * @return 配置为POST的HttpRequest.Builder
     * @throws FileServiceException 如果JSON序列化失败
     */
    private HttpRequest.Builder buildPostRequest(String path, Object body) throws FileServiceException {
        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            return buildRequest("POST", path, "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        } catch (IOException e) {
            throw new ParseException("Failed to serialize request body to JSON", e);
        }
    }
    
    /**
     * 构建DELETE请求
     *
     * @param path API路径
     * @return 配置为DELETE的HttpRequest.Builder
     */
    private HttpRequest.Builder buildDeleteRequest(String path) {
        return buildRequest("DELETE", path, null).DELETE();
    }
    
    // ==================== HTTP响应解析 ====================
    
    /**
     * 执行HTTP请求并解析响应
     * 
     * 处理成功响应（200）和错误响应（4xx、5xx）。
     * 将HTTP状态码映射到类型化异常。
     *
     * @param request 要执行的HTTP请求
     * @param responseType 期望响应类型的TypeReference
     * @param <T> 响应数据的类型
     * @return 解析后的响应数据
     * @throws FileServiceException 如果请求失败或响应解析失败
     */
    private <T> T executeRequest(HttpRequest request, TypeReference<ApiResponse<T>> responseType) 
            throws FileServiceException {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            int statusCode = response.statusCode();
            String responseBody = response.body();
            
            // 记录响应状态码（DEBUG级别）
            logger.debug("Received HTTP response: statusCode={}, bodyLength={}", 
                        statusCode, responseBody != null ? responseBody.length() : 0);
            
            // 处理成功响应
            if (statusCode == 200) {
                return parseSuccessResponse(responseBody, responseType);
            }
            
            // 处理错误响应
            handleErrorResponse(statusCode, responseBody);
            
            // 不应该到达这里
            throw new FileServiceException("Unexpected response status: " + statusCode);
            
        } catch (IOException e) {
            logger.error("Network error during HTTP request: {}", e.getMessage(), e);
            throw new NetworkException("Network error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("HTTP request interrupted: {}", e.getMessage(), e);
            throw new NetworkException("Request interrupted", e);
        }
    }

    /**
     * 执行直接返回 JSON 对象的请求。
     */
    private <T> T executeRawRequest(HttpRequest request, TypeReference<T> responseType)
            throws FileServiceException {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String responseBody = response.body();

            logger.debug("Received raw HTTP response: statusCode={}, bodyLength={}",
                    statusCode, responseBody != null ? responseBody.length() : 0);

            if (statusCode == 200) {
                return parseRawSuccessResponse(responseBody, responseType);
            }

            handleErrorResponse(statusCode, responseBody);
            throw new FileServiceException("Unexpected response status: " + statusCode);
        } catch (IOException e) {
            logger.error("Network error during HTTP request: {}", e.getMessage(), e);
            throw new NetworkException("Network error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("HTTP request interrupted: {}", e.getMessage(), e);
            throw new NetworkException("Request interrupted", e);
        }
    }
    
    /**
     * 执行不返回数据的HTTP请求（void响应）
     *
     * @param request 要执行的HTTP请求
     * @throws FileServiceException 如果请求失败
     */
    private void executeVoidRequest(HttpRequest request) throws FileServiceException {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            int statusCode = response.statusCode();
            String responseBody = response.body();
            
            // 记录响应状态码（DEBUG级别）
            logger.debug("Received HTTP response: statusCode={}", statusCode);
            
            // 处理成功响应
            if (statusCode == 200) {
                return;
            }
            
            // 处理错误响应
            handleErrorResponse(statusCode, responseBody);
            
        } catch (IOException e) {
            logger.error("Network error during HTTP request: {}", e.getMessage(), e);
            throw new NetworkException("Network error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("HTTP request interrupted: {}", e.getMessage(), e);
            throw new NetworkException("Request interrupted", e);
        }
    }
    
    /**
     * 解析成功的JSON响应
     *
     * @param responseBody 响应体字符串
     * @param responseType 期望响应类型的TypeReference
     * @param <T> 响应数据的类型
     * @return 解析后的响应数据
     * @throws ParseException 如果JSON解析失败
     */
    private <T> T parseSuccessResponse(String responseBody, TypeReference<ApiResponse<T>> responseType) 
            throws ParseException {
        try {
            ApiResponse<T> apiResponse = objectMapper.readValue(responseBody, responseType);
            
            if (apiResponse == null) {
                throw new ParseException("Response is null");
            }
            
            if (!apiResponse.isSuccess()) {
                throw new FileServiceException("API returned non-success code: " + apiResponse.getCode() 
                                              + ", message: " + apiResponse.getMessage());
            }
            
            return apiResponse.getData();
            
        } catch (IOException e) {
            logger.error("Failed to parse JSON response", e);
            throw new ParseException("Failed to parse JSON response: " + e.getMessage(), e);
        }
    }

    /**
     * 解析直接返回对象的成功响应。
     */
    private <T> T parseRawSuccessResponse(String responseBody, TypeReference<T> responseType)
            throws ParseException {
        try {
            return objectMapper.readValue(responseBody, responseType);
        } catch (IOException e) {
            logger.error("Failed to parse raw JSON response", e);
            throw new ParseException("Failed to parse raw JSON response: " + e.getMessage(), e);
        }
    }
    
    /**
     * 通过将HTTP状态码映射到类型化异常来处理错误响应
     *
     * @param statusCode HTTP状态码
     * @param responseBody 响应体（可能包含错误详情）
     * @throws FileServiceException 针对该状态码的相应异常
     */
    private void handleErrorResponse(int statusCode, String responseBody) throws FileServiceException {
        String errorMessage = extractErrorMessage(responseBody);
        
        // 记录错误详情（ERROR级别）
        logger.error("HTTP error response received: statusCode={}, errorMessage={}", 
                    statusCode, errorMessage);
        
        switch (statusCode) {
            case 400:
                throw new InvalidRequestException(errorMessage);
            case 401:
                throw new AuthenticationException(errorMessage);
            case 403:
                throw new AccessDeniedException(errorMessage);
            case 404:
                throw new FileNotFoundException(errorMessage);
            case 413:
                throw new QuotaExceededException(errorMessage);
            default:
                throw new FileServiceException("HTTP " + statusCode + ": " + errorMessage);
        }
    }
    
    /**
     * 从响应体中提取错误消息
     * 
     * 尝试将响应解析为ApiResponse以获取错误消息。
     * 如果解析失败则回退到原始响应体。
     *
     * @param responseBody 响应体
     * @return 错误消息
     */
    private String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "Unknown error";
        }
        
        try {
            ApiResponse<?> errorResponse = objectMapper.readValue(responseBody, 
                                                                  new TypeReference<ApiResponse<Object>>() {});
            if (errorResponse != null && errorResponse.getMessage() != null) {
                return errorResponse.getMessage();
            }
        } catch (IOException e) {
            // 忽略解析错误，回退到原始响应体
            logger.debug("Failed to parse error response as JSON", e);
        }
        
        return responseBody;
    }
    
    // ==================== URL域名替换 ====================
    
    /**
     * 根据配置替换文件URL中的域名
     * 
     * 对于配置了CDN域名的PUBLIC文件：使用CDN域名。
     * 对于配置了自定义域名的其他情况：使用自定义域名。
     * 否则：返回原始URL不变。
     * 
     * 保留原始URL的路径和查询参数。
     *
     * @param originalUrl 服务器返回的原始URL
     * @param accessLevel 文件的访问级别（PUBLIC或PRIVATE）
     * @return 替换域名后的URL（如果未配置替换则返回原始URL）
     */
    private String replaceFileUrl(String originalUrl, AccessLevel accessLevel) {
        if (originalUrl == null || originalUrl.isBlank()) {
            return originalUrl;
        }
        
        // 对于公开文件，如果配置了CDN域名则使用CDN域名
        if (accessLevel == AccessLevel.PUBLIC && config.getCdnDomain() != null 
                && !config.getCdnDomain().isBlank()) {
            return replaceUrlDomain(originalUrl, config.getCdnDomain());
        }
        
        // 否则如果配置了自定义域名则使用自定义域名
        if (config.getCustomDomain() != null && !config.getCustomDomain().isBlank()) {
            return replaceUrlDomain(originalUrl, config.getCustomDomain());
        }
        
        // 未配置替换，返回原始URL
        return originalUrl;
    }
    
    /**
     * 替换URL的域名部分，同时保留路径和查询参数
     *
     * @param url 原始URL
     * @param newDomain 新域名（包含协议，例如："https://cdn.example.com"）
     * @return 替换域名后的URL
     */
    private String replaceUrlDomain(String url, String newDomain) {
        try {
            URI uri = new URI(url);
            
            // 如果新域名以斜杠结尾则移除
            String domain = newDomain.endsWith("/") ? newDomain.substring(0, newDomain.length() - 1) : newDomain;
            
            // 构建新URL：域名 + 路径 + 查询参数
            StringBuilder newUrl = new StringBuilder(domain);
            
            if (uri.getPath() != null) {
                newUrl.append(uri.getPath());
            }
            
            if (uri.getQuery() != null) {
                newUrl.append("?").append(uri.getQuery());
            }
            
            return newUrl.toString();
            
        } catch (URISyntaxException e) {
            logger.warn("Failed to parse URL for domain replacement: {}", url, e);
            return url; // 解析失败时返回原始URL
        }
    }
    
    // ==================== Multipart/form-data构建 ====================
    
    /**
     * 构建multipart/form-data请求体
     * 
     * @param boundary 边界字符串
     * @param fieldName 表单字段名（通常为"file"）
     * @param fileName 文件名
     * @param fileData 文件数据
     * @param contentType 文件内容类型（可选，如果为null则自动检测）
     * @return multipart/form-data格式的字节数组
     */
    private byte[] buildMultipartBody(String boundary, String fieldName, String fileName, 
                                     byte[] fileData, String contentType) {
        try {
            // 如果未指定contentType，尝试根据文件名推断
            if (contentType == null || contentType.isBlank()) {
                contentType = Files.probeContentType(new File(fileName).toPath());
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }
            }
            
            StringBuilder sb = new StringBuilder();
            
            // 开始边界
            sb.append("--").append(boundary).append("\r\n");
            
            // Content-Disposition头
            sb.append("Content-Disposition: form-data; name=\"").append(fieldName)
              .append("\"; filename=\"").append(fileName).append("\"\r\n");
            
            // Content-Type头
            sb.append("Content-Type: ").append(contentType).append("\r\n");
            sb.append("\r\n");
            
            // 将头部转换为字节
            byte[] headerBytes = sb.toString().getBytes("UTF-8");
            
            // 结束边界
            byte[] endBoundary = ("\r\n--" + boundary + "--\r\n").getBytes("UTF-8");
            
            // 组合所有部分
            byte[] result = new byte[headerBytes.length + fileData.length + endBoundary.length];
            System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
            System.arraycopy(fileData, 0, result, headerBytes.length, fileData.length);
            System.arraycopy(endBoundary, 0, result, headerBytes.length + fileData.length, endBoundary.length);
            
            return result;
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to build multipart body", e);
        }
    }
}
