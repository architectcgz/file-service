# Implementation Plan: File Service Client SDK

## Overview

This implementation plan breaks down the File Service Client SDK development into discrete, incremental tasks. The plan follows a bottom-up approach: core models → client implementation → Spring Boot integration → testing.

**注意：所有代码注释使用中文编写。**

## Tasks

- [x] 1. Set up project structure and core models
  - Create `file-service-client` Maven module with pom.xml
  - Create `file-service-spring-boot-starter` Maven module with pom.xml
  - Define package structure: `com.platform.fileservice.client`
  - Create `AccessLevel` enum (PUBLIC, PRIVATE)
  - Create `ApiResponse<T>` generic wrapper class
  - _Requirements: 1.1, 1.2, 1.5_

- [x] 2. Implement request and response models
  - [x] 2.1 Create request models
    - Create `FileUploadRequest` with builder
    - Create `MultipartInitRequest` with builder
    - Create `InstantUploadCheckRequest` with builder
    - Create `PresignedUploadRequest` with builder
    - _Requirements: 2.1, 2.2, 2.5, 5.1, 13.1_
  
  - [ ]* 2.2 Write property test for request model validation
    - **Property 10: Instant Upload Request Structure**
    - **Validates: Requirements 13.2**
  
  - [x] 2.3 Create response models
    - Create `FileUploadResponse` with all required fields
    - Create `FileDetailResponse` with all required fields
    - Create `MultipartInitResponse` with all required fields
    - Create `MultipartUploadPart` with partNumber and etag
    - Create `InstantUploadCheckResponse` with exists flag
    - Create `PresignedUploadResponse` with URL and expiry
    - _Requirements: 1.5, 3.4, 5.5, 9.2, 9.3, 9.4_
  
  - [ ]* 2.4 Write property test for response structure completeness
    - **Property 3: Response Structure Completeness**
    - **Validates: Requirements 3.4, 9.2, 9.3, 9.4**

- [x] 3. Implement exception hierarchy
  - [x] 3.1 Create exception classes
    - Create `FileServiceException` base class
    - Create `InvalidRequestException` (400)
    - Create `AuthenticationException` (401)
    - Create `AccessDeniedException` (403)
    - Create `FileNotFoundException` (404)
    - Create `QuotaExceededException` (413)
    - Create `NetworkException` (network errors)
    - Create `ParseException` (JSON parsing errors)
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 9.5_
  
  - [ ]* 3.2 Write property test for HTTP error code mapping
    - **Property 2: HTTP Error Code Mapping**
    - **Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5, 8.7**

- [x] 4. Implement configuration and token provider
  - [x] 4.1 Create TokenProvider interface
    - Define `getToken()` method
    - Add static factory methods: `fixed()`, `fromSupplier()`
    - _Requirements: 6.3, 12.1, 12.3_
  
  - [x] 4.2 Create FileServiceClientConfig
    - Add required fields: serverUrl, tenantId, tokenProvider
    - Add optional fields: timeouts, connection pool, domains, retry settings
    - Implement `validate()` method with all validation rules
    - Use Builder pattern with defaults
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 15.1, 15.2_
  
  - [ ]* 4.3 Write property test for configuration validation
    - **Property 4: Configuration Validation**
    - **Validates: Requirements 6.6, 11.3, 12.4**

- [x] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement FileServiceClient interface
  - [x] 6.1 Define FileServiceClient interface
    - Add image upload methods
    - Add file upload methods (with and without access level)
    - Add file access methods (getFileUrl, getFileDetail)
    - Add file deletion method
    - Add multipart upload methods (init, uploadPart, complete, cancel)
    - Add instant upload methods
    - Add presigned URL methods
    - Extend AutoCloseable for resource management
    - _Requirements: 1.1, 1.2, 2.1, 2.2, 2.4, 2.5, 3.1, 3.3, 4.1, 5.1, 5.2, 5.3, 5.4, 10.4, 13.1_

- [x] 7. Implement HTTP client functionality
  - [x] 7.1 Create FileServiceClientImpl class
    - Initialize HttpClient with connection pool configuration
    - Implement constructor accepting FileServiceClientConfig
    - Call config.validate() in constructor
    - _Requirements: 6.6, 10.1, 10.2_
  
  - [x] 7.2 Implement request building logic
    - Create method to build HTTP requests with headers
    - Add X-App-Id header from config.tenantId
    - Add Authorization header from tokenProvider.getToken()
    - Add Content-Type header based on request type
    - _Requirements: 1.3, 1.4, 11.1, 11.2, 12.2_
  
  - [ ]* 7.3 Write property test for tenant ID header inclusion
    - **Property 1: Tenant ID Header Inclusion**
    - **Validates: Requirements 1.3, 11.1, 11.2**
  
  - [ ]* 7.4 Write property test for authentication token inclusion
    - **Property 7: Authentication Token Inclusion**
    - **Validates: Requirements 1.4, 12.2**
  
  - [x] 7.5 Implement response parsing logic
    - Create method to parse JSON responses using Jackson
    - Handle successful responses (200)
    - Handle error responses (4xx, 5xx)
    - Map HTTP status codes to exceptions
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.7, 9.1_
  
  - [ ]* 7.6 Write property test for JSON parsing robustness
    - **Property 5: JSON Parsing Robustness**
    - **Validates: Requirements 9.5**
  
  - [x] 7.7 Implement URL domain replacement logic
    - Create method to replace URL domain
    - Handle CDN domain for PUBLIC files
    - Handle custom domain for other cases
    - Preserve path and query parameters
    - _Requirements: 15.3, 15.4, 15.5_
  
  - [ ]* 7.8 Write property test for URL domain replacement
    - **Property 6: URL Domain Replacement**
    - **Validates: Requirements 15.3, 15.4, 15.5**

- [x] 8. Implement file upload operations
  - [x] 8.1 Implement image upload methods
    - Implement `uploadImage(File)` method
    - Implement `uploadImage(InputStream, String, long)` method
    - Build multipart/form-data request
    - Parse FileUploadResponse
    - Apply URL domain replacement
    - _Requirements: 1.1, 2.1, 2.6_
  
  - [x] 8.2 Implement file upload methods
    - Implement `uploadFile(File)` method
    - Implement `uploadFile(InputStream, String, long, String)` method
    - Implement `uploadFile(File, AccessLevel)` method
    - Include access level in request when specified
    - _Requirements: 1.2, 2.1, 2.3_
  
  - [ ]* 8.3 Write property test for access level preservation
    - **Property 8: Access Level Preservation**
    - **Validates: Requirements 2.3**
  
  - [ ]* 8.4 Write property test for small file direct upload
    - **Property 12: Small File Direct Upload**
    - **Validates: Requirements 2.1**

- [x] 9. Implement file access operations
  - [x] 9.1 Implement getFileUrl method
    - Build GET request to /api/v1/files/{fileId}/url
    - Parse URL response
    - Apply domain replacement
    - _Requirements: 3.1, 3.2_
  
  - [x] 9.2 Implement getFileDetail method
    - Build GET request to /api/v1/files/{fileId}
    - Parse FileDetailResponse
    - Apply domain replacement to URL field
    - _Requirements: 3.3, 3.4_
  
  - [ ]* 9.3 Write unit tests for file access operations
    - Test getFileUrl with mock responses
    - Test getFileDetail with mock responses
    - Test domain replacement in responses
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

- [x] 10. Implement file deletion operation
  - [x] 10.1 Implement deleteFile method
    - Build DELETE request to /api/v1/upload/{fileId}
    - Handle success response
    - Handle error responses
    - _Requirements: 4.1, 4.3_
  
  - [ ]* 10.2 Write unit tests for file deletion
    - Test successful deletion
    - Test deletion of non-existent file (404)
    - Test deletion of file from different tenant (403)
    - _Requirements: 4.1, 4.2, 4.3_

- [ ] 11. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 12. Implement multipart upload operations
  - [x] 12.1 Implement initMultipartUpload method
    - Build POST request to /api/v1/multipart/init
    - Send MultipartInitRequest as JSON
    - Parse MultipartInitResponse
    - _Requirements: 5.1_
  
  - [x] 12.2 Implement uploadPart method
    - Build POST request to /api/v1/multipart/{taskId}/upload?partNumber={n}
    - Send chunk data as multipart/form-data
    - Parse MultipartUploadPart response
    - Return part with partNumber and etag
    - _Requirements: 5.2, 5.5_
  
  - [ ]* 12.3 Write property test for multipart chunk metadata
    - **Property 9: Multipart Chunk Metadata**
    - **Validates: Requirements 5.5**
  
  - [x] 12.4 Implement completeMultipartUpload method
    - Build POST request to /api/v1/multipart/{taskId}/complete
    - Send fileHash in JSON body
    - Parse FileUploadResponse
    - Apply domain replacement
    - _Requirements: 5.3_
  
  - [x] 12.5 Implement cancelMultipartUpload method
    - Build DELETE request to /api/v1/multipart/{taskId}
    - Handle success response
    - _Requirements: 5.4_
  
  - [ ]* 12.6 Write unit tests for multipart upload flow
    - Test complete multipart upload flow (init → upload parts → complete)
    - Test multipart upload cancellation
    - Test error handling in multipart upload
    - _Requirements: 2.2, 5.1, 5.2, 5.3, 5.4, 5.5_

- [x] 13. Implement instant upload and presigned URL operations
  - [x] 13.1 Implement checkInstantUpload method
    - Build POST request to /api/v1/instant-upload/check
    - Send InstantUploadCheckRequest as JSON
    - Parse InstantUploadCheckResponse
    - Apply domain replacement if file exists
    - _Requirements: 2.5, 13.1, 13.2_
  
  - [x] 13.2 Implement getPresignedUploadUrl method
    - Build POST request to /api/v1/presigned/upload-url
    - Send PresignedUploadRequest as JSON
    - Parse PresignedUploadResponse
    - _Requirements: 2.4_
  
  - [x] 13.3 Implement confirmPresignedUpload method
    - Build POST request to /api/v1/presigned/{fileId}/confirm
    - Send fileHash in JSON body
    - Parse FileUploadResponse
    - Apply domain replacement
    - _Requirements: 2.4_
  
  - [ ]* 13.4 Write unit tests for instant upload and presigned URL
    - Test instant upload when file exists
    - Test instant upload when file doesn't exist
    - Test presigned URL generation and confirmation
    - _Requirements: 2.4, 2.5, 13.3, 13.4_

- [x] 14. Implement resource management and logging
  - [x] 14.1 Implement close method
    - Shut down HttpClient gracefully
    - Release connection pool resources
    - Mark client as closed
    - _Requirements: 10.4, 10.5_
  
  - [x] 14.2 Add logging with SLF4J
    - Log request URLs and methods at DEBUG level
    - Log response status codes at DEBUG level
    - Log errors at ERROR level
    - Ensure tokens and file content are not logged
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5_
  
  - [ ]* 14.3 Write property test for sensitive data exclusion from logs
    - **Property 11: Sensitive Data Exclusion from Logs**
    - **Validates: Requirements 14.5**
  
  - [ ]* 14.4 Write unit tests for resource management
    - Test client close() method
    - Test that closed client throws exception on use
    - _Requirements: 10.5_

- [ ] 15. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 16. Implement Spring Boot auto-configuration
  - [x] 16.1 Create FileServiceProperties
    - Add @ConfigurationProperties with prefix "file-service.client"
    - Add all configuration fields with defaults
    - _Requirements: 7.2, 7.3, 7.4, 15.6_
  
  - [x] 16.2 Create SpringTokenProvider
    - Implement TokenProvider interface
    - Try to get token from Spring Security context
    - Fall back to static token from properties
    - _Requirements: 12.1, 12.3_
  
  - [x] 16.3 Create FileServiceAutoConfiguration
    - Add @Configuration and @ConditionalOnClass
    - Enable FileServiceProperties
    - Create TokenProvider bean with @ConditionalOnMissingBean
    - Create FileServiceClient bean with @ConditionalOnMissingBean
    - Build FileServiceClientConfig from properties
    - _Requirements: 7.1, 7.2, 7.5_
  
  - [x] 16.4 Create spring.factories file
    - Register FileServiceAutoConfiguration
    - Place in src/main/resources/META-INF/
    - _Requirements: 7.1_
  
  - [ ]* 16.5 Write integration tests for Spring Boot auto-configuration
    - Test auto-configuration with properties
    - Test bean creation and injection
    - Test default values
    - Test custom TokenProvider override
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

- [x] 17. Create example usage and documentation
  - [x] 17.1 Create example Spring Boot application
    - Create example project in `examples/` directory
    - Show basic file upload usage
    - Show multipart upload usage
    - Show instant upload usage
    - Demonstrate error handling
    - _Requirements: All_
  
  - [x] 17.2 Create README.md for client module
    - Document installation instructions
    - Document configuration options
    - Provide usage examples
    - Document exception handling
    - _Requirements: All_
  
  - [x] 17.3 Create README.md for Spring Boot starter
    - Document Spring Boot integration
    - Document application.yml configuration
    - Provide Spring Boot usage examples
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

- [ ] 18. Configure Maven for artifact publishing
  - [ ] 18.1 Configure pom.xml for client module
    - Set groupId, artifactId, version
    - Declare all dependencies
    - Configure maven-source-plugin for source JAR
    - Configure maven-javadoc-plugin for javadoc JAR
    - _Requirements: 16.1, 16.3, 16.4, 16.5_
  
  - [ ] 18.2 Configure pom.xml for Spring Boot starter
    - Set groupId, artifactId, version
    - Add dependency on file-service-client
    - Add Spring Boot dependencies
    - Configure source and javadoc plugins
    - _Requirements: 16.2, 16.3, 16.4, 16.5_
  
  - [ ] 18.3 Create parent pom.xml
    - Define modules: file-service-client, file-service-spring-boot-starter
    - Define dependency management
    - Configure common plugins
    - _Requirements: 16.1, 16.2, 16.3_

- [ ] 19. Final checkpoint - Ensure all tests pass
  - Run all unit tests
  - Run all property-based tests
  - Run all integration tests
  - Verify Maven build succeeds
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
- Integration tests validate Spring Boot auto-configuration
