package com.architectcgz.file.integration;

import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MinIO 连接测试
 * 
 * 测试范围：
 * - 使用 Testcontainers 启动 MinIO
 * - 测试基本连接
 * - 测试存储桶列表
 * 
 * 验证需求：Requirements 4.1
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("MinIO 连接测试")
class MinIOConnectionTest {
    
    private static final String MINIO_IMAGE = "minio/minio:RELEASE.2025-04-22T22-12-26Z";
    private static final String ACCESS_KEY = "fileservice";
    private static final String SECRET_KEY = "fileservice123";
    private static final String REGION = "us-east-1";
    
    @Container
    static GenericContainer<?> minio = new GenericContainer<>(DockerImageName.parse(MINIO_IMAGE))
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data", "--console-address", ":9001")
            .withExposedPorts(9000, 9001)
            .waitingFor(new HttpWaitStrategy()
                    .forPath("/minio/health/live")
                    .forPort(9000)
                    .withStartupTimeout(Duration.ofMinutes(2)));
    
    private static S3Client s3Client;
    
    @BeforeAll
    static void setUpS3Client() {
        // 创建 S3 客户端
        String endpoint = String.format("http://%s:%d", minio.getHost(), minio.getMappedPort(9000));
        
        AwsBasicCredentials credentials = AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY);
        
        s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(REGION))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
    
    @AfterAll
    static void tearDownS3Client() {
        if (s3Client != null) {
            s3Client.close();
        }
    }
    
    // ========== 基本连接测试 ==========
    
    @Test
    @Order(1)
    @DisplayName("测试 MinIO 容器启动成功")
    void testMinIOContainerStarted() {
        assertTrue(minio.isRunning(), "MinIO 容器应该正在运行");
        assertNotNull(minio.getHost(), "MinIO 主机地址应该不为空");
        assertNotNull(minio.getMappedPort(9000), "MinIO API 端口应该已映射");
        assertNotNull(minio.getMappedPort(9001), "MinIO 控制台端口应该已映射");
    }
    
    @Test
    @Order(2)
    @DisplayName("测试 S3 客户端连接 MinIO")
    void testS3ClientConnection() {
        assertNotNull(s3Client, "S3 客户端应该已创建");
        
        // 测试连接 - 列出存储桶（即使为空也应该成功）
        assertDoesNotThrow(() -> {
            ListBucketsResponse response = s3Client.listBuckets();
            assertNotNull(response, "列出存储桶响应应该不为空");
            assertNotNull(response.buckets(), "存储桶列表应该不为空（可以是空列表）");
        }, "应该能够成功连接到 MinIO 并列出存储桶");
    }
    
    // ========== 存储桶操作测试 ==========
    
    @Test
    @Order(3)
    @DisplayName("测试创建存储桶")
    void testCreateBucket() {
        String bucketName = "test-bucket";
        
        // 创建存储桶
        assertDoesNotThrow(() -> {
            CreateBucketRequest request = CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.createBucket(request);
        }, "应该能够成功创建存储桶");
        
        // 验证存储桶已创建
        ListBucketsResponse response = s3Client.listBuckets();
        List<String> bucketNames = response.buckets().stream()
                .map(Bucket::name)
                .collect(Collectors.toList());
        
        assertTrue(bucketNames.contains(bucketName), 
                "存储桶列表应该包含新创建的存储桶");
    }
    
    @Test
    @Order(4)
    @DisplayName("测试列出存储桶")
    void testListBuckets() {
        // 列出所有存储桶
        ListBucketsResponse response = s3Client.listBuckets();
        
        assertNotNull(response, "响应应该不为空");
        assertNotNull(response.buckets(), "存储桶列表应该不为空");
        assertFalse(response.buckets().isEmpty(), "应该至少有一个存储桶（test-bucket）");
        
        // 验证存储桶信息
        for (Bucket bucket : response.buckets()) {
            assertNotNull(bucket.name(), "存储桶名称应该不为空");
            assertNotNull(bucket.creationDate(), "存储桶创建时间应该不为空");
        }
    }
    
    @Test
    @Order(5)
    @DisplayName("测试检查存储桶是否存在")
    void testBucketExists() {
        String existingBucket = "test-bucket";
        String nonExistingBucket = "non-existing-bucket";
        
        // 测试存在的存储桶
        assertDoesNotThrow(() -> {
            HeadBucketRequest request = HeadBucketRequest.builder()
                    .bucket(existingBucket)
                    .build();
            s3Client.headBucket(request);
        }, "检查存在的存储桶应该成功");
        
        // 测试不存在的存储桶
        assertThrows(NoSuchBucketException.class, () -> {
            HeadBucketRequest request = HeadBucketRequest.builder()
                    .bucket(nonExistingBucket)
                    .build();
            s3Client.headBucket(request);
        }, "检查不存在的存储桶应该抛出 NoSuchBucketException");
    }
    
    // ========== 对象操作测试 ==========
    
    @Test
    @Order(6)
    @DisplayName("测试上传和下载对象")
    void testPutAndGetObject() {
        String bucketName = "test-bucket";
        String objectKey = "test-file.txt";
        String content = "Hello MinIO!";
        
        // 上传对象
        assertDoesNotThrow(() -> {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType("text/plain")
                    .build();
            
            s3Client.putObject(putRequest, RequestBody.fromString(content));
        }, "应该能够成功上传对象");
        
        // 下载对象
        assertDoesNotThrow(() -> {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();
            
            String downloadedContent = s3Client.getObjectAsBytes(getRequest).asUtf8String();
            assertEquals(content, downloadedContent, "下载的内容应该与上传的内容一致");
        }, "应该能够成功下载对象");
    }
    
    @Test
    @Order(7)
    @DisplayName("测试列出对象")
    void testListObjects() {
        String bucketName = "test-bucket";
        
        // 列出对象
        ListObjectsV2Response response = s3Client.listObjectsV2(
                ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .build()
        );
        
        assertNotNull(response, "响应应该不为空");
        assertNotNull(response.contents(), "对象列表应该不为空");
        assertFalse(response.contents().isEmpty(), "应该至少有一个对象（test-file.txt）");
        
        // 验证对象信息
        boolean foundTestFile = false;
        for (S3Object object : response.contents()) {
            assertNotNull(object.key(), "对象键应该不为空");
            assertNotNull(object.size(), "对象大小应该不为空");
            assertNotNull(object.lastModified(), "对象最后修改时间应该不为空");
            
            if ("test-file.txt".equals(object.key())) {
                foundTestFile = true;
            }
        }
        
        assertTrue(foundTestFile, "应该找到上传的测试文件");
    }
    
    @Test
    @Order(8)
    @DisplayName("测试删除对象")
    void testDeleteObject() {
        String bucketName = "test-bucket";
        String objectKey = "test-file.txt";
        
        // 删除对象
        assertDoesNotThrow(() -> {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();
            
            s3Client.deleteObject(deleteRequest);
        }, "应该能够成功删除对象");
        
        // 验证对象已删除
        assertThrows(NoSuchKeyException.class, () -> {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();
            
            s3Client.getObjectAsBytes(getRequest);
        }, "获取已删除的对象应该抛出 NoSuchKeyException");
    }
    
    // ========== 配置验证测试 ==========
    
    @Test
    @Order(9)
    @DisplayName("测试 MinIO 配置正确性")
    void testMinIOConfiguration() {
        // 验证 MinIO 容器配置
        assertEquals(MINIO_IMAGE, minio.getDockerImageName(), 
                "MinIO 镜像版本应该正确");
        
        // 验证端口映射
        assertNotNull(minio.getMappedPort(9000), "API 端口应该已映射");
        assertNotNull(minio.getMappedPort(9001), "控制台端口应该已映射");
        
        // 验证环境变量
        assertTrue(minio.getEnvMap().containsKey("MINIO_ROOT_USER"), 
                "应该配置 MINIO_ROOT_USER");
        assertTrue(minio.getEnvMap().containsKey("MINIO_ROOT_PASSWORD"), 
                "应该配置 MINIO_ROOT_PASSWORD");
        
        assertEquals(ACCESS_KEY, minio.getEnvMap().get("MINIO_ROOT_USER"), 
                "访问密钥应该正确");
        assertEquals(SECRET_KEY, minio.getEnvMap().get("MINIO_ROOT_PASSWORD"), 
                "密钥应该正确");
    }
    
    @Test
    @Order(10)
    @DisplayName("测试 S3 客户端配置正确性")
    void testS3ClientConfiguration() {
        assertNotNull(s3Client, "S3 客户端应该已创建");
        
        // 测试客户端可以正常工作
        assertDoesNotThrow(() -> {
            ListBucketsResponse response = s3Client.listBuckets();
            assertNotNull(response, "应该能够成功调用 S3 API");
        }, "S3 客户端应该配置正确并能够正常工作");
    }
    
    // ========== 清理测试 ==========
    
    @Test
    @Order(11)
    @DisplayName("清理测试数据")
    void cleanupTestData() {
        String bucketName = "test-bucket";
        
        // 删除存储桶中的所有对象
        ListObjectsV2Response response = s3Client.listObjectsV2(
                ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .build()
        );
        
        for (S3Object object : response.contents()) {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(object.key())
                    .build());
        }
        
        // 删除存储桶
        assertDoesNotThrow(() -> {
            DeleteBucketRequest deleteRequest = DeleteBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            
            s3Client.deleteBucket(deleteRequest);
        }, "应该能够成功删除存储桶");
        
        // 验证存储桶已删除
        ListBucketsResponse bucketsResponse = s3Client.listBuckets();
        List<String> bucketNames = bucketsResponse.buckets().stream()
                .map(Bucket::name)
                .collect(Collectors.toList());
        
        assertFalse(bucketNames.contains(bucketName), 
                "存储桶列表不应该包含已删除的存储桶");
    }
}
