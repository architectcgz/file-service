# Running RustFS Integration Tests

## Quick Start

To run the property-based test for image file Content-Type preservation (Task 4.4):

### 1. Start MinIO (Required)

The tests require MinIO running on port 9100:

```bash
docker run -d --name minio-test \
  -p 9100:9000 \
  -p 9101:9001 \
  -e "MINIO_ROOT_USER=admin" \
  -e "MINIO_ROOT_PASSWORD=admin123456" \
  minio/minio server /data
```

### 2. Create Test Bucket

```bash
# Using MinIO Client (mc)
mc alias set minio-test http://localhost:9100 admin admin123456
mc mb minio-test/test-bucket
```

Or use the MinIO web console at http://localhost:9101

### 3. Run the Property Test

```bash
cd file-service
mvn test -Dtest=RustFSIntegrationTest#imageFileUpload_shouldPreserveContentType
```

## What the Test Does

The property-based test `imageFileUpload_shouldPreserveContentType` validates **Property 3: 文件类型保持性** (File Type Preservation):

- Runs 100 iterations with randomly generated image files
- Tests both JPEG and PNG image types
- Verifies that Content-Type is preserved in RustFS storage
- Ensures uploaded Content-Type matches stored Content-Type

## Test Output

Successful test output will show:
```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

If MinIO is not running, the test will be skipped:
```
[WARNING] Tests run: 1, Failures: 0, Errors: 0, Skipped: 1
```

## Cleanup

After testing, stop and remove the MinIO container:

```bash
docker stop minio-test
docker rm minio-test
```
