package com.architectcgz.file.infrastructure.storage;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.infrastructure.config.S3Properties;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * S3StorageService 单元测试
 * 
 * 测试 S3 兼容存储服务功能
 */
@DisplayName("S3StorageService 测试")
@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

    @Mock
    private S3Client s3Client;

    private S3Properties properties;
    private S3StorageService storageService;

    @BeforeEach
    void setUp() {
        properties = new S3Properties();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("admin");
        properties.setSecretKey("admin123456");
        properties.setBucket("test-bucket");
        properties.setRegion("us-east-1");
        properties.setPathStyleAccess(true);
        
        // 创建 S3StorageService 并注入mock 的S3Client
        storageService = createStorageServiceWithMockedClient();
    }

    /**
     * 创建带有 mock S3Client 的S3StorageService
     */
    private S3StorageService createStorageServiceWithMockedClient() {
        // 使用反射创建实例并注入mock
        S3StorageService service = new S3StorageService(properties) {
            // 重写构造函数中的S3Client 创建逻辑
        };
        ReflectionTestUtils.setField(service, "s3Client", s3Client);
        return service;
    }

    @Nested
    @DisplayName("上传文件")
    class UploadFile {

        @Test
        @DisplayName("应该成功上传文件")
        void shouldUploadFileSuccessfully() {
            // Given
            byte[] data = "test content".getBytes();
            String path = "test/file.txt";
            
            PutObjectResponse putResponse = PutObjectResponse.builder().build();
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(putResponse);

            // When
            String url = storageService.upload(data, path);

            // Then
            assertNotNull(url);
            assertTrue(url.contains(properties.getEndpoint()));
            assertTrue(url.contains(properties.getBucket()));
            assertTrue(url.contains(path));
            
            // 验证 S3Client.putObject 被调用
            verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @Test
        @DisplayName("应该使用默认内容类型上传")
        void shouldUploadWithDefaultContentType() {
            // Given
            byte[] data = "test content".getBytes();
            String path = "test/file.bin";
            
            PutObjectResponse putResponse = PutObjectResponse.builder().build();
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(putResponse);

            // When
            String url = storageService.upload(data, path);

            // Then
            assertNotNull(url);
            verify(s3Client).putObject(argThat((PutObjectRequest request) -> 
                    "application/octet-stream".equals(request.contentType())), 
                    any(RequestBody.class));
        }

        @Test
        @DisplayName("应该支持指定内容类型上传")
        void shouldUploadWithSpecifiedContentType() {
            // Given
            byte[] data = "test content".getBytes();
            String path = "test/file.txt";
            String contentType = "text/plain";
            
            PutObjectResponse putResponse = PutObjectResponse.builder().build();
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(putResponse);

            // When
            String url = storageService.upload(data, path, contentType);

            // Then
            assertNotNull(url);
            verify(s3Client).putObject(argThat((PutObjectRequest request) -> 
                    contentType.equals(request.contentType()) &&
                    properties.getBucket().equals(request.bucket()) &&
                    path.equals(request.key())), 
                    any(RequestBody.class));
        }

        @Test
        @DisplayName("S3异常时应该抛出BusinessException")
        void shouldThrowBusinessExceptionWhenS3ExceptionOccurs() {
            // Given
            byte[] data = "test content".getBytes();
            String path = "test/file.txt";
            
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenThrow(S3Exception.builder()
                            .message("Upload failed")
                            .build());

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, 
                    () -> storageService.upload(data, path));
            assertTrue(exception.getMessage().contains("文件上传失败"));
            assertEquals(FileServiceErrorCodes.FILE_UPLOAD_FAILED, exception.getCode());
        }

        @Test
        @DisplayName("SDK客户端异常时应该抛出 BusinessException")
        void shouldThrowBusinessExceptionWhenSdkClientExceptionOccurs() {
            // Given
            byte[] data = "test content".getBytes();
            String path = "test/file.txt";
            
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenThrow(SdkClientException.builder()
                            .message("Network connection failed")
                            .build());

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, 
                    () -> storageService.upload(data, path));
            assertTrue(exception.getMessage().contains("S3 客户端错误"));
            assertEquals(FileServiceErrorCodes.S3_CLIENT_ERROR, exception.getCode());
        }

        @Test
        @DisplayName("上传图片文件应该成功")
        void shouldUploadImageFileSuccessfully() {
            // Given
            byte[] imageData = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}; // JPEG magic bytes
            String path = "images/2026/01/16/test.jpg";
            String contentType = "image/jpeg";
            
            PutObjectResponse putResponse = PutObjectResponse.builder().build();
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(putResponse);

            // When
            String url = storageService.upload(imageData, path, contentType);

            // Then
            assertNotNull(url);
            assertTrue(url.contains(path));
            verify(s3Client).putObject(argThat((PutObjectRequest request) -> 
                    contentType.equals(request.contentType())), 
                    any(RequestBody.class));
        }
    }

    @Nested
    @DisplayName("删除文件")
    class DeleteFile {

        @Test
        @DisplayName("应该成功删除文件")
        void shouldDeleteFileSuccessfully() {
            // Given
            String path = "test/file.txt";
            
            DeleteObjectResponse deleteResponse = DeleteObjectResponse.builder().build();
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenReturn(deleteResponse);

            // When
            storageService.delete(path);

            // Then
            verify(s3Client).deleteObject(argThat((DeleteObjectRequest request) -> 
                    properties.getBucket().equals(request.bucket()) &&
                    path.equals(request.key())));
        }

        @Test
        @DisplayName("删除图片文件应该成功")
        void shouldDeleteImageFileSuccessfully() {
            // Given
            String path = "images/2026/01/16/test.jpg";
            
            DeleteObjectResponse deleteResponse = DeleteObjectResponse.builder().build();
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenReturn(deleteResponse);

            // When
            storageService.delete(path);

            // Then
            verify(s3Client).deleteObject(argThat((DeleteObjectRequest request) -> 
                    properties.getBucket().equals(request.bucket()) &&
                    path.equals(request.key())));
        }

        @Test
        @DisplayName("S3异常时应该抛出BusinessException")
        void shouldThrowBusinessExceptionWhenS3ExceptionOccurs() {
            // Given
            String path = "test/file.txt";
            
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenThrow(S3Exception.builder()
                            .message("Delete failed")
                            .build());

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, 
                    () -> storageService.delete(path));
            assertTrue(exception.getMessage().contains("文件删除失败"));
        }

        @Test
        @DisplayName("SDK客户端异常时应该抛出 BusinessException")
        void shouldThrowBusinessExceptionWhenSdkClientExceptionOccurs() {
            // Given
            String path = "test/file.txt";
            
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenThrow(SdkClientException.builder()
                            .message("Network connection failed")
                            .build());

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, 
                    () -> storageService.delete(path));
            assertTrue(exception.getMessage().contains("S3 客户端错误"));
        }
    }

    @Nested
    @DisplayName("获取文件 URL")
    class GetUrl {

        @Test
        @DisplayName("应该返回正确的S3 endpoint URL 格式")
        void shouldReturnCorrectS3EndpointUrlFormat() {
            // Given
            String path = "test/file.txt";
            // 确保没有配置 CDN domain
            properties.setCdnDomain(null);

            // When
            String url = storageService.getUrl(path);

            // Then
            assertNotNull(url);
            // URL 格式应该是 {endpoint}/{bucket}/{path}
            String expectedUrl = properties.getEndpoint() + "/" + properties.getBucket() + "/" + path;
            assertEquals(expectedUrl, url);
        }

        @Test
        @DisplayName("应该正确处理 endpoint 末尾有斜杠的情况")
        void shouldHandleEndpointWithTrailingSlash() {
            // Given
            String path = "images/2026/01/16/test.jpg";
            properties.setEndpoint("http://localhost:9000/");
            properties.setCdnDomain(null);

            // When
            String url = storageService.getUrl(path);

            // Then
            assertNotNull(url);
            // 应该移除末尾斜杠，避免双斜杠
            String expectedUrl = "http://localhost:9000/" + properties.getBucket() + "/" + path;
            assertEquals(expectedUrl, url);
            // 确保没有双斜杠
            assertFalse(url.contains("//localhost:9000//"));
        }

        @Test
        @DisplayName("应该正确处理复杂路径")
        void shouldHandleComplexPath() {
            // Given
            String path = "uploads/2026/01/16/user123/image-abc123.webp";
            properties.setCdnDomain(null);

            // When
            String url = storageService.getUrl(path);

            // Then
            assertNotNull(url);
            assertTrue(url.contains(path));
            assertTrue(url.contains(properties.getBucket()));
            // URL 应该以endpoint 开头
            assertTrue(url.startsWith(properties.getEndpoint()));
        }

        @Test
        @DisplayName("应该正确处理空路径")
        void shouldHandleEmptyPath() {
            // Given
            String path = "";
            properties.setCdnDomain(null);

            // When
            String url = storageService.getUrl(path);

            // Then
            assertNotNull(url);
            String expectedUrl = properties.getEndpoint() + "/" + properties.getBucket() + "/";
            assertEquals(expectedUrl, url);
        }

        @Test
        @DisplayName("应该正确处理带特殊字符的路径")
        void shouldHandlePathWithSpecialCharacters() {
            // Given
            String path = "test/文件名txt";
            properties.setCdnDomain(null);

            // When
            String url = storageService.getUrl(path);

            // Then
            assertNotNull(url);
            assertTrue(url.contains(path));
        }

        @Test
        @DisplayName("配置 CDN domain 时应该返回CDN URL")
        void shouldReturnCdnUrlWhenCdnDomainConfigured() {
            // Given
            String path = "images/2026/01/16/test.jpg";
            String cdnDomain = "https://cdn.example.com";
            properties.setCdnDomain(cdnDomain);

            // When
            String url = storageService.getUrl(path);

            // Then
            assertNotNull(url);
            // URL 应该以CDN domain 开头，而不是S3 endpoint
            assertTrue(url.startsWith(cdnDomain), "URL should start with CDN domain");
            // URL 应该包含文件路径
            assertTrue(url.contains(path), "URL should contain the file path");
            // URL 不应该包含bucket 名称（CDN 通常已经配置好路径映射）
            assertFalse(url.contains(properties.getBucket()), "CDN URL should not contain bucket name");
            // URL 不应该包含S3 endpoint
            assertFalse(url.contains(properties.getEndpoint()), "CDN URL should not contain S3 endpoint");
            // 验证完整 URL 格式
            String expectedUrl = cdnDomain + "/" + path;
            assertEquals(expectedUrl, url);
        }

        @Test
        @DisplayName("配置 CDN domain 末尾有斜杠时应该正确处理")
        void shouldHandleCdnDomainWithTrailingSlash() {
            // Given
            String path = "images/2026/01/16/test.jpg";
            String cdnDomain = "https://cdn.example.com/";
            properties.setCdnDomain(cdnDomain);

            // When
            String url = storageService.getUrl(path);

            // Then
            assertNotNull(url);
            // 应该正确处理末尾斜杠，避免双斜杠
            String expectedUrl = "https://cdn.example.com/" + path;
            assertEquals(expectedUrl, url);
            // 确保没有双斜杠
            assertFalse(url.contains("//images"), "URL should not have double slashes before path");
        }

        @Test
        @DisplayName("CDN domain 为空字符串时应该返回 S3 endpoint URL")
        void shouldReturnS3UrlWhenCdnDomainIsEmptyString() {
            // Given
            String path = "test/file.txt";
            properties.setCdnDomain("");

            // When
            String url = storageService.getUrl(path);

            // Then
            assertNotNull(url);
            // 空字符串应该被视为未配置 CDN，返回S3 endpoint URL
            assertTrue(url.startsWith(properties.getEndpoint()), "URL should start with S3 endpoint when CDN is empty");
            assertTrue(url.contains(properties.getBucket()), "URL should contain bucket name");
        }

        @Test
        @DisplayName("CDN domain 为空白字符串时应该返回S3 endpoint URL")
        void shouldReturnS3UrlWhenCdnDomainIsBlank() {
            // Given
            String path = "test/file.txt";
            properties.setCdnDomain("   ");

            // When
            String url = storageService.getUrl(path);

            // Then
            assertNotNull(url);
            // 空白字符串应该被视为未配置CDN，返回S3 endpoint URL
            assertTrue(url.startsWith(properties.getEndpoint()), "URL should start with S3 endpoint when CDN is blank");
            assertTrue(url.contains(properties.getBucket()), "URL should contain bucket name");
        }
    }

    @Nested
    @DisplayName("检查文件是否存在")
    class ExistsFile {

        @Test
        @DisplayName("文件存在时应该返回true")
        void shouldReturnTrueWhenFileExists() {
            // Given
            String path = "test/existing-file.txt";
            
            HeadObjectResponse headResponse = HeadObjectResponse.builder().build();
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenReturn(headResponse);

            // When
            boolean exists = storageService.exists(path);

            // Then
            assertTrue(exists, "Should return true when file exists");
            verify(s3Client).headObject(argThat((HeadObjectRequest request) -> 
                    properties.getBucket().equals(request.bucket()) &&
                    path.equals(request.key())));
        }

        @Test
        @DisplayName("文件不存在时应该返回 false")
        void shouldReturnFalseWhenFileDoesNotExist() {
            // Given
            String path = "test/non-existing-file.txt";
            
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenThrow(NoSuchKeyException.builder()
                            .message("The specified key does not exist")
                            .build());

            // When
            boolean exists = storageService.exists(path);

            // Then
            assertFalse(exists, "Should return false when file does not exist");
            verify(s3Client).headObject(argThat((HeadObjectRequest request) -> 
                    properties.getBucket().equals(request.bucket()) &&
                    path.equals(request.key())));
        }

        @Test
        @DisplayName("检查图片文件存在应该返回正确结果")
        void shouldCheckImageFileExistence() {
            // Given
            String path = "images/2026/01/16/test.jpg";
            
            HeadObjectResponse headResponse = HeadObjectResponse.builder().build();
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenReturn(headResponse);

            // When
            boolean exists = storageService.exists(path);

            // Then
            assertTrue(exists, "Should return true for existing image file");
            verify(s3Client).headObject(argThat((HeadObjectRequest request) -> 
                    path.equals(request.key())));
        }

        @Test
        @DisplayName("S3异常时应该抛出BusinessException")
        void shouldThrowBusinessExceptionWhenS3ExceptionOccurs() {
            // Given
            String path = "test/file.txt";
            
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenThrow(S3Exception.builder()
                            .message("Access denied")
                            .build());

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, 
                    () -> storageService.exists(path));
            assertTrue(exception.getMessage().contains("检查文件是否存在失败"));
        }

        @Test
        @DisplayName("SDK客户端异常时应该抛出 BusinessException")
        void shouldThrowBusinessExceptionWhenSdkClientExceptionOccurs() {
            // Given
            String path = "test/file.txt";
            
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenThrow(SdkClientException.builder()
                            .message("Network connection failed")
                            .build());

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, 
                    () -> storageService.exists(path));
            assertTrue(exception.getMessage().contains("S3 客户端错误"));
        }

        @Test
        @DisplayName("检查复杂路径文件存在应该正确处理")
        void shouldHandleComplexPathCorrectly() {
            // Given
            String path = "uploads/2026/01/16/user123/document-abc123.pdf";
            
            HeadObjectResponse headResponse = HeadObjectResponse.builder().build();
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenReturn(headResponse);

            // When
            boolean exists = storageService.exists(path);

            // Then
            assertTrue(exists, "Should return true for existing file with complex path");
            verify(s3Client).headObject(argThat((HeadObjectRequest request) -> 
                    properties.getBucket().equals(request.bucket()) &&
                    path.equals(request.key())));
        }

        @Test
        @DisplayName("检查空路径应该正确处理")
        void shouldHandleEmptyPath() {
            // Given
            String path = "";
            
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenThrow(NoSuchKeyException.builder()
                            .message("The specified key does not exist")
                            .build());

            // When
            boolean exists = storageService.exists(path);

            // Then
            assertFalse(exists, "Should return false for empty path");
        }
    }

    @Nested
    @DisplayName("Bucket 自动创建")
    class BucketAutoCreation {

        @Test
        @DisplayName("bucket 不存在时应该自动创建")
        void shouldAutoCreateBucketWhenNotExists() {
            // Given
            S3Properties testProperties = new S3Properties();
            testProperties.setEndpoint("http://localhost:9000");
            testProperties.setAccessKey("admin");
            testProperties.setSecretKey("admin123456");
            testProperties.setBucket("new-test-bucket");
            testProperties.setRegion("us-east-1");
            testProperties.setPathStyleAccess(true);
            
            // 创建 mock S3Client
            S3Client mockS3Client = mock(S3Client.class);
            
            // 模拟 bucket 不存在的情况
            when(mockS3Client.headBucket(any(HeadBucketRequest.class)))
                    .thenThrow(NoSuchBucketException.builder()
                            .message("The specified bucket does not exist")
                            .build());
            
            // 模拟创建 bucket 成功
            CreateBucketResponse createResponse = CreateBucketResponse.builder().build();
            when(mockS3Client.createBucket(any(CreateBucketRequest.class)))
                    .thenReturn(createResponse);
            
            // 创建 S3StorageService 并注入mock S3Client
            S3StorageService testService = new S3StorageService(testProperties);
            ReflectionTestUtils.setField(testService, "s3Client", mockS3Client);
            
            // When - 调用 init 方法触发 bucket 检查和创建
            testService.init();
            
            // Then
            // 验证 headBucket 被调用来检查bucket 是否存在
            verify(mockS3Client).headBucket(argThat((HeadBucketRequest request) -> 
                    testProperties.getBucket().equals(request.bucket())));
            
            // 验证 createBucket 被调用来创建 bucket
            verify(mockS3Client).createBucket(argThat((CreateBucketRequest request) -> 
                    testProperties.getBucket().equals(request.bucket())));
        }

        @Test
        @DisplayName("bucket 已存在时不应该创建")
        void shouldNotCreateBucketWhenExists() {
            // Given
            S3Properties testProperties = new S3Properties();
            testProperties.setEndpoint("http://localhost:9000");
            testProperties.setAccessKey("admin");
            testProperties.setSecretKey("admin123456");
            testProperties.setBucket("existing-bucket");
            testProperties.setRegion("us-east-1");
            testProperties.setPathStyleAccess(true);
            
            // 创建 mock S3Client
            S3Client mockS3Client = mock(S3Client.class);
            
            // 模拟 bucket 已存在的情况
            HeadBucketResponse headResponse = HeadBucketResponse.builder().build();
            when(mockS3Client.headBucket(any(HeadBucketRequest.class)))
                    .thenReturn(headResponse);
            
            // 创建 S3StorageService 并注入mock S3Client
            S3StorageService testService = new S3StorageService(testProperties);
            ReflectionTestUtils.setField(testService, "s3Client", mockS3Client);
            
            // When - 调用 init 方法触发 bucket 检查
            testService.init();
            
            // Then
            // 验证 headBucket 被调用来检查bucket 是否存在
            verify(mockS3Client).headBucket(argThat((HeadBucketRequest request) -> 
                    testProperties.getBucket().equals(request.bucket())));
            
            // 验证 createBucket 没有被调用
            verify(mockS3Client, never()).createBucket(any(CreateBucketRequest.class));
        }

        @Test
        @DisplayName("bucket 创建失败时应该抛出BusinessException")
        void shouldThrowBusinessExceptionWhenBucketCreationFails() {
            // Given
            S3Properties testProperties = new S3Properties();
            testProperties.setEndpoint("http://localhost:9000");
            testProperties.setAccessKey("admin");
            testProperties.setSecretKey("admin123456");
            testProperties.setBucket("failed-bucket");
            testProperties.setRegion("us-east-1");
            testProperties.setPathStyleAccess(true);
            
            // 创建 mock S3Client
            S3Client mockS3Client = mock(S3Client.class);
            
            // 模拟 bucket 不存在的情况
            when(mockS3Client.headBucket(any(HeadBucketRequest.class)))
                    .thenThrow(NoSuchBucketException.builder()
                            .message("The specified bucket does not exist")
                            .build());
            
            // 模拟创建 bucket 失败
            when(mockS3Client.createBucket(any(CreateBucketRequest.class)))
                    .thenThrow(S3Exception.builder()
                            .message("Access denied")
                            .build());
            
            // 创建 S3StorageService 并注入mock S3Client
            S3StorageService testService = new S3StorageService(testProperties);
            ReflectionTestUtils.setField(testService, "s3Client", mockS3Client);
            
            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, 
                    () -> testService.init());
            assertTrue(exception.getMessage().contains("创建 S3 存储桶失败"));
            
            // 验证 headBucket 和createBucket 都被调用
            verify(mockS3Client).headBucket(any(HeadBucketRequest.class));
            verify(mockS3Client).createBucket(any(CreateBucketRequest.class));
        }
    }

    @Nested
    @DisplayName("分片上传")
    class MultipartUpload {

        @Test
        @DisplayName("应该成功创建分片上传任务")
        void shouldCreateMultipartUploadSuccessfully() {
            // Given
            String path = "test/large-file.bin";
            String contentType = "application/octet-stream";
            String expectedUploadId = "test-upload-id-123";
            
            CreateMultipartUploadResponse createResponse = CreateMultipartUploadResponse.builder()
                    .uploadId(expectedUploadId)
                    .build();
            when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                    .thenReturn(createResponse);

            // When
            String uploadId = storageService.createMultipartUpload(path, contentType);

            // Then
            assertNotNull(uploadId);
            assertEquals(expectedUploadId, uploadId);
            verify(s3Client).createMultipartUpload(argThat((CreateMultipartUploadRequest request) -> 
                    properties.getBucket().equals(request.bucket()) &&
                    path.equals(request.key()) &&
                    contentType.equals(request.contentType())));
        }

        @Test
        @DisplayName("创建分片上传失败时应该抛出BusinessException")
        void shouldThrowBusinessExceptionWhenCreateMultipartUploadFails() {
            // Given
            String path = "test/large-file.bin";
            String contentType = "application/octet-stream";
            
            when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                    .thenThrow(S3Exception.builder()
                            .message("Failed to create multipart upload")
                            .build());

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, 
                    () -> storageService.createMultipartUpload(path, contentType));
            assertTrue(exception.getMessage().contains("创建分片上传任务失败"));
        }

        @Test
        @DisplayName("应该成功上传分片")
        void shouldUploadPartSuccessfully() {
            // Given
            String path = "test/large-file.bin";
            String uploadId = "test-upload-id-123";
            int partNumber = 1;
            byte[] data = "part data".getBytes();
            String expectedETag = "etag-123";
            
            UploadPartResponse uploadResponse = UploadPartResponse.builder()
                    .eTag(expectedETag)
                    .build();
            when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                    .thenReturn(uploadResponse);

            // When
            String etag = storageService.uploadPart(path, uploadId, partNumber, data);

            // Then
            assertNotNull(etag);
            assertEquals(expectedETag, etag);
            verify(s3Client).uploadPart(
                    argThat((UploadPartRequest request) -> 
                            properties.getBucket().equals(request.bucket()) &&
                            path.equals(request.key()) &&
                            uploadId.equals(request.uploadId()) &&
                            partNumber == request.partNumber()),
                    any(RequestBody.class));
        }

        @Test
        @DisplayName("上传分片失败时应该抛出BusinessException")
        void shouldThrowBusinessExceptionWhenUploadPartFails() {
            // Given
            String path = "test/large-file.bin";
            String uploadId = "test-upload-id-123";
            int partNumber = 1;
            byte[] data = "part data".getBytes();
            
            when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                    .thenThrow(S3Exception.builder()
                            .message("Failed to upload part")
                            .build());

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, 
                    () -> storageService.uploadPart(path, uploadId, partNumber, data));
            assertTrue(exception.getMessage().contains("上传分片失败"));
        }

        @Test
        @DisplayName("应该成功完成分片上传")
        void shouldCompleteMultipartUploadSuccessfully() {
            // Given
            String path = "test/large-file.bin";
            String uploadId = "test-upload-id-123";
            java.util.List<CompletedPart> parts = java.util.Arrays.asList(
                    CompletedPart.builder().partNumber(1).eTag("etag-1").build(),
                    CompletedPart.builder().partNumber(2).eTag("etag-2").build()
            );
            
            CompleteMultipartUploadResponse completeResponse = CompleteMultipartUploadResponse.builder()
                    .build();
            when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                    .thenReturn(completeResponse);

            // When
            String url = storageService.completeMultipartUpload(path, uploadId, parts);

            // Then
            assertNotNull(url);
            assertTrue(url.contains(path));
            verify(s3Client).completeMultipartUpload(argThat((CompleteMultipartUploadRequest request) -> 
                    properties.getBucket().equals(request.bucket()) &&
                    path.equals(request.key()) &&
                    uploadId.equals(request.uploadId()) &&
                    request.multipartUpload() != null &&
                    request.multipartUpload().parts().size() == 2));
        }

        @Test
        @DisplayName("完成分片上传失败时应该抛出BusinessException")
        void shouldThrowBusinessExceptionWhenCompleteMultipartUploadFails() {
            // Given
            String path = "test/large-file.bin";
            String uploadId = "test-upload-id-123";
            java.util.List<CompletedPart> parts = java.util.Arrays.asList(
                    CompletedPart.builder().partNumber(1).eTag("etag-1").build()
            );
            
            when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                    .thenThrow(S3Exception.builder()
                            .message("Failed to complete multipart upload")
                            .build());

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, 
                    () -> storageService.completeMultipartUpload(path, uploadId, parts));
            assertTrue(exception.getMessage().contains("完成分片上传失败"));
        }

        @Test
        @DisplayName("应该成功中止分片上传")
        void shouldAbortMultipartUploadSuccessfully() {
            // Given
            String path = "test/large-file.bin";
            String uploadId = "test-upload-id-123";
            
            AbortMultipartUploadResponse abortResponse = AbortMultipartUploadResponse.builder()
                    .build();
            when(s3Client.abortMultipartUpload(any(AbortMultipartUploadRequest.class)))
                    .thenReturn(abortResponse);

            // When
            storageService.abortMultipartUpload(path, uploadId);

            // Then
            verify(s3Client).abortMultipartUpload(argThat((AbortMultipartUploadRequest request) -> 
                    properties.getBucket().equals(request.bucket()) &&
                    path.equals(request.key()) &&
                    uploadId.equals(request.uploadId())));
        }

        @Test
        @DisplayName("中止分片上传失败时应该抛出BusinessException")
        void shouldThrowBusinessExceptionWhenAbortMultipartUploadFails() {
            // Given
            String path = "test/large-file.bin";
            String uploadId = "test-upload-id-123";
            
            when(s3Client.abortMultipartUpload(any(AbortMultipartUploadRequest.class)))
                    .thenThrow(S3Exception.builder()
                            .message("Failed to abort multipart upload")
                            .build());

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, 
                    () -> storageService.abortMultipartUpload(path, uploadId));
            assertTrue(exception.getMessage().contains("中止分片上传失败"));
        }

        @Test
        @DisplayName("应该支持多个分片的上传")
        void shouldSupportMultiplePartUploads() {
            // Given
            String path = "test/large-file.bin";
            String uploadId = "test-upload-id-123";
            
            // 模拟上传分片 - 使用 any() 匹配器
            UploadPartResponse uploadResponse1 = UploadPartResponse.builder()
                    .eTag("etag-1")
                    .build();
            UploadPartResponse uploadResponse2 = UploadPartResponse.builder()
                    .eTag("etag-2")
                    .build();
            UploadPartResponse uploadResponse3 = UploadPartResponse.builder()
                    .eTag("etag-3")
                    .build();
            
            when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                    .thenReturn(uploadResponse1, uploadResponse2, uploadResponse3);

            // When
            String etag1 = storageService.uploadPart(path, uploadId, 1, "part1".getBytes());
            String etag2 = storageService.uploadPart(path, uploadId, 2, "part2".getBytes());
            String etag3 = storageService.uploadPart(path, uploadId, 3, "part3".getBytes());

            // Then
            assertEquals("etag-1", etag1);
            assertEquals("etag-2", etag2);
            assertEquals("etag-3", etag3);
            verify(s3Client, times(3)).uploadPart(any(UploadPartRequest.class), any(RequestBody.class));
        }
    }

    @Nested
    @DisplayName("预签名URL")
    class PresignedUrl {

        @Mock
        private software.amazon.awssdk.services.s3.presigner.S3Presigner s3Presigner;

        @BeforeEach
        void setUpPresigner() {
            // 注入 mock 的S3Presigner
            ReflectionTestUtils.setField(storageService, "s3Presigner", s3Presigner);
        }

        @Test
        @DisplayName("应该成功生成预签名上传URL")
        void shouldGeneratePresignedPutUrlSuccessfully() {
            // Given
            String path = "test/file.txt";
            String contentType = "text/plain";
            int expireSeconds = 900; // 15 minutes
            String expectedUrl = "https://s3.example.com/bucket/test/file.txt?X-Amz-Signature=...";
            
            software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest presignedRequest = 
                    mock(software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest.class);
            try {
                when(presignedRequest.url()).thenReturn(new java.net.URL(expectedUrl));
            } catch (java.net.MalformedURLException e) {
                fail("Invalid URL in test");
            }
            when(s3Presigner.presignPutObject(any(software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.class)))
                    .thenReturn(presignedRequest);

            // When
            String presignedUrl = storageService.generatePresignedPutUrl(path, contentType, expireSeconds);

            // Then
            assertNotNull(presignedUrl);
            assertEquals(expectedUrl, presignedUrl);
            verify(s3Presigner).presignPutObject(any(software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.class));
        }

        @Test
        @DisplayName("应该成功生成预签名下载URL")
        void shouldGeneratePresignedGetUrlSuccessfully() {
            // Given
            String path = "test/file.txt";
            int expireSeconds = 3600; // 1 hour
            String expectedUrl = "https://s3.example.com/bucket/test/file.txt?X-Amz-Signature=...";
            
            software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest presignedRequest = 
                    mock(software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest.class);
            try {
                when(presignedRequest.url()).thenReturn(new java.net.URL(expectedUrl));
            } catch (java.net.MalformedURLException e) {
                fail("Invalid URL in test");
            }
            when(s3Presigner.presignGetObject(any(software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class)))
                    .thenReturn(presignedRequest);

            // When
            String presignedUrl = storageService.generatePresignedGetUrl(path, expireSeconds);

            // Then
            assertNotNull(presignedUrl);
            assertEquals(expectedUrl, presignedUrl);
            verify(s3Presigner).presignGetObject(any(software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class));
        }

        @Test
        @DisplayName("生成预签名上传URL 失败时应该抛出BusinessException")
        void shouldThrowBusinessExceptionWhenGeneratePresignedPutUrlFails() {
            // Given
            String path = "test/file.txt";
            String contentType = "text/plain";
            int expireSeconds = 900;
            
            when(s3Presigner.presignPutObject(any(software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.class)))
                    .thenThrow(S3Exception.builder()
                            .message("Failed to generate presigned URL")
                            .build());

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, 
                    () -> storageService.generatePresignedPutUrl(path, contentType, expireSeconds));
            assertTrue(exception.getMessage().contains("生成预签名上传URL 失败"));
        }

        @Test
        @DisplayName("生成预签名下载URL 失败时应该抛出BusinessException")
        void shouldThrowBusinessExceptionWhenGeneratePresignedGetUrlFails() {
            // Given
            String path = "test/file.txt";
            int expireSeconds = 3600;
            
            when(s3Presigner.presignGetObject(any(software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class)))
                    .thenThrow(S3Exception.builder()
                            .message("Failed to generate presigned URL")
                            .build());

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, 
                    () -> storageService.generatePresignedGetUrl(path, expireSeconds));
            assertTrue(exception.getMessage().contains("生成预签名下载URL 失败"));
        }

        @Test
        @DisplayName("应该支持不同的过期时间")
        void shouldSupportDifferentExpirationTimes() {
            // Given
            String path = "test/file.txt";
            String contentType = "text/plain";
            int shortExpire = 300; // 5 minutes
            int longExpire = 7200; // 2 hours
            
            software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest presignedRequest = 
                    mock(software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest.class);
            try {
                when(presignedRequest.url()).thenReturn(new java.net.URL("https://s3.example.com/bucket/test/file.txt"));
            } catch (java.net.MalformedURLException e) {
                fail("Invalid URL in test");
            }
            when(s3Presigner.presignPutObject(any(software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.class)))
                    .thenReturn(presignedRequest);

            // When
            String shortUrl = storageService.generatePresignedPutUrl(path, contentType, shortExpire);
            String longUrl = storageService.generatePresignedPutUrl(path, contentType, longExpire);

            // Then
            assertNotNull(shortUrl);
            assertNotNull(longUrl);
            verify(s3Presigner, times(2)).presignPutObject(any(software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.class));
        }

        @Test
        @DisplayName("应该支持不同的内容类型")
        void shouldSupportDifferentContentTypes() {
            // Given
            String path = "test/file";
            int expireSeconds = 900;
            
            software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest presignedRequest = 
                    mock(software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest.class);
            try {
                when(presignedRequest.url()).thenReturn(new java.net.URL("https://s3.example.com/bucket/test/file"));
            } catch (java.net.MalformedURLException e) {
                fail("Invalid URL in test");
            }
            when(s3Presigner.presignPutObject(any(software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.class)))
                    .thenReturn(presignedRequest);

            // When
            String imageUrl = storageService.generatePresignedPutUrl(path + ".jpg", "image/jpeg", expireSeconds);
            String videoUrl = storageService.generatePresignedPutUrl(path + ".mp4", "video/mp4", expireSeconds);
            String pdfUrl = storageService.generatePresignedPutUrl(path + ".pdf", "application/pdf", expireSeconds);

            // Then
            assertNotNull(imageUrl);
            assertNotNull(videoUrl);
            assertNotNull(pdfUrl);
            verify(s3Presigner, times(3)).presignPutObject(any(software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.class));
        }

        @Test
        @DisplayName("SDK客户端异常时应该抛出 BusinessException")
        void shouldThrowBusinessExceptionWhenSdkClientExceptionOccurs() {
            // Given
            String path = "test/file.txt";
            String contentType = "text/plain";
            int expireSeconds = 900;
            
            when(s3Presigner.presignPutObject(any(software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.class)))
                    .thenThrow(SdkClientException.builder()
                            .message("Network connection failed")
                            .build());

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, 
                    () -> storageService.generatePresignedPutUrl(path, contentType, expireSeconds));
            assertTrue(exception.getMessage().contains("S3 客户端错误"));
        }
    }
}
