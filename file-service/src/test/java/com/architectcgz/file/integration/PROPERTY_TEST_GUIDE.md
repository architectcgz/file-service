# Property-Based Testing Guide for RustFS Integration Tests

## Overview

The RustFS integration tests include property-based tests that verify correctness properties across many randomly generated inputs. These tests use the jqwik library for property-based testing in Java.

## Property Tests Implemented

### Task 4.2: Text File Upload Properties

**Test Method:** `textFileUpload_shouldEnsureFileExistenceAndURLAccessConsistency`

**Properties Verified:**

1. **Property 1: 上传后文件存在性 (File Existence After Upload)**
   - For any text file uploaded successfully, the file should exist in RustFS at the specified path
   - The file content in RustFS should match the uploaded content exactly

2. **Property 2: URL 访问一致性 (URL Access Consistency)**
   - For any text file uploaded successfully, the returned URL should be accessible
   - The content downloaded via URL should match the uploaded content exactly

**Test Configuration:**
- Runs 100 iterations with randomly generated text files
- Text content: 10-1000 characters with alphanumeric and punctuation
- Filenames: 5-20 characters with alphanumeric, dash, and underscore

**Validates:** Requirements 1.1, 1.3, 1.4, 2.1, 2.2, 2.3

## Prerequisites

### 1. Start MinIO/RustFS

The property-based tests require a running MinIO or RustFS instance on port 9100.

**Option A: Using Docker (MinIO)**
```bash
docker run -d --name minio-test \
  -p 9100:9000 \
  -e "MINIO_ROOT_USER=admin" \
  -e "MINIO_ROOT_PASSWORD=admin123456" \
  minio/minio server /data
```

**Option B: Using Docker Compose (RustFS)**
```bash
cd docker
docker-compose up -d rustfs
```

### 2. Create Test Bucket

After starting MinIO/RustFS, create the test bucket:

```bash
# Using MinIO Client (mc)
mc alias set minio-test http://localhost:9100 admin admin123456
mc mb minio-test/test-bucket
```

Or use the MinIO web console at http://localhost:9101

## Running Property-Based Tests

### Run All Property Tests
```bash
cd file-service
mvn test -Dtest=RustFSIntegrationTest
```

### Run Specific Property Test
```bash
mvn test -Dtest=RustFSIntegrationTest#textFileUpload_shouldEnsureFileExistenceAndURLAccessConsistency
```

### Run with Verbose Output
```bash
mvn test -Dtest=RustFSIntegrationTest -Djqwik.reporting.usejunitplatform=true
```

## Understanding Property Test Output

### Successful Run
```
[jqwik] textFileUpload_shouldEnsureFileExistenceAndURLAccessConsistency
        tries = 100
        checks = 100
        generation-mode = RANDOMIZED
        seed = 1234567890
```

### Failed Run (Example)
```
[jqwik] textFileUpload_shouldEnsureFileExistenceAndURLAccessConsistency
        tries = 42
        checks = 42
        seed = 1234567890
        
Shrunk Sample (1 steps)
-----------------------
  content: "test"
  filename: "test-abc.txt"
  
Property 1 violated: File should exist in RustFS at path: /test-app/2024/01/20/abc123.txt
```

When a property test fails, jqwik will:
1. Show which iteration failed (e.g., try 42 out of 100)
2. Attempt to shrink the failing input to the minimal example
3. Display the shrunk input that causes the failure

## Test Configuration

The property tests use the same configuration as regular integration tests:

**File:** `src/test/resources/application-rustfs-test.yml`

Key settings:
```yaml
storage:
  s3:
    endpoint: http://localhost:9100
    access-key: admin
    secret-key: admin123456
    bucket: test-bucket
```

## Troubleshooting

### Test Skipped
If you see "Tests run: 1, Failures: 0, Errors: 0, Skipped: 1", it means:
- RustFS/MinIO is not running on port 9100
- The `@BeforeAll` check failed to connect to RustFS

**Solution:** Start MinIO/RustFS as described in Prerequisites

### Property Test Fails
If a property test fails:
1. Check the shrunk sample to see the minimal failing input
2. Verify RustFS is accessible and the bucket exists
3. Check logs for detailed error messages
4. Run the specific failing case manually to debug

### Compilation Errors
If you see jqwik-related compilation errors:
- Ensure jqwik dependency is in pom.xml (version 1.8.2)
- Run `mvn clean compile test-compile`

## Property Test Best Practices

### 1. Keep Properties Simple
Each property should test one clear invariant:
- ✅ "Uploaded files exist in storage"
- ❌ "Uploaded files exist, have correct permissions, and can be deleted"

### 2. Use Meaningful Generators
The test uses custom generators for:
- Text content: Realistic character sets and lengths
- Filenames: Valid filename patterns

### 3. Verify Cleanup
Property tests run many iterations, so cleanup is critical:
- Each iteration adds files to `testContext`
- `@AfterEach` cleanup removes all test files
- Prevents storage pollution across test runs

### 4. Monitor Test Duration
100 iterations with real S3 operations can take time:
- Typical run: 30-60 seconds
- If too slow, reduce `tries` parameter
- Consider using `@Tag("slow")` for CI/CD filtering

## Next Steps

After verifying property tests pass:
1. Implement property tests for image files (Task 4.4)
2. Implement property tests for large files (Task 5.2)
3. Implement property tests for file deletion (Task 6.2, 6.4)

## References

- jqwik Documentation: https://jqwik.net/docs/current/user-guide.html
- Design Document: `.kiro/specs/rustfs-integration-test/design.md`
- Requirements: `.kiro/specs/rustfs-integration-test/requirements.md`
- Tasks: `.kiro/specs/rustfs-integration-test/tasks.md`
