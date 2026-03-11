package com.architectcgz.file.infrastructure.storage;

import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.infrastructure.config.S3Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * S3 兼容存储服务实现
 * 支持 RustFS、MinIO、AWS S3、阿里云 OSS 或S3 兼容存储服务
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {
    
    private final S3Properties properties;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    
    public S3StorageService(S3Properties properties) {
        this.properties = properties;
        this.s3Client = buildS3Client(properties);
        this.s3Presigner = buildS3Presigner(properties);
        log.info("S3StorageService initialized with endpoint: {}, bucket: {}", 
                properties.getEndpoint(), properties.getBucket());
    }
    
    @PostConstruct
    public void init() {
        ensureBucketExists();
    }

    @Override
    public String getDefaultBucketName() {
        return properties.getBucket();
    }

    @Override
    public String getBucketName(com.architectcgz.file.domain.model.AccessLevel accessLevel) {
        return properties.getBucketByAccessLevel(accessLevel);
    }
    
    @Override
    public String upload(byte[] data, String path) {
        return upload(data, path, "application/octet-stream");
    }
    
    @Override
    public String upload(byte[] data, String path, String contentType) {
        try {
            String bucketName = resolveBucket(null);
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(path)
                    .contentType(contentType)
                    .build();
            
            s3Client.putObject(putRequest, RequestBody.fromBytes(data));
            log.debug("File uploaded to S3: bucket={}, key={}", bucketName, path);
            
            return getUrl(bucketName, path);
        } catch (S3Exception e) {
            log.error("Failed to upload file to S3: bucket={}, key={}, error={}", 
                    properties.getBucket(), path, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.FILE_UPLOAD_FAILED, e.getMessage()), e);
        } catch (SdkClientException e) {
            log.error("S3 client error during upload: bucket={}, key={}, error={}", 
                    properties.getBucket(), path, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_CLIENT_ERROR, e.getMessage()), e);
        }
    }
    
    @Override
    public String uploadFromFile(Path file, String storagePath, String contentType) {
        return uploadFromFile(file, storagePath, contentType, com.architectcgz.file.domain.model.AccessLevel.PUBLIC);
    }

    @Override
    public String uploadFromFile(Path file, String storagePath, String contentType,
                                 com.architectcgz.file.domain.model.AccessLevel accessLevel) {
        try {
            String bucketName = resolveBucket(properties.getBucketByAccessLevel(accessLevel));
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storagePath)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromFile(file));
            log.debug("File uploaded to S3 from local file: bucket={}, key={}", bucketName, storagePath);

            if (accessLevel == com.architectcgz.file.domain.model.AccessLevel.PUBLIC) {
                return getPublicUrl(bucketName, storagePath);
            }
            return getUrl(bucketName, storagePath);
        } catch (S3Exception e) {
            log.error("Failed to upload file to S3 from local file: bucket={}, key={}, error={}",
                    properties.getBucket(), storagePath, e.getMessage(), e);
            throw new BusinessException("文件上传失败: " + e.getMessage(), e);
        } catch (SdkClientException e) {
            log.error("S3 client error during file upload: bucket={}, key={}, error={}",
                    properties.getBucket(), storagePath, e.getMessage(), e);
            throw new BusinessException("S3 客户端错误: " + e.getMessage(), e);
        }
    }

    @Override
    public String uploadToPublicBucket(byte[] data, String path, String contentType) {
        try {
            String publicBucket = resolveBucket(properties.getPublicBucket());
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(publicBucket)
                    .key(path)
                    .contentType(contentType)
                    .build();
            
            s3Client.putObject(putRequest, RequestBody.fromBytes(data));
            log.debug("File uploaded to public bucket: bucket={}, key={}", publicBucket, path);
            
            return getPublicUrl(publicBucket, path);
        } catch (S3Exception e) {
            log.error("Failed to upload file to public bucket: bucket={}, key={}, error={}", 
                    properties.getPublicBucket(), path, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_UPLOAD_PUBLIC_FAILED, e.getMessage()), e);
        } catch (SdkClientException e) {
            log.error("S3 client error during public bucket upload: bucket={}, key={}, error={}", 
                    properties.getPublicBucket(), path, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_CLIENT_ERROR, e.getMessage()), e);
        }
    }
    
    @Override
    public String uploadToPrivateBucket(byte[] data, String path, String contentType) {
        try {
            String privateBucket = resolveBucket(properties.getPrivateBucket());
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(privateBucket)
                    .key(path)
                    .contentType(contentType)
                    .build();
            
            s3Client.putObject(putRequest, RequestBody.fromBytes(data));
            log.debug("File uploaded to private bucket: bucket={}, key={}", privateBucket, path);
            
            // 私有文件不返回直接URL，返回路径即可
            return path;
        } catch (S3Exception e) {
            log.error("Failed to upload file to private bucket: bucket={}, key={}, error={}", 
                    properties.getPrivateBucket(), path, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_UPLOAD_PRIVATE_FAILED, e.getMessage()), e);
        } catch (SdkClientException e) {
            log.error("S3 client error during private bucket upload: bucket={}, key={}, error={}", 
                    properties.getPrivateBucket(), path, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_CLIENT_ERROR, e.getMessage()), e);
        }
    }
    
    @Override
    public String uploadByAccessLevel(byte[] data, String path, String contentType, 
                                     com.architectcgz.file.domain.model.AccessLevel accessLevel) {
        if (accessLevel == com.architectcgz.file.domain.model.AccessLevel.PUBLIC) {
            return uploadToPublicBucket(data, path, contentType);
        } else {
            return uploadToPrivateBucket(data, path, contentType);
        }
    }
    
    @Override
    public void delete(String path) {
        delete(null, path);
    }

    @Override
    public void delete(String bucketName, String path) {
        try {
            String resolvedBucket = resolveBucket(bucketName);
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(resolvedBucket)
                    .key(path)
                    .build();
            
            s3Client.deleteObject(deleteRequest);
            log.debug("File deleted from S3: bucket={}, key={}", resolvedBucket, path);
        } catch (S3Exception e) {
            log.error("Failed to delete file from S3: bucket={}, key={}, error={}", 
                    resolveBucket(bucketName), path, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.FILE_DELETE_FAILED, e.getMessage()), e);
        } catch (SdkClientException e) {
            log.error("S3 client error during delete: bucket={}, key={}, error={}", 
                    resolveBucket(bucketName), path, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_CLIENT_ERROR, e.getMessage()), e);
        }
    }
    
    @Override
    public String getUrl(String path) {
        return getUrl(null, path);
    }

    @Override
    public String getUrl(String bucketName, String path) {
        // 如果配置或CDN domain，优先返或CDN URL
        if (StringUtils.hasText(properties.getCdnDomain())) {
            String cdnDomain = properties.getCdnDomain();
            // 确保 CDN domain 或/ 结尾
            if (!cdnDomain.endsWith("/")) {
                cdnDomain = cdnDomain + "/";
            }
            return cdnDomain + path;
        }
        
        // 否则返回 S3 endpoint URL
        String endpoint = properties.getEndpoint();
        // 移除末尾或/
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + "/" + resolveBucket(bucketName) + "/" + path;
    }
    
    @Override
    public String getPublicUrl(String path) {
        return getPublicUrl(properties.getPublicBucket(), path);
    }

    @Override
    public String getPublicUrl(String bucketName, String path) {
        // 如果配置了CDN domain，优先返回CDN URL
        if (StringUtils.hasText(properties.getCdnDomain())) {
            String cdnDomain = properties.getCdnDomain();
            // 确保 CDN domain 以 / 结尾
            if (!cdnDomain.endsWith("/")) {
                cdnDomain = cdnDomain + "/";
            }
            return cdnDomain + path;
        }
        
        // 否则返回公开存储桶的 S3 endpoint URL
        String endpoint = properties.getPublicEndpoint();
        if (!StringUtils.hasText(endpoint)) {
            endpoint = properties.getEndpoint();
        }
        // 移除末尾的 /
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + "/" + resolveBucket(bucketName) + "/" + path;
    }
    
    @Override
    public String generatePresignedUrl(String path, Duration expiration) {
        return generatePresignedUrl(properties.getPrivateBucket(), path, expiration);
    }

    @Override
    public String generatePresignedUrl(String bucketName, String path, Duration expiration) {
        try {
            String privateBucket = resolveBucket(bucketName);
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(privateBucket)
                    .key(path)
                    .build();
            
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(getRequest)
                    .build();
            
            String presignedUrl = s3Presigner.presignGetObject(presignRequest).url().toString();
            
            log.debug("Generated presigned URL for private file: bucket={}, key={}, expiresIn={}s", 
                    privateBucket, path, expiration.getSeconds());
            
            return presignedUrl;
        } catch (S3Exception e) {
            log.error("Failed to generate presigned URL: bucket={}, key={}, error={}", 
                    properties.getPrivateBucket(), path, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_PRESIGN_GET_FAILED, e.getMessage()), e);
        } catch (SdkClientException e) {
            log.error("S3 client error during presigned URL generation: bucket={}, key={}, error={}", 
                    properties.getPrivateBucket(), path, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_CLIENT_ERROR, e.getMessage()), e);
        }
    }
    
    @Override
    public boolean exists(String path) {
        return exists(null, path);
    }

    @Override
    public boolean exists(String bucketName, String path) {
        try {
            String resolvedBucket = resolveBucket(bucketName);
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(resolvedBucket)
                    .key(path)
                    .build();

            s3Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException e) {
            // 文件不存在，返回 false，不抛异或
            return false;
        } catch (S3Exception e) {
            log.error("Failed to check file existence in S3: bucket={}, key={}, error={}",
                    resolveBucket(bucketName), path, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_EXISTS_CHECK_FAILED, e.getMessage()), e);
        } catch (SdkClientException e) {
            log.error("S3 client error during exists check: bucket={}, key={}, error={}",
                    resolveBucket(bucketName), path, e.getMessage(), e);
            throw new BusinessException("S3 客户端错误: " + e.getMessage(), e);
        }
    }

    @Override
    public ObjectMetadata getObjectMetadata(String path) {
        return getObjectMetadata(null, path);
    }

    @Override
    public ObjectMetadata getObjectMetadata(String bucketName, String path) {
        try {
            String resolvedBucket = resolveBucket(bucketName);
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(resolvedBucket)
                    .key(path)
                    .build();

            HeadObjectResponse response = s3Client.headObject(headRequest);

            log.debug("Got object metadata from S3: bucket={}, key={}, size={}, contentType={}",
                    resolvedBucket, path, response.contentLength(), response.contentType());

            return ObjectMetadata.builder()
                    .fileSize(response.contentLength() != null ? response.contentLength() : 0L)
                    .contentType(response.contentType() != null ? response.contentType() : "application/octet-stream")
                    .build();
        } catch (NoSuchKeyException e) {
            log.error("File not found in S3 when getting metadata: bucket={}, key={}",
                    resolveBucket(bucketName), path);
            throw new BusinessException("文件不存在: " + path, e);
        } catch (S3Exception e) {
            log.error("Failed to get object metadata from S3: bucket={}, key={}, error={}",
                    resolveBucket(bucketName), path, e.getMessage(), e);
            throw new BusinessException("获取文件元数据失败: " + e.getMessage(), e);
        } catch (SdkClientException e) {
            log.error("S3 client error during head object: bucket={}, key={}, error={}",
                    resolveBucket(bucketName), path, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_CLIENT_ERROR, e.getMessage()), e);
        }
    }
    
    /**
     * 构建 S3Client
     * 配置 endpoint、credentials、region，支或path-style access
     */
    private S3Client buildS3Client(S3Properties props) {
        try {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                    props.getAccessKey(), 
                    props.getSecretKey()
            );
            
            // 配置 path-style access (RustFS/MinIO 需或
            S3Configuration s3Config = S3Configuration.builder()
                    .pathStyleAccessEnabled(props.isPathStyleAccess())
                    .build();
            
            return S3Client.builder()
                    .endpointOverride(URI.create(props.getEndpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .region(Region.of(props.getRegion()))
                    .serviceConfiguration(s3Config)
                    .build();
        } catch (IllegalArgumentException e) {
            log.error("Invalid S3 configuration: endpoint={}, region={}, error={}", 
                    props.getEndpoint(), props.getRegion(), e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_CONFIG_INVALID, e.getMessage()), e);
        } catch (SdkClientException e) {
            log.error("Failed to build S3 client: endpoint={}, error={}", 
                    props.getEndpoint(), e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_CLIENT_BUILD_FAILED, e.getMessage()), e);
        }
    }
    
    /**
     * 确保 bucket 存在，不存在则创或
     */
    private void ensureBucketExists() {
        Set<String> buckets = new LinkedHashSet<>();
        buckets.add(resolveBucket(properties.getBucket()));
        buckets.add(resolveBucket(properties.getPublicBucket()));
        buckets.add(resolveBucket(properties.getPrivateBucket()));

        for (String bucketName : buckets) {
            ensureBucketExists(bucketName, bucketName.equals(resolveBucket(properties.getPublicBucket())));
        }
    }

    private void ensureBucketExists(String bucketName, boolean publicRead) {
        try {
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();

            s3Client.headBucket(headBucketRequest);
            log.info("S3 bucket exists: {}", bucketName);
            if (publicRead) {
                setBucketPublicReadPolicy(bucketName);
            }
        } catch (NoSuchBucketException e) {
            log.info("S3 bucket does not exist, creating: {}", bucketName);
            createBucket(bucketName, publicRead);
        } catch (S3Exception e) {
            log.error("Failed to check S3 bucket: {}, error={}", bucketName, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_INIT_FAILED, e.getMessage()), e);
        } catch (SdkClientException e) {
            log.error("S3 client error during bucket check: bucket={}, error={}", bucketName, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_CLIENT_ERROR, e.getMessage()), e);
        }
    }
    
    /**
     * 创建 bucket 并设置公开访问策略
     */
    private void createBucket(String bucketName, boolean publicRead) {
        try {
            CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            
            s3Client.createBucket(createBucketRequest);
            log.info("S3 bucket created successfully: {}", bucketName);
            
            if (publicRead) {
                setBucketPublicReadPolicy(bucketName);
            }
            
        } catch (S3Exception e) {
            log.error("Failed to create S3 bucket: {}, error={}", bucketName, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_BUCKET_CREATE_FAILED, e.getMessage()), e);
        } catch (SdkClientException e) {
            log.error("S3 client error during bucket creation: bucket={}, error={}", 
                    bucketName, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_CLIENT_ERROR, e.getMessage()), e);
        }
    }
    
    /**
     * 设置bucket为公开读取策略
     * 允许所有人读取bucket中的对象
     */
    private void setBucketPublicReadPolicy(String bucketName) {
        try {
            String policyJson = String.format(
                "{\n" +
                "  \"Version\": \"2012-10-17\",\n" +
                "  \"Statement\": [\n" +
                "    {\n" +
                "      \"Sid\": \"PublicReadGetObject\",\n" +
                "      \"Effect\": \"Allow\",\n" +
                "      \"Principal\": \"*\",\n" +
                "      \"Action\": \"s3:GetObject\",\n" +
                "      \"Resource\": \"arn:aws:s3:::%s/*\"\n" +
                "    }\n" +
                "  ]\n" +
                "}", 
                bucketName
            );
            
            PutBucketPolicyRequest policyRequest = PutBucketPolicyRequest.builder()
                    .bucket(bucketName)
                    .policy(policyJson)
                    .build();
            
            s3Client.putBucketPolicy(policyRequest);
            log.info("S3 bucket policy set to public read: {}", bucketName);
            
        } catch (S3Exception e) {
            log.warn("Failed to set bucket policy (may not be supported by storage service): {}", e.getMessage());
            // 不抛出异常，因为某些S3兼容服务可能不支持bucket policy
        } catch (SdkClientException e) {
            log.warn("S3 client error during bucket policy setup: {}", e.getMessage());
        }
    }

    private String resolveBucket(String bucketName) {
        return StringUtils.hasText(bucketName) ? bucketName : properties.getBucket();
    }
    
    /**
     * 创建分片上传任务
     * 
     * @param path 存储路径
     * @param contentType 文件类型
     * @return S3 multipart upload ID
     */
    public String createMultipartUpload(String path, String contentType) {
        try {
            CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                    .bucket(properties.getBucket())
                    .key(path)
                    .contentType(contentType)
                    .build();
            
            CreateMultipartUploadResponse response = s3Client.createMultipartUpload(createRequest);
            String uploadId = response.uploadId();
            
            log.debug("Multipart upload created: bucket={}, key={}, uploadId={}", 
                    properties.getBucket(), path, uploadId);
            
            return uploadId;
        } catch (S3Exception e) {
            log.error("Failed to create multipart upload: bucket={}, key={}, error={}", 
                    properties.getBucket(), path, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_MULTIPART_CREATE_FAILED, e.getMessage()), e);
        } catch (SdkClientException e) {
            log.error("S3 client error during multipart upload creation: bucket={}, key={}, error={}", 
                    properties.getBucket(), path, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_CLIENT_ERROR, e.getMessage()), e);
        }
    }
    
    /**
     * 上传单个分片
     * 
     * @param path 存储路径
     * @param uploadId S3 multipart upload ID
     * @param partNumber 分片或(或1 开或
     * @param data 分片数据
     * @return ETag (用于完成上传时验或
     */
    public String uploadPart(String path, String uploadId, int partNumber, byte[] data) {
        try {
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(properties.getBucket())
                    .key(path)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .build();
            
            UploadPartResponse response = s3Client.uploadPart(
                    uploadPartRequest, 
                    RequestBody.fromBytes(data)
            );
            
            String etag = response.eTag();
            log.debug("Part uploaded: bucket={}, key={}, uploadId={}, partNumber={}, etag={}", 
                    properties.getBucket(), path, uploadId, partNumber, etag);
            
            return etag;
        } catch (S3Exception e) {
            log.error("Failed to upload part: bucket={}, key={}, uploadId={}, partNumber={}, error={}", 
                    properties.getBucket(), path, uploadId, partNumber, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_PART_UPLOAD_FAILED, e.getMessage()), e);
        } catch (SdkClientException e) {
            log.error("S3 client error during part upload: bucket={}, key={}, uploadId={}, partNumber={}, error={}", 
                    properties.getBucket(), path, uploadId, partNumber, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_CLIENT_ERROR, e.getMessage()), e);
        }
    }
    
    /**
     * 完成分片上传，合并所有分或
     * 
     * @param path 存储路径
     * @param uploadId S3 multipart upload ID
     * @param parts 已上传的分片列表 (partNumber -> etag)
     * @return 文件访问 URL
     */
    public String completeMultipartUpload(String path, String uploadId, java.util.List<CompletedPart> parts) {
        try {
            CompletedMultipartUpload completedUpload = CompletedMultipartUpload.builder()
                    .parts(parts)
                    .build();
            
            CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                    .bucket(properties.getBucket())
                    .key(path)
                    .uploadId(uploadId)
                    .multipartUpload(completedUpload)
                    .build();
            
            s3Client.completeMultipartUpload(completeRequest);
            log.info("Multipart upload completed: bucket={}, key={}, uploadId={}, parts={}", 
                    properties.getBucket(), path, uploadId, parts.size());
            
            return getUrl(path);
        } catch (S3Exception e) {
            log.error("Failed to complete multipart upload: bucket={}, key={}, uploadId={}, error={}", 
                    properties.getBucket(), path, uploadId, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_MULTIPART_COMPLETE_FAILED, e.getMessage()), e);
        } catch (SdkClientException e) {
            log.error("S3 client error during multipart upload completion: bucket={}, key={}, uploadId={}, error={}", 
                    properties.getBucket(), path, uploadId, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_CLIENT_ERROR, e.getMessage()), e);
        }
    }
    
    /**
     * 中止分片上传,清理已上传的分片
     * 
     * @param path 存储路径
     * @param uploadId S3 multipart upload ID
     */
    public void abortMultipartUpload(String path, String uploadId) {
        try {
            AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                    .bucket(properties.getBucket())
                    .key(path)
                    .uploadId(uploadId)
                    .build();
            
            s3Client.abortMultipartUpload(abortRequest);
            log.info("Multipart upload aborted: bucket={}, key={}, uploadId={}", 
                    properties.getBucket(), path, uploadId);
        } catch (S3Exception e) {
            log.error("Failed to abort multipart upload: bucket={}, key={}, uploadId={}, error={}", 
                    properties.getBucket(), path, uploadId, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_MULTIPART_ABORT_FAILED, e.getMessage()), e);
        } catch (SdkClientException e) {
            log.error("S3 client error during multipart upload abort: bucket={}, key={}, uploadId={}, error={}", 
                    properties.getBucket(), path, uploadId, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_CLIENT_ERROR, e.getMessage()), e);
        }
    }
    
    /**
     * 列出已上传的分片
     * 用于断点续传场景，查询 S3 中已上传的分片
     * 
     * @param path 存储路径
     * @param uploadId S3 multipart upload ID
     * @return 已上传的分片编号列表
     */
    public java.util.List<Integer> listUploadedParts(String path, String uploadId) {
        try {
            ListPartsRequest listPartsRequest = ListPartsRequest.builder()
                    .bucket(properties.getBucket())
                    .key(path)
                    .uploadId(uploadId)
                    .build();
            
            ListPartsResponse response = s3Client.listParts(listPartsRequest);
            
            java.util.List<Integer> partNumbers = response.parts().stream()
                    .map(Part::partNumber)
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());
            
            log.info("Listed uploaded parts: bucket={}, key={}, uploadId={}, count={}", 
                    properties.getBucket(), path, uploadId, partNumbers.size());
            
            return partNumbers;
        } catch (S3Exception e) {
            log.error("Failed to list parts: bucket={}, key={}, uploadId={}, error={}", 
                    properties.getBucket(), path, uploadId, e.getMessage(), e);
            // 如果上传不存在，返回空列表而不是抛出异常
            if (e.statusCode() == 404) {
                log.warn("Upload not found, returning empty list: uploadId={}", uploadId);
                return java.util.Collections.emptyList();
            }
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_LIST_PARTS_FAILED, e.getMessage()), e);
        } catch (SdkClientException e) {
            log.error("S3 client error during list parts: bucket={}, key={}, uploadId={}, error={}", 
                    properties.getBucket(), path, uploadId, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_CLIENT_ERROR, e.getMessage()), e);
        }
    }
    
    /**
     * 列出已上传分片的完整信息（包括 ETag）
     * 用于断点续传场景，查询 S3 中已上传分片的完整信息
     * 
     * @param path 存储路径
     * @param uploadId S3 multipart upload ID
     * @return 已上传分片的完整信息列表（包括分片编号和 ETag）
     */
    public java.util.List<PartInfo> listUploadedPartsWithETag(String path, String uploadId) {
        try {
            ListPartsRequest listPartsRequest = ListPartsRequest.builder()
                    .bucket(properties.getBucket())
                    .key(path)
                    .uploadId(uploadId)
                    .build();
            
            ListPartsResponse response = s3Client.listParts(listPartsRequest);
            
            java.util.List<PartInfo> partInfos = response.parts().stream()
                    .map(part -> new PartInfo(part.partNumber(), part.eTag()))
                    .sorted((p1, p2) -> Integer.compare(p1.getPartNumber(), p2.getPartNumber()))
                    .collect(java.util.stream.Collectors.toList());
            
            log.info("Listed uploaded parts with ETag: bucket={}, key={}, uploadId={}, count={}", 
                    properties.getBucket(), path, uploadId, partInfos.size());
            
            return partInfos;
        } catch (S3Exception e) {
            log.error("Failed to list parts with ETag: bucket={}, key={}, uploadId={}, error={}", 
                    properties.getBucket(), path, uploadId, e.getMessage(), e);
            // 如果上传不存在，返回空列表而不是抛出异常
            if (e.statusCode() == 404) {
                log.warn("Upload not found, returning empty list: uploadId={}", uploadId);
                return java.util.Collections.emptyList();
            }
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_LIST_PARTS_FAILED, e.getMessage()), e);
        } catch (SdkClientException e) {
            log.error("S3 client error during list parts with ETag: bucket={}, key={}, uploadId={}, error={}", 
                    properties.getBucket(), path, uploadId, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_CLIENT_ERROR, e.getMessage()), e);
        }
    }
    
    /**
     * 分片信息（包括编号和 ETag）
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class PartInfo {
        private Integer partNumber;
        private String etag;
    }
    
    /**
     * 构建 S3Presigner
     * 配置 endpoint、credentials、region，用于生成预签名 URL
     * 使用 publicEndpoint (如果配置) 以便浏览器可以访问
     */
    private S3Presigner buildS3Presigner(S3Properties props) {
        try {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                    props.getAccessKey(), 
                    props.getSecretKey()
            );
            
            // 配置 path-style access (RustFS/MinIO 需或
            S3Configuration s3Config = S3Configuration.builder()
                    .pathStyleAccessEnabled(props.isPathStyleAccess())
                    .build();
            
            // 使用 publicEndpoint 用于生成浏览器可访问的预签名 URL
            String presignEndpoint = props.getPresignEndpoint();
            log.info("Building S3Presigner with endpoint: {} (public: {})", 
                    presignEndpoint, props.getPublicEndpoint() != null);
            
            return S3Presigner.builder()
                    .endpointOverride(URI.create(presignEndpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .region(Region.of(props.getRegion()))
                    .serviceConfiguration(s3Config)
                    .build();
        } catch (IllegalArgumentException e) {
            log.error("Invalid S3 presigner configuration: endpoint={}, region={}, error={}", 
                    props.getPresignEndpoint(), props.getRegion(), e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_PRESIGNER_CONFIG_INVALID, e.getMessage()), e);
        } catch (SdkClientException e) {
            log.error("Failed to build S3 presigner: endpoint={}, error={}", 
                    props.getPresignEndpoint(), e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_PRESIGNER_BUILD_FAILED, e.getMessage()), e);
        }
    }
    
    /**
     * 生成预签名上传URL
     * 允许客户端直接上传文件到 S3，无需通过服务器中或
     * 
     * @param path 存储路径
     * @param contentType 文件类型
     * @param expireSeconds URL 过期时间（秒传
     * @return 预签名上传URL
     */
    public String generatePresignedPutUrl(String path, String contentType, int expireSeconds) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(path)
                    .contentType(contentType)
                    .build();
            
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expireSeconds))
                    .putObjectRequest(putRequest)
                    .build();
            
            String presignedUrl = s3Presigner.presignPutObject(presignRequest).url().toString();
            
            log.debug("Generated presigned PUT URL: bucket={}, key={}, expiresIn={}s", 
                    properties.getBucket(), path, expireSeconds);
            
            return presignedUrl;
        } catch (S3Exception e) {
            log.error("Failed to generate presigned PUT URL: bucket={}, key={}, error={}", 
                    properties.getBucket(), path, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_PRESIGN_PUT_FAILED, e.getMessage()), e);
        } catch (SdkClientException e) {
            log.error("S3 client error during presigned PUT URL generation: bucket={}, key={}, error={}", 
                    properties.getBucket(), path, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_CLIENT_ERROR, e.getMessage()), e);
        }
    }
    
    /**
     * 生成预签名下或URL
     * 用于私有文件的临时访或
     * 
     * @param path 存储路径
     * @param expireSeconds URL 过期时间（秒传
     * @return 预签名下或URL
     */
    public String generatePresignedGetUrl(String path, int expireSeconds) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(path)
                    .build();
            
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expireSeconds))
                    .getObjectRequest(getRequest)
                    .build();
            
            String presignedUrl = s3Presigner.presignGetObject(presignRequest).url().toString();
            
            log.debug("Generated presigned GET URL: bucket={}, key={}, expiresIn={}s", 
                    properties.getBucket(), path, expireSeconds);
            
            return presignedUrl;
        } catch (S3Exception e) {
            log.error("Failed to generate presigned GET URL: bucket={}, key={}, error={}", 
                    properties.getBucket(), path, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_PRESIGN_DOWNLOAD_FAILED, e.getMessage()), e);
        } catch (SdkClientException e) {
            log.error("S3 client error during presigned GET URL generation: bucket={}, key={}, error={}", 
                    properties.getBucket(), path, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_CLIENT_ERROR, e.getMessage()), e);
        }
    }
    
    /**
     * 生成预签名分片上传 URL
     * 用于客户端直接上传分片到 S3
     * 
     * @param path 存储路径
     * @param uploadId S3 multipart upload ID
     * @param partNumber 分片号（从 1 开始）
     * @param expireSeconds URL 过期时间（秒）
     * @return 预签名上传 URL
     */
    public String generatePresignedUploadPartUrl(String path, String uploadId, 
                                                 int partNumber, int expireSeconds) {
        try {
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(properties.getBucket())
                    .key(path)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .build();
            
            software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest presignRequest = 
                    software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expireSeconds))
                    .uploadPartRequest(uploadPartRequest)
                    .build();
            
            String presignedUrl = s3Presigner.presignUploadPart(presignRequest).url().toString();
            
            log.debug("Generated presigned upload part URL: bucket={}, key={}, uploadId={}, partNumber={}, expiresIn={}s", 
                    properties.getBucket(), path, uploadId, partNumber, expireSeconds);
            
            return presignedUrl;
        } catch (S3Exception e) {
            log.error("Failed to generate presigned upload part URL: bucket={}, key={}, uploadId={}, partNumber={}, error={}", 
                    properties.getBucket(), path, uploadId, partNumber, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_PRESIGN_PART_FAILED, e.getMessage()), e);
        } catch (SdkClientException e) {
            log.error("S3 client error during presigned upload part URL generation: bucket={}, key={}, uploadId={}, partNumber={}, error={}", 
                    properties.getBucket(), path, uploadId, partNumber, e.getMessage(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.S3_CLIENT_ERROR, e.getMessage()), e);
        }
    }
}
