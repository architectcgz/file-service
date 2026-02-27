# Implementation Plan: Fix Test Failures

## Overview

This implementation plan addresses 36 failing tests across 5 categories by making targeted fixes to source code and test configurations. The tasks are ordered to maximize test fixes with each step, starting with the most impactful changes.

## Tasks

- [x] 1. Fix S3StorageService exception handling
  - Add BusinessException wrapping for all S3Exception and SdkClientException catches
  - Ensure proper error messages are included
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_

- [x] 1.1 Fix exception handling in upload method
  - Verify S3Exception is caught and wrapped in BusinessException
  - Verify SdkClientException is caught and wrapped in BusinessException
  - _Requirements: 4.7_

- [x] 1.2 Fix exception handling in delete method
  - Verify S3Exception is caught and wrapped in BusinessException
  - Verify SdkClientException is caught and wrapped in BusinessException
  - _Requirements: 4.2_

- [x] 1.3 Fix exception handling in exists method
  - Verify S3Exception is caught and wrapped in BusinessException
  - Verify SdkClientException is caught and wrapped in BusinessException
  - Keep NoSuchKeyException handling as-is (return false)
  - _Requirements: 4.3, 4.4_

- [x] 1.4 Fix exception handling in createBucket method
  - Verify S3Exception is caught and wrapped in BusinessException
  - Verify SdkClientException is caught and wrapped in BusinessException
  - _Requirements: 4.1_

- [x] 1.5 Fix exception handling in generatePresignedGetUrl method
  - Verify S3Exception is caught and wrapped in BusinessException
  - Verify SdkClientException is caught and wrapped in BusinessException
  - _Requirements: 4.5, 4.6_

- [x] 1.6 Fix exception handling in generatePresignedPutUrl method
  - Verify S3Exception is caught and wrapped in BusinessException
  - Verify SdkClientException is caught and wrapped in BusinessException
  - _Requirements: 4.6_

- [x] 1.7 Run S3StorageServiceTest to verify fixes

  - Execute S3StorageServiceTest
  - Verify all 7 exception handling tests pass
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_

- [x] 2. Fix FileAccessService permission check order
  - Reorder validation checks to: appId → deletion status → access level
  - Update error messages to match test expectations
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 2.1 Fix getFileUrl method validation order
  - Move appId ownership check before deletion status check
  - Move appId ownership check before access level check
  - Ensure error message is "无权访问该文件，文件不属于当前应用" for appId mismatch
  - Ensure error message is "文件已删除" for deleted files (after appId check)
  - Ensure error message is "无权访问该文件" for private file non-owner access (after appId and deletion checks)
  - _Requirements: 3.1, 3.2, 3.3, 3.4_

- [x] 2.2 Fix getFileDetail method validation order
  - Move appId ownership check before deletion status check
  - Move appId ownership check before access level check
  - Ensure error message is "无权访问该文件，文件不属于当前应用" for appId mismatch
  - Ensure error message is "文件已删除" for deleted files (after appId check)
  - Ensure error message is "无权访问该文件" for private file non-owner access (after appId and deletion checks)
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 2.3 Run FileAccessServiceTest to verify fixes

  - Execute FileAccessServiceTest
  - Verify all 10 permission check tests pass
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 3. Checkpoint - Verify core service fixes
  - Ensure all tests pass for S3StorageService and FileAccessService
  - Ask the user if questions arise

- [x] 4. Fix integration test configurations
  - Add missing test property sources and profiles
  - Ensure ApplicationContext loads successfully
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [x] 4.1 Fix AccessPropertiesTest configuration
  - Add @TestPropertySource with storage.access.* properties
  - Add @ActiveProfiles("test") if needed
  - Verify test loads ApplicationContext successfully
  - _Requirements: 1.1_

- [x] 4.2 Fix MultipartPropertiesTest configuration
  - Add @TestPropertySource with storage.multipart.* properties
  - Add @ActiveProfiles("test") if needed
  - Verify test loads ApplicationContext successfully
  - _Requirements: 1.2_

- [x] 4.3 Fix FileDeduplicationTest configuration
  - Add @ActiveProfiles("test")
  - Add @TestPropertySource with required storage properties
  - Verify test loads ApplicationContext successfully
  - _Requirements: 1.3_

- [x] 4.4 Fix MultiAppFileIsolationTest configuration
  - Add @ActiveProfiles("test")
  - Add @TestPropertySource with required storage properties
  - Verify test loads ApplicationContext successfully
  - _Requirements: 1.4_

- [x] 4.5 Run integration tests to verify fixes

  - Execute AccessPropertiesTest, MultipartPropertiesTest, FileDeduplicationTest, MultiAppFileIsolationTest
  - Verify all 10 integration tests pass
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [x] 5. Fix MultipartControllerTest configuration
  - Ensure controller is properly registered in test context
  - Add missing @Import annotations if needed
  - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [x] 5.1 Update MultipartControllerTest annotations
  - Verify @WebMvcTest(controllers = MultipartController.class) is used
  - Add @Import for required configuration classes if needed
  - Ensure @MockBean is used for MultipartUploadService
  - _Requirements: 2.4_

- [x] 5.2 Verify MultipartController has correct annotations
  - Ensure @RestController annotation is present
  - Ensure @RequestMapping("/api/v1/multipart") is correct
  - Verify endpoint mappings match test expectations
  - _Requirements: 2.1, 2.2_

- [x] 5.3 Run MultipartControllerTest to verify fixes

  - Execute MultipartControllerTest
  - Verify all 3 controller tests pass (no 404 errors)
  - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [x] 6. Fix Mockito matcher usage in MultipartUploadServiceTest
  - Identify and fix mixed matcher usage
  - Ensure all mock setups follow all-or-none matcher rule
  - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [x] 6.1 Fix shouldThrowExceptionWhenFileTooLarge test
  - Review all mock setups in the test method
  - Identify the line with mixed matchers (3 expected, 2 recorded)
  - Fix by using all matchers or all concrete values consistently
  - _Requirements: 5.2_

- [x] 6.2 Run MultipartUploadServiceTest to verify fix

  - Execute MultipartUploadServiceTest
  - Verify shouldThrowExceptionWhenFileTooLarge test passes
  - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [x] 7. Final verification - Run full test suite
  - Execute all tests in file-service module
  - Verify all 36 previously failing tests now pass
  - Ensure no regressions in previously passing tests

- [x] 8. Checkpoint - Final review
  - Ensure all tests pass
  - Ask the user if questions arise

## Notes

- Tasks marked with `*` are optional test verification steps that can be skipped for faster implementation
- Each task references specific requirements for traceability
- The implementation order is optimized to fix the most tests with each step
- Checkpoint tasks ensure incremental validation and allow for user feedback
- All fixes are minimal and targeted to avoid introducing new issues
