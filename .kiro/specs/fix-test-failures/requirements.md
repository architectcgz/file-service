# Requirements Document

## Introduction

This document specifies the requirements for fixing all failing unit and integration tests in the file-service project. The test suite currently has 36 failing tests across multiple categories: ApplicationContext loading failures, routing issues, permission check logic errors, exception handling problems, and mock configuration issues.

## Glossary

- **File_Service**: The Spring Boot microservice that handles file upload, storage, and access operations
- **Test_Suite**: The collection of unit and integration tests for the File_Service
- **ApplicationContext**: Spring's dependency injection container that manages beans and their lifecycle
- **S3_Storage_Service**: The service component that interacts with S3-compatible storage (MinIO)
- **File_Access_Service**: The service component that validates and controls file access permissions
- **Multipart_Controller**: The REST controller that handles multipart upload endpoints
- **Multipart_Upload_Service**: The service that manages chunked file uploads
- **Integration_Test**: A test that loads the full Spring ApplicationContext
- **Unit_Test**: A test that uses mocks and doesn't load the full ApplicationContext

## Requirements

### Requirement 1: Fix ApplicationContext Loading Failures

**User Story:** As a developer, I want all integration tests to successfully load the Spring ApplicationContext, so that I can test the application with realistic dependency injection.

#### Acceptance Criteria

1. WHEN AccessPropertiesTest runs, THE Test_Suite SHALL successfully load the ApplicationContext with test properties
2. WHEN MultipartPropertiesTest runs, THE Test_Suite SHALL successfully load the ApplicationContext with multipart configuration
3. WHEN FileDeduplicationTest runs, THE Test_Suite SHALL successfully load the ApplicationContext with test profile
4. WHEN MultiAppFileIsolationTest runs, THE Test_Suite SHALL successfully load the ApplicationContext with test profile
5. WHEN any integration test loads ApplicationContext, THE Test_Suite SHALL not exceed the failure threshold

### Requirement 2: Fix Multipart Controller Routing

**User Story:** As a developer, I want the multipart upload endpoints to be correctly routed, so that controller tests can verify the API behavior.

#### Acceptance Criteria

1. WHEN a POST request is sent to /api/v1/multipart/init, THE Multipart_Controller SHALL handle the request and return status 200
2. WHEN a PUT request is sent to /api/v1/multipart/{taskId}/parts/{partNumber}, THE Multipart_Controller SHALL handle the request and return status 200
3. WHEN an invalid taskId is provided, THE Multipart_Controller SHALL return a server error status (5xx)
4. WHEN the controller is tested with MockMvc, THE Test_Suite SHALL not receive 404 errors for valid endpoints

### Requirement 3: Fix File Access Service Permission Logic

**User Story:** As a developer, I want file access permission checks to return correct error messages, so that users understand why access was denied.

#### Acceptance Criteria

1. WHEN a deleted file is accessed, THE File_Access_Service SHALL throw an exception with message "文件已删除"
2. WHEN a private file is accessed by a non-owner, THE File_Access_Service SHALL throw an exception with message "无权访问该文件"
3. WHEN a file from a different appId is accessed, THE File_Access_Service SHALL check appId ownership before checking other permissions
4. WHEN permission checks are performed, THE File_Access_Service SHALL validate in the correct order: appId ownership, deletion status, then access level
5. WHEN a public file is accessed, THE File_Access_Service SHALL return file details without requiring ownership

### Requirement 4: Fix S3 Storage Service Exception Handling

**User Story:** As a developer, I want S3 storage operations to properly throw BusinessException when errors occur, so that error handling can be tested and verified.

#### Acceptance Criteria

1. WHEN bucket creation fails, THE S3_Storage_Service SHALL throw BusinessException with appropriate error code
2. WHEN file deletion encounters SdkClientException, THE S3_Storage_Service SHALL throw BusinessException
3. WHEN file existence check encounters S3Exception, THE S3_Storage_Service SHALL throw BusinessException
4. WHEN file existence check encounters SdkClientException, THE S3_Storage_Service SHALL throw BusinessException
5. WHEN presigned URL generation fails, THE S3_Storage_Service SHALL throw BusinessException
6. WHEN presigned URL generation encounters SdkClientException, THE S3_Storage_Service SHALL throw BusinessException
7. WHEN file upload encounters SdkClientException, THE S3_Storage_Service SHALL throw BusinessException

### Requirement 5: Fix Multipart Upload Service Mock Configuration

**User Story:** As a developer, I want Mockito argument matchers to be used correctly in tests, so that mock behavior can be properly configured and verified.

#### Acceptance Criteria

1. WHEN configuring mock behavior with multiple parameters, THE Test_Suite SHALL use matchers for all parameters or none
2. WHEN testing file size validation in MultipartUploadServiceTest, THE Test_Suite SHALL properly mock the validator with correct argument matchers
3. WHEN Mockito detects invalid matcher usage, THE Test_Suite SHALL fail with a clear error message indicating the problem
4. WHEN mock methods are called during tests, THE Test_Suite SHALL verify that argument matchers match the actual invocations

### Requirement 6: Maintain Test Isolation and Independence

**User Story:** As a developer, I want each test to run independently without side effects, so that test results are reliable and reproducible.

#### Acceptance Criteria

1. WHEN any test modifies shared state, THE Test_Suite SHALL reset that state after the test completes
2. WHEN integration tests use the database, THE Test_Suite SHALL use transactions that rollback after each test
3. WHEN tests create mock objects, THE Test_Suite SHALL ensure mocks don't leak between test methods
4. WHEN tests run in any order, THE Test_Suite SHALL produce the same results

### Requirement 7: Ensure Correct Test Configuration

**User Story:** As a developer, I want test configurations to match the application's runtime configuration, so that tests accurately reflect production behavior.

#### Acceptance Criteria

1. WHEN integration tests load properties, THE Test_Suite SHALL use test-specific property files or annotations
2. WHEN tests require specific Spring profiles, THE Test_Suite SHALL activate the correct profiles
3. WHEN tests need to override properties, THE Test_Suite SHALL use @TestPropertySource or equivalent mechanisms
4. WHEN MockMvc tests run, THE Test_Suite SHALL properly configure the web application context with all necessary controllers and filters
