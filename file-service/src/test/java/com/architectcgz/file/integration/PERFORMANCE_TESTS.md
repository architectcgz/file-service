# Performance Benchmark Tests

This document describes the performance benchmark tests for the RustFS integration.

## Overview

The performance tests measure upload and download throughput for various file sizes to ensure the file service meets acceptable performance thresholds.

## Test Suite

### Test 10.1: Upload Performance Test
- **Test Method**: `measureUploadPerformance()`
- **Purpose**: Measures upload performance for different file sizes
- **File Sizes**: 1MB, 5MB, 10MB, 20MB
- **Metrics**:
  - Upload time (milliseconds)
  - Throughput (MB/s)
- **Threshold**: Minimum 1 MB/s upload throughput
- **Validates**: Requirements 8.1, 8.4

### Test 10.2: Download Performance Test
- **Test Method**: `measureDownloadPerformance()`
- **Purpose**: Measures download performance via URL access
- **File Sizes**: 1MB, 5MB, 10MB, 20MB
- **Metrics**:
  - Download time (milliseconds)
  - Throughput (MB/s)
- **Validates**: Requirements 8.2

### Test 10.3: Comprehensive Performance Test
- **Test Method**: `comprehensivePerformanceTest()`
- **Purpose**: Combined upload and download performance testing with detailed reporting
- **File Sizes**: 1MB, 5MB, 10MB, 15MB, 20MB
- **Metrics**:
  - Upload and download time for each file size
  - Throughput comparison
  - Average, minimum, and maximum throughput
  - Performance recommendations
- **Validates**: Requirements 8.3

## Running Performance Tests

### Prerequisites

1. **RustFS/MinIO must be running**:
   ```bash
   cd docker
   docker-compose up -d minio
   ```

2. **Verify RustFS is accessible**:
   - Endpoint: http://localhost:9100
   - Access Key: admin
   - Secret Key: admin123456

### Run All Performance Tests

```bash
cd file-service
mvn test -Dgroups=performance -Dtest=RustFSIntegrationTest
```

### Run Individual Performance Tests

**Upload Performance Test**:
```bash
mvn test -Dtest=RustFSIntegrationTest#measureUploadPerformance
```

**Download Performance Test**:
```bash
mvn test -Dtest=RustFSIntegrationTest#measureDownloadPerformance
```

**Comprehensive Performance Test**:
```bash
mvn test -Dtest=RustFSIntegrationTest#comprehensivePerformanceTest
```

### Exclude Performance Tests from Regular Test Runs

Performance tests are tagged with `@Tag("performance")` and can be excluded from regular test runs:

```bash
# Run all tests EXCEPT performance tests
mvn test -Dtest=RustFSIntegrationTest -DexcludedGroups=performance
```

## Performance Metrics

### Upload Performance Metrics
- **File Size**: Size of the uploaded file in MB and bytes
- **Upload Time**: Time taken to upload the file in milliseconds
- **Throughput**: Upload speed in MB/s (calculated as: file_size_MB / (upload_time_ms / 1000))

### Download Performance Metrics
- **File Size**: Size of the downloaded file in MB and bytes
- **Download Time**: Time taken to download the file via URL in milliseconds
- **Throughput**: Download speed in MB/s (calculated as: file_size_MB / (download_time_ms / 1000))

### Performance Thresholds

- **Minimum Upload Throughput**: 1 MB/s
- **Acceptable Upload Throughput**: 5-10 MB/s
- **Excellent Upload Throughput**: > 10 MB/s

## Performance Report Format

The comprehensive performance test generates a detailed report including:

1. **Test Configuration**:
   - Storage type (S3/RustFS)
   - Endpoint URL
   - Bucket name
   - Multipart upload settings

2. **Individual File Results**:
   - Upload and download metrics for each file size
   - Content integrity verification

3. **Performance Summary**:
   - Upload performance statistics (average, min, max throughput)
   - Download performance statistics (average, min, max throughput)
   - Performance comparison (upload vs download)

4. **Recommendations**:
   - Performance assessment based on thresholds
   - Suggestions for optimization if needed

## Example Output

```
================================================================================
COMPREHENSIVE PERFORMANCE BENCHMARK TEST
================================================================================
Test Configuration:
  Storage Type: S3 (RustFS)
  Endpoint: http://localhost:9100
  Bucket: test-bucket
  Multipart Threshold: 10 MB
  Multipart Part Size: 5 MB
================================================================================

Testing 1 MB File
Upload Results:
  File ID: abc123
  Duration: 250 ms (0.25 seconds)
  Throughput: 4.00 MB/s
  Status: PASS

Download Results:
  URL: http://localhost:9100/test-bucket/...
  Duration: 200 ms (0.20 seconds)
  Throughput: 5.00 MB/s
  Content Integrity: VERIFIED

...

COMPREHENSIVE PERFORMANCE REPORT
================================================================================
UPLOAD PERFORMANCE SUMMARY
Individual Results:
  Upload 1MB: 1.00 MB in 250 ms (4.00 MB/s)
  Upload 5MB: 5.00 MB in 1000 ms (5.00 MB/s)
  Upload 10MB: 10.00 MB in 2000 ms (5.00 MB/s)
  Upload 15MB: 15.00 MB in 3000 ms (5.00 MB/s)
  Upload 20MB: 20.00 MB in 4000 ms (5.00 MB/s)

Statistics:
  Average Throughput: 4.80 MB/s
  Minimum Throughput: 4.00 MB/s
  Maximum Throughput: 5.00 MB/s
  Overall Status: PASS

RECOMMENDATIONS
  ✓ Upload throughput is acceptable (5-10 MB/s)
    - Performance is within normal range for network storage
```

## Troubleshooting

### Performance Tests Fail with Connection Error

**Problem**: Tests fail with "RustFS is not available" error

**Solution**:
1. Verify RustFS/MinIO is running: `docker ps | grep minio`
2. Check endpoint is accessible: `curl http://localhost:9100`
3. Verify credentials in test configuration

### Low Throughput Performance

**Problem**: Throughput is below 1 MB/s

**Possible Causes**:
1. Network bandwidth limitations
2. RustFS/MinIO resource constraints
3. Local disk I/O bottleneck
4. Multipart upload settings not optimized

**Solutions**:
1. Check network bandwidth: `iperf3` or similar tools
2. Increase RustFS/MinIO resources (CPU, memory)
3. Adjust multipart upload threshold and part size
4. Run tests on faster storage (SSD vs HDD)

### Tests Take Too Long

**Problem**: Performance tests take excessive time to complete

**Solution**:
1. Reduce file sizes in test configuration
2. Reduce number of test iterations
3. Run tests individually instead of all at once
4. Use faster storage backend

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Performance Tests

on:
  schedule:
    - cron: '0 2 * * *'  # Run daily at 2 AM
  workflow_dispatch:  # Allow manual trigger

jobs:
  performance-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Set up JDK 17
        uses: actions/setup-java@v2
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Start RustFS
        run: |
          cd docker
          docker-compose up -d minio
          sleep 10  # Wait for RustFS to be ready
      
      - name: Run Performance Tests
        run: |
          cd file-service
          mvn test -Dgroups=performance -Dtest=RustFSIntegrationTest
      
      - name: Upload Performance Report
        uses: actions/upload-artifact@v2
        with:
          name: performance-report
          path: file-service/target/surefire-reports/
      
      - name: Stop RustFS
        run: |
          cd docker
          docker-compose down
```

## Notes

- Performance tests are **optional** and tagged with `@Tag("performance")`
- Tests can be excluded from regular CI/CD runs to save time
- Performance results may vary based on:
  - Network conditions
  - System resources (CPU, memory, disk)
  - RustFS/MinIO configuration
  - Concurrent load on the system
- For consistent results, run tests in a controlled environment
- Performance tests verify both throughput and content integrity
