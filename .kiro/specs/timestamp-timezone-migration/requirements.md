# Requirements Document

## Introduction

This document specifies the requirements for migrating the file-service database from `TIMESTAMPTZ` (timestamp with time zone) to `TIMESTAMP` (timestamp without time zone), and configuring the PostgreSQL database to use UTC+8 timezone in the Docker environment.

## Glossary

- **File_Service**: The file management service application
- **PostgreSQL**: The relational database system used by File_Service
- **TIMESTAMPTZ**: PostgreSQL data type for timestamp with time zone
- **TIMESTAMP**: PostgreSQL data type for timestamp without time zone
- **Docker_Compose**: The container orchestration configuration
- **Flyway**: The database migration tool used by File_Service
- **UTC+8**: The timezone offset (China Standard Time)

## Requirements

### Requirement 1: Database Timezone Configuration

**User Story:** As a system administrator, I want the PostgreSQL database to use UTC+8 timezone, so that all timestamp values are stored and displayed in the correct local timezone.

#### Acceptance Criteria

1. WHEN the PostgreSQL container starts, THE Docker_Compose SHALL set the timezone environment variable to UTC+8
2. WHEN the PostgreSQL container is running, THE PostgreSQL SHALL report its timezone as UTC+8
3. WHEN a timestamp is stored without explicit timezone, THE PostgreSQL SHALL interpret it as UTC+8

### Requirement 2: Migration Script Creation

**User Story:** As a developer, I want to directly modify existing Flyway migration scripts, so that the database schema uses TIMESTAMP instead of TIMESTAMPTZ from the beginning.

#### Acceptance Criteria

1. WHEN modifying V1 migration, THE Migration_Script SHALL use TIMESTAMP instead of TIMESTAMPTZ for all timestamp columns
2. WHEN modifying V3 migration, THE Migration_Script SHALL use TIMESTAMP instead of TIMESTAMPTZ for all timestamp columns
3. WHEN modifying V4 migration, THE Migration_Script SHALL use TIMESTAMP instead of TIMESTAMPTZ for all timestamp columns
4. THE System SHALL not require a new V5 migration since we are modifying existing migrations directly

### Requirement 3: Java Type Handler Update

**User Story:** As a developer, I want the Java application to handle timestamp types correctly, so that date/time values are processed without timezone conversion issues.

#### Acceptance Criteria

1. WHEN the application reads timestamp values from the database, THE Type_Handler SHALL interpret them as local time without timezone conversion
2. WHEN the application writes timestamp values to the database, THE Type_Handler SHALL store them as local time without timezone conversion
3. THE Application SHALL continue to use OffsetDateTime or LocalDateTime for timestamp fields in domain models

### Requirement 4: Documentation Update

**User Story:** As a developer, I want updated documentation about the timezone handling, so that I understand how to work with timestamps correctly.

#### Acceptance Criteria

1. THE Documentation SHALL explain that all timestamps are stored as UTC+8 local time without timezone information
2. THE Documentation SHALL provide examples of correct timestamp handling in the application code
