# Design Document: Timestamp Timezone Migration

## Overview

This design document outlines the approach for migrating the file-service database from `TIMESTAMPTZ` (timestamp with time zone) to `TIMESTAMP` (timestamp without time zone), and configuring the PostgreSQL database to use UTC+8 timezone. Since the system is in development phase, we will directly modify existing migration scripts rather than creating new ones.

## Architecture

### Current State
- All database tables use `TIMESTAMPTZ` for timestamp columns
- PostgreSQL container runs with default timezone (UTC)
- Java application uses `OffsetDateTime` with `OffsetDateTimeTypeHandler` for MyBatis
- Timestamps are stored with timezone information

### Target State
- All database tables use `TIMESTAMP` for timestamp columns
- PostgreSQL container configured with UTC+8 timezone
- Java application uses `LocalDateTime` with appropriate type handler
- Timestamps stored as local time without timezone information

## Components and Interfaces

### 1. Docker Compose Configuration

**File:** `docker/docker-compose.yml`

**Changes:**
- Add `TZ=Asia/Shanghai` environment variable to PostgreSQL service
- Add `PGTZ=Asia/Shanghai` environment variable to PostgreSQL service

**Configuration:**
```yaml
postgres:
  environment:
    - TZ=Asia/Shanghai
    - PGTZ=Asia/Shanghai
    - POSTGRES_USER=${POSTGRES_USER:-postgres}
    - POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-postgres}
    - POSTGRES_DB=${POSTGRES_DB:-file_service}
```

### 2. Database Migration Scripts

**Files to Modify:**
- `file-service/src/main/resources/db/migration/V1__create_upload_tables.sql`
- `file-service/src/main/resources/db/migration/V3__create_tenant_tables.sql`
- `file-service/src/main/resources/db/migration/V4__create_audit_log_table.sql`

**Changes:**
Replace all occurrences of `TIMESTAMPTZ` with `TIMESTAMP`

**Affected Tables and Columns:**

V1 Migration:
- `file_records`: created_at, updated_at
- `storage_objects`: created_at, updated_at
- `upload_tasks`: created_at, updated_at, completed_at, expires_at
- `upload_parts`: uploaded_at

V3 Migration:
- `tenants`: created_at, updated_at
- `tenant_usage`: last_upload_at, updated_at

V4 Migration:
- `admin_audit_logs`: created_at

### 3. MyBatis Type Handler

**File:** `file-service/src/main/java/com/architectcgz/file/infrastructure/config/OffsetDateTimeTypeHandler.java`

**Current Implementation:**
- Handles `OffsetDateTime` ↔ `TIMESTAMPTZ` conversion
- Preserves timezone information

**Target Implementation:**
- Option A: Rename to `LocalDateTimeTypeHandler` and handle `LocalDateTime` ↔ `TIMESTAMP`
- Option B: Keep `OffsetDateTimeTypeHandler` but adapt to work with `TIMESTAMP` by using system default timezone (UTC+8)

**Recommended Approach:** Option A
- Simpler and more explicit
- `LocalDateTime` naturally represents time without timezone
- Aligns with the database schema change

### 4. Domain Models

**Files to Update:**
All domain model classes that use `OffsetDateTime` for timestamp fields:

- `file-service/src/main/java/com/architectcgz/file/domain/model/FileRecord.java`
- `file-service/src/main/java/com/architectcgz/file/domain/model/StorageObject.java`
- `file-service/src/main/java/com/architectcgz/file/domain/model/UploadTask.java`
- `file-service/src/main/java/com/architectcgz/file/domain/model/UploadPart.java`
- `file-service/src/main/java/com/architectcgz/file/domain/model/Tenant.java`
- `file-service/src/main/java/com/architectcgz/file/domain/model/TenantUsage.java`
- `file-service/src/main/java/com/architectcgz/file/domain/model/AuditLog.java`

**Changes:**
- Replace `OffsetDateTime` with `LocalDateTime`
- Update getter/setter methods
- Update any date/time manipulation logic

### 5. Repository Implementations and PO Classes

**Files to Update:**
All PO (Persistent Object) classes in `file-service/src/main/java/com/architectcgz/file/infrastructure/repository/po/`:

- `FileRecordPO.java`
- `StorageObjectPO.java`
- `UploadTaskPO.java`
- `UploadPartPO.java`
- `TenantPO.java`
- `TenantUsagePO.java`
- `AuditLogPO.java`

**Changes:**
- Replace `OffsetDateTime` with `LocalDateTime`
- Update conversion logic in repository implementations

### 6. MyBatis Configuration

**File:** `file-service/src/main/java/com/architectcgz/file/infrastructure/config/MyBatisConfig.java`

**Changes:**
- Update type handler registration from `OffsetDateTimeTypeHandler` to `LocalDateTimeTypeHandler`
- Ensure proper mapping for `LocalDateTime` ↔ `TIMESTAMP`

## Data Models

### Type Handler Implementation

**New LocalDateTimeTypeHandler:**

```java
public class LocalDateTimeTypeHandler extends BaseTypeHandler<LocalDateTime> {
    
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, LocalDateTime parameter, JdbcType jdbcType) throws SQLException {
        ps.setTimestamp(i, Timestamp.valueOf(parameter));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnIndex);
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    @Override
    public LocalDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Timestamp timestamp = cs.getTimestamp(columnIndex);
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }
}
```

### Domain Model Example

**Before:**
```java
public class FileRecord {
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
```

**After:**
```java
public class FileRecord {
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Timezone Configuration Persistence
*For any* PostgreSQL container restart, the timezone setting should remain UTC+8 and all timestamp operations should use this timezone.
**Validates: Requirements 1.1, 1.2, 1.3**

### Property 2: Type Consistency
*For any* timestamp value stored in the database, reading it back through the application should return the same local time value without timezone conversion.
**Validates: Requirements 3.1, 3.2**

### Property 3: Schema Consistency
*For all* database tables with timestamp columns, the column type should be TIMESTAMP (not TIMESTAMPTZ) after migration script execution.
**Validates: Requirements 2.1, 2.2, 2.3**

## Error Handling

### Migration Errors
- If Flyway detects checksum mismatch on modified migrations, the database must be recreated
- Document the need to drop and recreate the database when modifying existing migrations

### Type Conversion Errors
- If `LocalDateTime` conversion fails, log detailed error with column name and value
- Throw appropriate exception to prevent data corruption

### Timezone Configuration Errors
- If PostgreSQL timezone cannot be set, container startup should fail with clear error message
- Validate timezone setting on application startup

## Testing Strategy

### Unit Tests
- Test `LocalDateTimeTypeHandler` with various timestamp values
- Test domain model serialization/deserialization
- Test edge cases: null values, min/max timestamps

### Integration Tests
- Verify PostgreSQL timezone configuration
- Test timestamp storage and retrieval through full stack
- Verify MyBatis mapper operations with new type handler

### Property-Based Tests
- Generate random `LocalDateTime` values and verify round-trip consistency
- Test that timezone changes don't affect stored values

### Manual Verification
1. Start PostgreSQL container and verify timezone: `SHOW timezone;`
2. Insert test data and verify stored format in database
3. Retrieve data through application and verify no timezone conversion occurs
4. Check application logs for any timezone-related warnings

## Implementation Notes

### Breaking Changes
- Existing databases must be dropped and recreated
- Any external systems expecting timezone information will need updates
- API responses may change if they include timestamp fields

### Deployment Steps
1. Stop all running containers
2. Remove existing PostgreSQL volume: `docker volume rm file_service_postgres_data`
3. Update docker-compose.yml with timezone configuration
4. Update all migration scripts (V1, V3, V4)
5. Update Java code (type handler, domain models, PO classes)
6. Rebuild and restart containers
7. Verify timezone configuration
8. Run integration tests

### Rollback Plan
Since we're in development phase:
- Revert code changes from version control
- Drop and recreate database with original migrations
- Restart containers

## Documentation Updates

### Files to Update
1. `file-service/README.md` - Add timezone configuration section
2. `docker/README.md` - Document PostgreSQL timezone setting
3. API documentation - Note that timestamps are in UTC+8 local time

### Content to Add
- Explanation that all timestamps are stored as UTC+8 local time
- Examples of correct timestamp handling in application code
- Note about timezone-naive timestamp storage
- Migration guide for development environment reset
