# File Service Integration Tests

## Overview

This directory contains integration tests for the file service refactoring, specifically testing multi-app file isolation and file deduplication across different application IDs.

## Test Classes

### 1. MultiAppFileIsolationTest

**Purpose:** Tests that files uploaded by different applications (identified by `appId`) are properly isolated and cannot be accessed across application boundaries.

**Test Scenarios:**
- **Blog app uploads file, IM app cannot access** - Verifies that when blog app uploads a file, IM app receives 403 Forbidden when trying to access it
- **Same file in different appIds are independently deduplicated** - Verifies that the same file uploaded to different apps creates separate storage objects
- **File deletion validates appId ownership** - Verifies that only the owning app can delete its files
- **Cross-appId file detail access is denied** - Verifies that file metadata cannot be accessed across app boundaries

**Validates Requirements:** 2.4, 2.5, 4.4, 4.5, 4.6

### 2. FileDeduplicationTest

**Purpose:** Tests that file deduplication works correctly within the same application while maintaining isolation across different applications.

**Test Scenarios:**
- **Same file uploaded twice in same appId shares storage** - Verifies that uploading the same file twice creates two file records but shares one storage object
- **Same file in different appIds does NOT share storage** - Verifies that appId isolation prevents cross-app deduplication
- **Different files create separate storage objects** - Verifies that different files don't share storage
- **Deleting one file decrements reference count** - Verifies that reference counting works correctly
- **Multiple users uploading same file share storage within appId** - Verifies that different users in the same app can share deduplicated files
- **Storage object query filters by appId and hash** - Verifies that storage object lookups respect appId boundaries

**Validates Requirements:** 2.4, 2.5, 3.1, 3.2, 3.3

## Key Testing Patterns

### App ID Isolation
All tests verify that the `X-App-Id` header is properly enforced:
- Files are tagged with their originating appId
- Cross-app access attempts return 403 Forbidden
- Storage paths include appId prefix (e.g., `/blog/...`, `/im/...`)

### File Deduplication
Tests verify the deduplication logic:
- Same file hash within same appId → shared storage object
- Same file hash across different appIds → separate storage objects
- Reference counting increments/decrements correctly
- Physical file deletion only occurs when reference count reaches zero

### User Isolation
Tests verify that multiple users can:
- Upload the same file and share storage (within same appId)
- Delete their own file records without affecting other users
- Access only their own files

## Running the Tests

### Prerequisites
1. Fix compilation errors in the main codebase (see `file-service/docker/CHECKPOINT_STATUS.md`)
2. Ensure test database is configured in `src/test/resources/application-test.yml`
3. Ensure all dependencies are available

### Run All Integration Tests
```bash
mvn test -Dtest=*IntegrationTest -pl blog-upload
```

### Run Specific Test Class
```bash
mvn test -Dtest=MultiAppFileIsolationTest -pl blog-upload
mvn test -Dtest=FileDeduplicationTest -pl blog-upload
```

### Run Specific Test Method
```bash
mvn test -Dtest=MultiAppFileIsolationTest#blogUpload_imAccess_shouldReturn403 -pl blog-upload
```

## Test Configuration

### Test Profile
Tests use the `test` profile which should be configured in:
- `src/test/resources/application-test.yml`

### Required Configuration
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/file_service_test
    username: postgres
    password: postgres
  
  jpa:
    hibernate:
      ddl-auto: create-drop
```

### Mock Data
Tests use `MockMultipartFile` to simulate file uploads without requiring actual file I/O.

## Expected Behavior

### Successful Test Run
When all tests pass, you should see:
- All file isolation checks pass (403 for cross-app access)
- All deduplication checks pass (shared storage within app, isolated across apps)
- All reference counting checks pass (correct increment/decrement)
- All storage path checks pass (correct appId prefix)

### Common Failures
1. **403 not returned for cross-app access** - AppIdValidationInterceptor not configured
2. **Storage objects shared across appIds** - Deduplication logic not checking appId
3. **Reference count incorrect** - Reference counting logic has bugs
4. **Storage paths missing appId** - StoragePathGenerator not including appId

## Integration with CI/CD

These tests should be run as part of the integration test phase:
```bash
mvn verify -pl blog-upload
```

## Troubleshooting

### Compilation Errors
If you see "未结束的字符串文字" errors, the main codebase has unterminated string literals with Chinese characters. Fix those first before running tests.

### Database Connection Errors
Ensure PostgreSQL is running and the test database exists:
```sql
CREATE DATABASE file_service_test;
```

### Test Data Cleanup
Tests use `@Transactional` annotation which automatically rolls back changes after each test. No manual cleanup is needed.

## Related Documentation

- Design Document: `.kiro/specs/file-service-refactoring/design.md`
- Requirements: `.kiro/specs/file-service-refactoring/requirements.md`
- Tasks: `.kiro/specs/file-service-refactoring/tasks.md`
- Checkpoint Status: `file-service/docker/CHECKPOINT_STATUS.md`

## Next Steps

After these integration tests pass:
1. Proceed to Task 18: Write API test scripts (PowerShell)
2. Proceed to Task 19: Update documentation
3. Proceed to Task 20: Final checkpoint - end-to-end verification
