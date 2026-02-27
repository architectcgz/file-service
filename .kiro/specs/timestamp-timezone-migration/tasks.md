# Implementation Plan: Timestamp Timezone Migration

## Overview

This plan outlines the tasks to migrate from TIMESTAMPTZ to TIMESTAMP and configure PostgreSQL to use UTC+8 timezone. Tasks are ordered to minimize build errors and ensure incremental progress.

## Tasks

- [x] 1. Update Docker Compose configuration
  - Add TZ and PGTZ environment variables to PostgreSQL service
  - Set both to Asia/Shanghai (UTC+8)
  - _Requirements: 1.1, 1.2, 1.3_

- [ ] 2. Update database migration scripts
  - [x] 2.1 Modify V1__create_upload_tables.sql
    - Replace all TIMESTAMPTZ with TIMESTAMP
    - Affects: file_records, storage_objects, upload_tasks, upload_parts
    - _Requirements: 2.1_

  - [x] 2.2 Modify V3__create_tenant_tables.sql
    - Replace all TIMESTAMPTZ with TIMESTAMP
    - Affects: tenants, tenant_usage
    - _Requirements: 2.2_

  - [x] 2.3 Modify V4__create_audit_log_table.sql
    - Replace all TIMESTAMPTZ with TIMESTAMP
    - Affects: admin_audit_logs
    - _Requirements: 2.3_

- [ ] 3. Create LocalDateTimeTypeHandler
  - [x] 3.1 Create new LocalDateTimeTypeHandler class
    - Implement BaseTypeHandler<LocalDateTime>
    - Handle conversion between LocalDateTime and TIMESTAMP
    - _Requirements: 3.1, 3.2_

  - [ ]* 3.2 Write unit tests for LocalDateTimeTypeHandler
    - Test null value handling
    - Test various LocalDateTime values
    - Test edge cases (min/max timestamps)
    - _Requirements: 3.1, 3.2_

- [x] 4. Update MyBatis configuration
  - Update MyBatisConfig to register LocalDateTimeTypeHandler
  - Remove or deprecate OffsetDateTimeTypeHandler registration
  - _Requirements: 3.1, 3.2_

- [ ] 5. Update domain models
  - [x] 5.1 Update FileRecord model
    - Replace OffsetDateTime with LocalDateTime for createdAt, updatedAt
    - _Requirements: 3.1, 3.2, 3.3_

  - [x] 5.2 Update StorageObject model
    - Replace OffsetDateTime with LocalDateTime for createdAt, updatedAt
    - _Requirements: 3.1, 3.2, 3.3_

  - [x] 5.3 Update UploadTask model
    - Replace OffsetDateTime with LocalDateTime for createdAt, updatedAt, completedAt, expiresAt
    - _Requirements: 3.1, 3.2, 3.3_

  - [x] 5.4 Update UploadPart model
    - Replace OffsetDateTime with LocalDateTime for uploadedAt
    - _Requirements: 3.1, 3.2, 3.3_

  - [x] 5.5 Update Tenant model
    - Replace OffsetDateTime with LocalDateTime for createdAt, updatedAt
    - _Requirements: 3.1, 3.2, 3.3_

  - [x] 5.6 Update TenantUsage model
    - Replace OffsetDateTime with LocalDateTime for lastUploadAt, updatedAt
    - _Requirements: 3.1, 3.2, 3.3_

  - [x] 5.7 Update AuditLog model
    - Replace OffsetDateTime with LocalDateTime for createdAt
    - _Requirements: 3.1, 3.2, 3.3_

- [ ] 6. Update PO (Persistent Object) classes
  - [x] 6.1 Update FileRecordPO
    - Replace OffsetDateTime with LocalDateTime
    - _Requirements: 3.1, 3.2_

  - [x] 6.2 Update StorageObjectPO
    - Replace OffsetDateTime with LocalDateTime
    - _Requirements: 3.1, 3.2_

  - [x] 6.3 Update UploadTaskPO
    - Replace OffsetDateTime with LocalDateTime
    - _Requirements: 3.1, 3.2_

  - [x] 6.4 Update UploadPartPO
    - Replace OffsetDateTime with LocalDateTime
    - _Requirements: 3.1, 3.2_

  - [x] 6.5 Update TenantPO
    - Replace OffsetDateTime with LocalDateTime
    - _Requirements: 3.1, 3.2_

  - [x] 6.6 Update TenantUsagePO
    - Replace OffsetDateTime with LocalDateTime
    - _Requirements: 3.1, 3.2_

  - [x] 6.7 Update AuditLogPO
    - Replace OffsetDateTime with LocalDateTime
    - _Requirements: 3.1, 3.2_

- [ ] 7. Update repository implementations
  - [x] 7.1 Update FileRecordRepositoryImpl
    - Update any OffsetDateTime conversion logic
    - _Requirements: 3.1, 3.2_

  - [x] 7.2 Update StorageObjectRepositoryImpl
    - Update any OffsetDateTime conversion logic
    - _Requirements: 3.1, 3.2_

  - [x] 7.3 Update UploadTaskRepositoryImpl
    - Update any OffsetDateTime conversion logic
    - _Requirements: 3.1, 3.2_

  - [x] 7.4 Update TenantRepositoryImpl
    - Update any OffsetDateTime conversion logic
    - _Requirements: 3.1, 3.2_

  - [x] 7.5 Update TenantUsageRepositoryImpl
    - Update any OffsetDateTime conversion logic
    - _Requirements: 3.1, 3.2_

  - [x] 7.6 Update AuditLogRepositoryImpl
    - Update any OffsetDateTime conversion logic
    - _Requirements: 3.1, 3.2_

- [ ] 8. Update utility classes
  - [x] 8.1 Update DateTimeUtils if it exists
    - Replace OffsetDateTime usage with LocalDateTime
    - _Requirements: 3.1, 3.2_

  - [x] 8.2 Search and update any other classes using OffsetDateTime
    - Use IDE search to find all OffsetDateTime references
    - Update to LocalDateTime where appropriate
    - _Requirements: 3.1, 3.2_

- [x] 9. Checkpoint - Rebuild and verify compilation
  - Ensure all code compiles without errors
  - Fix any remaining OffsetDateTime references
  - Ask user if questions arise

- [x] 10. Reset development environment
  - [x] 10.1 Stop all Docker containers
    - Run: `docker-compose -f docker/docker-compose.yml down`
    - _Requirements: 1.1_

  - [x] 10.2 Remove PostgreSQL volume
    - Run: `docker volume rm file_service_postgres_data`
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 10.3 Start containers with new configuration
    - Run: `docker-compose -f docker/docker-compose.yml up -d`
    - _Requirements: 1.1, 1.2, 1.3_

- [x] 11. Verify timezone configuration
  - [x] 11.1 Connect to PostgreSQL and check timezone
    - Run: `docker exec -it file-service-postgres psql -U postgres -d file_service -c "SHOW timezone;"`
    - Verify output shows "Asia/Shanghai" or "UTC+8"
    - _Requirements: 1.2_

  - [x] 11.2 Verify database schema
    - Check that all timestamp columns are TIMESTAMP type (not TIMESTAMPTZ)
    - _Requirements: 2.1, 2.2, 2.3_

- [ ]* 12. Run integration tests
  - Run full test suite to verify timestamp handling
  - Verify no timezone conversion issues
  - _Requirements: 3.1, 3.2_

- [x] 13. Update documentation
  - [x] 13.1 Update file-service README
    - Add section explaining UTC+8 timezone configuration
    - Document that timestamps are stored as local time
    - _Requirements: 4.1, 4.2_

  - [x] 13.2 Update docker README
    - Document PostgreSQL timezone environment variables
    - _Requirements: 4.1_

  - [ ]* 13.3 Update API documentation if needed
    - Note that timestamps are in UTC+8 local time format
    - _Requirements: 4.2_

- [ ] 14. Final checkpoint
  - Ensure all tests pass
  - Verify application starts successfully
  - Ask user if questions arise

## Notes

- Tasks marked with `*` are optional and can be skipped for faster implementation
- The old OffsetDateTimeTypeHandler can be kept for reference but should not be used
- This migration requires dropping and recreating the database
- All existing data will be lost (acceptable in development phase)
