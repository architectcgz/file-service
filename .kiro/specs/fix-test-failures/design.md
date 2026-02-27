# Design Document: Fix Test Failures

## Overview

This document provides a comprehensive design for fixing all 36 failing tests in the file-service project. The failures fall into five main categories:

1. **ApplicationContext Loading Failures** (10 tests) - Integration tests fail to load Spring context due to missing or misconfigured beans
2. **Controller Routing Issues** (3 tests) - MockMvc tests receive 404 errors because controllers are not properly registered
3. **Permission Check Logic Errors** (10 tests) - FileAccessService validates permissions in the wrong order, causing incorrect error messages
4. **Exception Handling Issues** (7 tests) - S3StorageService catches exceptions but doesn't rethrow BusinessException as expected
5. **Mock Configuration Errors** (1 test) - Mockito argument matchers used incorrectly

The design focuses on minimal, targeted fixes that address root causes rather than symptoms.

## Architecture

### Component Interaction

```
Test Layer
├── Integration Tests (@SpringBootTest)
│   ├── AccessPropertiesTest
│   ├── MultipartPropertiesTest
│   ├── FileDeduplicationTest
│   └── MultiAppFileIsolationTest
├── Controller Tests (@WebMvcTest)
│   └── MultipartControllerTest
└── Service Tests (@ExtendWith(MockitoExtension))
    ├── FileAccessServiceTest
    ├── S3StorageServiceTest
    └── MultipartUploadServiceTest

Application Layer
├── FileAccessService (permission validation order fix)
├── MultipartUploadService (mock configuration fix)
└── Controllers (routing configuration fix)

Infrastructure Layer
└── S3StorageService (exception handling fix)
```

### Key Design Decisions

1. **Fix Permission Check Order**: Modify FileAccessService to check appId ownership FIRST, before deletion status or access level
2. **Add Missing Exception Handling**: Wrap all S3Exception and SdkClientException in BusinessException
3. **Fix Test Configuration**: Add missing @Import annotations and test property sources
4. **Correct Mock Usage**: Fix Mockito matcher usage to follow all-or-none rule
5. **Fix Controller Registration**: Ensure MultipartController is properly scanned in @WebMvcTest

## Components and Interfaces

### 1. FileAccessService Permission Check Order

**Current Implementation Problem**:
```java
// Current order (WRONG):
1. Check if file exists
2. Check appId ownership  
3. Check deletion status
4. Check access level
```

**Fixed Implementation**:
```java
// Correct order:
1. Check if file exists
2. Check appId ownership (FIRST)
3. Check deletion status (SECOND)
4. Check access level (THIRD)
```

**Rationale**: AppId ownership is the most fundamental security check. If a file doesn't belong to the requesting app, we should fail immediately with "无权访问该文件，文件不属于当前应用" before checking anything else. This prevents information leakage about file status.

### 2. S3StorageService Exception Handling

**Current Implementation Problem**:
```java
catch (S3Exception e) {
    log.error("...", e);
    // Missing: throw new BusinessException(...)
}
catch (SdkClientException e) {
    log.error("...", e);
    // Missing: throw new BusinessException(...)
}
```

**Fixed Implementation**:
```java
catch (S3Exception e) {
    log.error("...", e);
    throw new BusinessException("操作失败: " + e.getMessage(), e);
}
catch (SdkClientException e) {
    log.error("...", e);
    throw new BusinessException("S3 客户端错误: " + e.getMessage(), e);
}
```

**Methods Requiring Fixes**:
- `ensureBucketExists()` / `createBucket()`
- `delete(String path)`
- `exists(String path)`
- `upload(byte[] data, String path, String contentType)`
- `generatePresignedGetUrl(String path, int expireSeconds)`
- `generatePresignedPutUrl(String path, String contentType, int expireSeconds)`

### 3. Integration Test Configuration

**Problem**: Tests fail with "ApplicationContext failure threshold exceeded" because required beans are not available in test context.

**Root Causes**:
1. Missing `@TestPropertySource` for custom properties
2. Missing `@Import` for configuration classes
3. Missing `@ActiveProfiles("test")` for test-specific beans
4. Incomplete test application context configuration

**Solution Pattern**:
```java
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "storage.type=s3",
    "storage.s3.endpoint=http://localhost:9000",
    "storage.s3.bucket=test-bucket",
    // ... other required properties
})
class IntegrationTest {
    // Test methods
}
```

**Affected Tests**:
- `AccessPropertiesTest` - needs storage.access.* properties
- `MultipartPropertiesTest` - needs storage.multipart.* properties
- `FileDeduplicationTest` - needs full application context with test profile
- `MultiAppFileIsolationTest` - needs full application context with test profile

### 4. Controller Test Configuration

**Problem**: `MultipartControllerTest` receives 404 errors because the controller is not properly registered in the test context.

**Root Cause**: `@WebMvcTest(MultipartController.class)` doesn't automatically scan for the controller if it's not in the default package structure or if there are missing dependencies.

**Solution**:
```java
@WebMvcTest(controllers = MultipartController.class)
@Import({
    // Import any required configuration classes
    // Import any required service beans (or use @MockBean)
})
class MultipartControllerTest {
    @MockBean
    private MultipartUploadService multipartUploadService;
    
    // Test methods
}
```

**Additional Fix**: Ensure controller has proper `@RestController` and `@RequestMapping` annotations.

### 5. Mockito Argument Matcher Fix

**Problem**: `MultipartUploadServiceTest.shouldThrowExceptionWhenFileTooLarge` uses mixed matchers and raw values.

**Current Code (WRONG)**:
```java
doNothing().when(fileTypeValidator).validateFile(anyString(), anyString(), anyLong());
// Later called with:
fileTypeValidator.validateFile(request.getFileName(), request.getContentType(), request.getFileSize());
// But mock setup has 3 matchers, actual call has 3 values - this should work
// The issue is likely in a different line
```

**Investigation Needed**: The error message indicates "3 matchers expected, 2 recorded", suggesting the actual problem is elsewhere in the test setup.

**Solution**: Review all mock setups in the test and ensure consistent matcher usage:
```java
// Option 1: All matchers
when(repository.findByUserIdAndFileHash(anyString(), anyLong(), anyString()))
    .thenReturn(Optional.empty());

// Option 2: All concrete values
when(repository.findByUserIdAndFileHash("blog", 12345L, "hash123"))
    .thenReturn(Optional.empty());

// WRONG: Mixed
when(repository.findByUserIdAndFileHash("blog", anyLong(), anyString())) // DON'T DO THIS
```

## Data Models

No data model changes required. All fixes are in business logic and test configuration.

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: AppId Check Precedence
*For any* file access request where the file belongs to a different appId, the error message should be "无权访问该文件，文件不属于当前应用" regardless of the file's deletion status or access level.

**Validates: Requirements 3.3, 3.4**

### Property 2: Deleted File Error Message
*For any* file access request where the file is deleted AND belongs to the correct appId, the error message should be "文件已删除" regardless of the access level or requesting user.

**Validates: Requirements 3.1**

### Property 3: Private File Access Error Message
*For any* file access request where the file is private, not deleted, belongs to the correct appId, AND the requesting user is not the owner, the error message should be "无权访问该文件".

**Validates: Requirements 3.2**

### Property 4: Public File Access
*For any* public file that is not deleted and belongs to the correct appId, any user should be able to access the file details without ownership validation.

**Validates: Requirements 3.5**

### Property 5: S3 Exception Wrapping
*For any* S3 operation that throws S3Exception or SdkClientException, the service should throw BusinessException with an appropriate error message.

**Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7**

## Error Handling

### FileAccessService Error Handling

**Validation Order** (CRITICAL):
1. File existence check → "文件不存在"
2. AppId ownership check → "无权访问该文件，文件不属于当前应用"
3. Deletion status check → "文件已删除"
4. Access level check → "无权访问该文件"

**Error Messages**:
- File not found: `"文件不存在"`
- Wrong appId: `"无权访问该文件，文件不属于当前应用"`
- File deleted: `"文件已删除"`
- Private file, non-owner: `"无权访问该文件"`

### S3StorageService Error Handling

**Exception Mapping**:
- `S3Exception` → `BusinessException` with message: `"操作失败: " + e.getMessage()`
- `SdkClientException` → `BusinessException` with message: `"S3 客户端错误: " + e.getMessage()`
- `NoSuchKeyException` (in exists()) → return `false` (not an error)
- `NoSuchBucketException` (in ensureBucketExists()) → create bucket (not an error)

**Methods Requiring Exception Handling**:
1. `upload()` - wrap S3Exception and SdkClientException
2. `delete()` - wrap S3Exception and SdkClientException
3. `exists()` - wrap S3Exception and SdkClientException (but not NoSuchKeyException)
4. `createBucket()` - wrap S3Exception and SdkClientException
5. `generatePresignedGetUrl()` - wrap S3Exception and SdkClientException
6. `generatePresignedPutUrl()` - wrap S3Exception and SdkClientException

## Testing Strategy

### Unit Tests

**FileAccessService Tests**:
- Test permission check order with specific scenarios
- Verify correct error messages for each validation failure
- Test edge cases: null access level, missing storage object

**S3StorageService Tests**:
- Mock S3Client to throw S3Exception and verify BusinessException is thrown
- Mock S3Client to throw SdkClientException and verify BusinessException is thrown
- Verify exception messages contain appropriate context

**MultipartUploadService Tests**:
- Fix Mockito matcher usage in existing tests
- Ensure all mock setups follow all-or-none matcher rule

### Integration Tests

**Configuration Tests**:
- `AccessPropertiesTest` - verify properties are loaded correctly
- `MultipartPropertiesTest` - verify multipart configuration is loaded correctly

**Feature Tests**:
- `FileDeduplicationTest` - verify file deduplication works across app boundaries
- `MultiAppFileIsolationTest` - verify files are isolated by appId

**Controller Tests**:
- `MultipartControllerTest` - verify endpoints are properly routed
- Test successful requests return 200
- Test invalid requests return appropriate error codes

### Test Configuration Requirements

**Integration Tests** (`@SpringBootTest`):
- Must use `@ActiveProfiles("test")` to load test-specific beans
- Must use `@TestPropertySource` to provide required properties
- Must configure test database (H2 or testcontainers)
- Must use `@Transactional` for automatic rollback

**Controller Tests** (`@WebMvcTest`):
- Must specify controller class: `@WebMvcTest(controllers = XxxController.class)`
- Must use `@MockBean` for service dependencies
- Must import required configuration classes with `@Import`

**Service Tests** (`@ExtendWith(MockitoExtension)`):
- Must use `@Mock` for dependencies
- Must use `@InjectMocks` for service under test
- Must follow Mockito matcher rules: all matchers or all concrete values

### Property-Based Testing

While most of the requirements are about test infrastructure (not testable as properties), we can write property-based tests for the functional requirements:

**Property Test 1: Permission Check Order**
- Generate random file records with various combinations of appId, deletion status, and access level
- Verify that error messages follow the correct precedence order

**Property Test 2: S3 Exception Handling**
- Generate random S3 operations
- Mock S3Client to throw various exceptions
- Verify that all exceptions are wrapped in BusinessException

**Configuration**:
- Use JUnit QuickCheck or jqwik for Java property-based testing
- Run minimum 100 iterations per property test
- Tag each test with: `@Tag("property-test")` and `@Tag("fix-test-failures")`

## Implementation Notes

### Fix Order

Implement fixes in this order to minimize test failures:

1. **Fix S3StorageService exception handling** - This fixes 7 tests
2. **Fix FileAccessService permission check order** - This fixes 10 tests
3. **Fix integration test configuration** - This fixes 10 tests
4. **Fix controller test configuration** - This fixes 3 tests
5. **Fix Mockito matcher usage** - This fixes 1 test

### Code Changes Summary

**FileAccessService.java**:
- Move appId check before deletion status check in `getFileUrl()`
- Move appId check before deletion status check in `getFileDetail()`
- Update error messages to match test expectations

**S3StorageService.java**:
- Add `throw new BusinessException(...)` after logging in all catch blocks
- Ensure all S3Exception and SdkClientException are wrapped
- Keep NoSuchKeyException handling in `exists()` as-is (return false)

**Test Configuration Files**:
- Add `@TestPropertySource` to AccessPropertiesTest
- Add `@TestPropertySource` to MultipartPropertiesTest
- Add `@ActiveProfiles("test")` to integration tests
- Add `@Import` to MultipartControllerTest
- Fix Mockito matchers in MultipartUploadServiceTest

### Backward Compatibility

All changes are internal to the service layer and test configuration. No API changes are required. The fixes improve error message accuracy and test reliability without affecting production behavior.

### Performance Considerations

No performance impact. The changes only affect:
1. Order of validation checks (same number of checks)
2. Exception wrapping (negligible overhead)
3. Test configuration (no runtime impact)
