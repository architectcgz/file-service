package com.architectcgz.file.infrastructure.storage;

import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.infrastructure.config.S3Properties;
import net.jqwik.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * S3StorageService 属性测试
 * 
 * Feature: file-service-optimization
 * 使用基于属性的测试验证文件存储服务的正确性属性
 */
class S3StorageServicePropertyTest {

    /**
     * Feature: file-service-optimization, Property 1: 文件存储桶选择
     * 
     * 属性：对于任何文件上传操作，当访问级别为 PUBLIC 时，文件应该被存储到 Public_Bucket；
     * 当访问级别为 PRIVATE 时，文件应该被存储到 Private_Bucket。
     * 
     * 验证需求：1.2
     */
    @Property(tries = 100)
    @Label("Property 1: 文件存储桶选择 - 根据访问级别选择正确的存储桶")
    void fileStorageBucketSelection(
            @ForAll("accessLevels") AccessLevel accessLevel,
            @ForAll("filePaths") String path,
            @ForAll("fileData") byte[] data,
            @ForAll("contentTypes") String contentType
    ) {
        // Given: 配置双桶的 S3 存储服务
        S3Properties properties = new S3Properties();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("test-key");
        properties.setSecretKey("test-secret");
        properties.setBucket("default-bucket");
        properties.setPublicBucket("public-bucket");
        properties.setPrivateBucket("private-bucket");
        properties.setRegion("us-east-1");
        properties.setPathStyleAccess(true);
        
        // 创建 mock S3Client
        S3Client mockS3Client = mock(S3Client.class);
        PutObjectResponse putResponse = PutObjectResponse.builder().build();
        when(mockS3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(putResponse);
        
        // 创建 S3StorageService 并注入 mock S3Client
        S3StorageService storageService = new S3StorageService(properties);
        ReflectionTestUtils.setField(storageService, "s3Client", mockS3Client);
        
        // When: 根据访问级别上传文件
        String result = storageService.uploadByAccessLevel(data, path, contentType, accessLevel);
        
        // Then: 验证文件被上传到正确的存储桶
        String expectedBucket = accessLevel == AccessLevel.PUBLIC 
                ? properties.getPublicBucket() 
                : properties.getPrivateBucket();
        
        // 验证 S3Client.putObject 被调用，并且使用了正确的 bucket
        verify(mockS3Client).putObject(
                argThat((PutObjectRequest request) -> {
                    boolean correctBucket = expectedBucket.equals(request.bucket());
                    boolean correctKey = path.equals(request.key());
                    boolean correctContentType = contentType.equals(request.contentType());
                    
                    return correctBucket && correctKey && correctContentType;
                }),
                any(RequestBody.class)
        );
        
        // 验证返回值不为空
        assertNotNull(result, "Upload should return a non-null result");
        
        // 对于公开文件，验证返回的是公开 URL
        if (accessLevel == AccessLevel.PUBLIC) {
            assertTrue(result.contains(path), 
                    "Public file URL should contain the file path");
        }
    }
    
    // ========== Arbitraries (数据生成器) ==========
    
    /**
     * 生成访问级别
     */
    @Provide
    Arbitrary<AccessLevel> accessLevels() {
        return Arbitraries.of(AccessLevel.PUBLIC, AccessLevel.PRIVATE);
    }
    
    /**
     * 生成文件路径
     * 格式: {tenantId}/{year}/{month}/{day}/{userId}/{type}/{fileId}.{ext}
     */
    @Provide
    Arbitrary<String> filePaths() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10), // tenantId
                Arbitraries.integers().between(2020, 2030), // year
                Arbitraries.integers().between(1, 12), // month
                Arbitraries.integers().between(1, 28), // day
                Arbitraries.strings().alpha().numeric().ofMinLength(5).ofMaxLength(15), // userId
                Arbitraries.of("images", "files", "videos", "audios"), // type
                Arbitraries.strings().alpha().numeric().ofMinLength(8).ofMaxLength(16), // fileId
                Arbitraries.of("jpg", "png", "pdf", "txt", "mp4", "mp3") // extension
        ).as((tenantId, year, month, day, userId, type, fileId, ext) ->
                String.format("%s/%04d/%02d/%02d/%s/%s/%s.%s",
                        tenantId, year, month, day, userId, type, fileId, ext)
        );
    }
    
    /**
     * 生成文件数据
     * 生成 1KB 到 100KB 的随机数据
     */
    @Provide
    Arbitrary<byte[]> fileData() {
        return Arbitraries.integers().between(1024, 102400) // 1KB to 100KB
                .flatMap(size -> Arbitraries.bytes().array(byte[].class).ofSize(size));
    }
    
    /**
     * 生成内容类型
     */
    @Provide
    Arbitrary<String> contentTypes() {
        return Arbitraries.of(
                "image/jpeg",
                "image/png",
                "image/gif",
                "image/webp",
                "application/pdf",
                "text/plain",
                "video/mp4",
                "audio/mpeg",
                "application/octet-stream"
        );
    }
}
