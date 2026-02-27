package com.architectcgz.file.integration.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * S3 验证工具类
 * 用于直接验证文件在 S3/RustFS 中的存储状态
 */
public class S3Verifier {
    
    private static final Logger log = LoggerFactory.getLogger(S3Verifier.class);
    
    private final S3Client s3Client;
    private final String bucket;
    
    public S3Verifier(S3Client s3Client, String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }
    
    /**
     * 检查文件是否存在
     * 
     * @param path 文件路径（S3 key）
     * @return true 如果文件存在，否则 false
     */
    public boolean fileExists(String path) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(path)
                .build();
            
            s3Client.headObject(request);
            log.debug("File exists in S3: bucket={}, key={}", bucket, path);
            return true;
        } catch (NoSuchKeyException e) {
            log.debug("File does not exist in S3: bucket={}, key={}", bucket, path);
            return false;
        } catch (SdkException e) {
            log.error("Error checking file existence: bucket={}, key={}", bucket, path, e);
            throw new RuntimeException("Failed to check file existence", e);
        }
    }
    
    /**
     * 获取文件内容
     * 
     * @param path 文件路径（S3 key）
     * @return 文件内容字节数组
     */
    public byte[] getFileContent(String path) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(path)
                .build();
            
            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = response.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            
            byte[] content = baos.toByteArray();
            log.debug("Retrieved file content from S3: bucket={}, key={}, size={} bytes", 
                     bucket, path, content.length);
            
            return content;
        } catch (NoSuchKeyException e) {
            log.error("File not found in S3: bucket={}, key={}", bucket, path);
            throw new RuntimeException("File not found: " + path, e);
        } catch (IOException e) {
            log.error("Error reading file content: bucket={}, key={}", bucket, path, e);
            throw new RuntimeException("Failed to read file content", e);
        } catch (SdkException e) {
            log.error("S3 error getting file content: bucket={}, key={}", bucket, path, e);
            throw new RuntimeException("Failed to get file content from S3", e);
        }
    }
    
    /**
     * 获取文件大小
     * 
     * @param path 文件路径（S3 key）
     * @return 文件大小（字节）
     */
    public long getFileSize(String path) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(path)
                .build();
            
            HeadObjectResponse response = s3Client.headObject(request);
            long size = response.contentLength();
            
            log.debug("File size in S3: bucket={}, key={}, size={} bytes", bucket, path, size);
            return size;
        } catch (NoSuchKeyException e) {
            log.error("File not found in S3: bucket={}, key={}", bucket, path);
            throw new RuntimeException("File not found: " + path, e);
        } catch (SdkException e) {
            log.error("Error getting file size: bucket={}, key={}", bucket, path, e);
            throw new RuntimeException("Failed to get file size", e);
        }
    }
    
    /**
     * 获取文件的 Content-Type
     * 
     * @param path 文件路径（S3 key）
     * @return Content-Type
     */
    public String getContentType(String path) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(path)
                .build();
            
            HeadObjectResponse response = s3Client.headObject(request);
            String contentType = response.contentType();
            
            log.debug("File content type in S3: bucket={}, key={}, contentType={}", 
                     bucket, path, contentType);
            return contentType;
        } catch (NoSuchKeyException e) {
            log.error("File not found in S3: bucket={}, key={}", bucket, path);
            throw new RuntimeException("File not found: " + path, e);
        } catch (SdkException e) {
            log.error("Error getting content type: bucket={}, key={}", bucket, path, e);
            throw new RuntimeException("Failed to get content type", e);
        }
    }
    
    /**
     * 获取文件的元数据
     * 
     * @param path 文件路径（S3 key）
     * @return HeadObjectResponse 包含所有元数据
     */
    public HeadObjectResponse getFileMetadata(String path) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(path)
                .build();
            
            HeadObjectResponse response = s3Client.headObject(request);
            log.debug("Retrieved file metadata from S3: bucket={}, key={}", bucket, path);
            
            return response;
        } catch (NoSuchKeyException e) {
            log.error("File not found in S3: bucket={}, key={}", bucket, path);
            throw new RuntimeException("File not found: " + path, e);
        } catch (SdkException e) {
            log.error("Error getting file metadata: bucket={}, key={}", bucket, path, e);
            throw new RuntimeException("Failed to get file metadata", e);
        }
    }
    
    /**
     * 删除文件（用于测试清理）
     * 
     * @param path 文件路径（S3 key）
     */
    public void deleteFile(String path) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(path)
                .build();
            
            s3Client.deleteObject(request);
            log.debug("Deleted file from S3: bucket={}, key={}", bucket, path);
        } catch (SdkException e) {
            log.warn("Error deleting file from S3: bucket={}, key={}", bucket, path, e);
            // Don't throw exception for cleanup operations
        }
    }
    
    /**
     * 列出指定前缀的所有文件
     * 
     * @param prefix 路径前缀
     * @return 文件列表
     */
    public ListObjectsV2Response listFiles(String prefix) {
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .build();
            
            ListObjectsV2Response response = s3Client.listObjectsV2(request);
            log.debug("Listed files in S3: bucket={}, prefix={}, count={}", 
                     bucket, prefix, response.contents().size());
            
            return response;
        } catch (SdkException e) {
            log.error("Error listing files: bucket={}, prefix={}", bucket, prefix, e);
            throw new RuntimeException("Failed to list files", e);
        }
    }
}
