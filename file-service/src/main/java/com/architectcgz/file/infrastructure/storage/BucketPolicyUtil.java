package com.architectcgz.file.infrastructure.storage;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * S3 存储桶策略工具类
 * 用于生成和应用存储桶访问策略
 */
@Slf4j
public class BucketPolicyUtil {
    
    /**
     * 生成公开存储桶的访问策略
     * 允许所有人读取存储桶中的对象
     * 
     * @param bucketName 存储桶名称
     * @return JSON 格式的存储桶策略
     */
    public static String generatePublicBucketPolicy(String bucketName) {
        return String.format(
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
    }
    
    /**
     * 应用存储桶策略到指定存储桶
     * 
     * @param s3Client S3 客户端
     * @param bucketName 存储桶名称
     * @param policy JSON 格式的存储桶策略
     * @return 是否成功应用策略
     */
    public static boolean applyBucketPolicy(S3Client s3Client, String bucketName, String policy) {
        try {
            PutBucketPolicyRequest policyRequest = PutBucketPolicyRequest.builder()
                    .bucket(bucketName)
                    .policy(policy)
                    .build();
            
            s3Client.putBucketPolicy(policyRequest);
            log.info("Successfully applied bucket policy to: {}", bucketName);
            return true;
        } catch (S3Exception e) {
            log.warn("Failed to apply bucket policy to {}: {} (may not be supported by storage service)", 
                    bucketName, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error while applying bucket policy to {}: {}", 
                    bucketName, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 为公开存储桶应用公开读取策略
     * 
     * @param s3Client S3 客户端
     * @param bucketName 存储桶名称
     * @return 是否成功应用策略
     */
    public static boolean applyPublicReadPolicy(S3Client s3Client, String bucketName) {
        String policy = generatePublicBucketPolicy(bucketName);
        return applyBucketPolicy(s3Client, bucketName, policy);
    }
}
