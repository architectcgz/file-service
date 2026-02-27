package com.architectcgz.file.common.exception;

import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 属性测试：异常消息清晰性
 * Feature: file-service-optimization, Property 36: 异常消息清晰性
 * 验证需求：15.6
 */
class ExceptionMessagePropertyTest {

    @Property(tries = 100)
    @Label("TenantNotFoundException should contain clear error message with tenant ID")
    void tenantNotFoundExceptionShouldContainTenantId(@ForAll("tenantIds") String tenantId) {
        // Given: A tenant ID
        
        // When: Creating TenantNotFoundException
        TenantNotFoundException exception = new TenantNotFoundException(tenantId);
        
        // Then: Exception should have correct error code
        assertThat(exception.getCode()).isEqualTo("TENANT_NOT_FOUND");
        
        // And: Message should contain the tenant ID
        assertThat(exception.getMessage())
            .isNotNull()
            .isNotEmpty()
            .contains(tenantId)
            .containsIgnoringCase("tenant")
            .containsIgnoringCase("not found");
    }

    @Property(tries = 100)
    @Label("TenantSuspendedException should contain clear error message with tenant ID")
    void tenantSuspendedExceptionShouldContainTenantId(@ForAll("tenantIds") String tenantId) {
        // Given: A tenant ID
        
        // When: Creating TenantSuspendedException
        TenantSuspendedException exception = new TenantSuspendedException(tenantId);
        
        // Then: Exception should have correct error code
        assertThat(exception.getCode()).isEqualTo("TENANT_SUSPENDED");
        
        // And: Message should contain the tenant ID
        assertThat(exception.getMessage())
            .isNotNull()
            .isNotEmpty()
            .contains(tenantId)
            .containsIgnoringCase("tenant")
            .containsIgnoringCase("suspended");
    }

    @Property(tries = 100)
    @Label("QuotaExceededException should contain clear error message with quota details")
    void quotaExceededExceptionShouldContainQuotaDetails(
        @ForAll("quotaTypes") String quotaType,
        @ForAll("positiveNumbers") long current,
        @ForAll("positiveNumbers") long limit
    ) {
        // Given: Quota type and values where current exceeds limit
        Assume.that(current > limit);
        
        // When: Creating QuotaExceededException with details
        QuotaExceededException exception = new QuotaExceededException(quotaType, current, limit);
        
        // Then: Exception should have correct error code
        assertThat(exception.getCode()).isEqualTo("QUOTA_EXCEEDED");
        
        // And: Message should contain quota type, current value, and limit
        assertThat(exception.getMessage())
            .isNotNull()
            .isNotEmpty()
            .contains(quotaType)
            .contains(String.valueOf(current))
            .contains(String.valueOf(limit))
            .containsIgnoringCase("quota")
            .containsIgnoringCase("exceeded");
    }

    @Property(tries = 100)
    @Label("FileTooLargeException should contain clear error message with file size details")
    void fileTooLargeExceptionShouldContainSizeDetails(
        @ForAll("positiveNumbers") long fileSize,
        @ForAll("positiveNumbers") long maxSize
    ) {
        // Given: File size that exceeds max size
        Assume.that(fileSize > maxSize);
        
        // When: Creating FileTooLargeException
        FileTooLargeException exception = new FileTooLargeException(fileSize, maxSize);
        
        // Then: Exception should have correct error code
        assertThat(exception.getCode()).isEqualTo("FILE_TOO_LARGE");
        
        // And: Message should contain file size and max size
        assertThat(exception.getMessage())
            .isNotNull()
            .isNotEmpty()
            .contains(String.valueOf(fileSize))
            .contains(String.valueOf(maxSize))
            .containsIgnoringCase("file")
            .containsIgnoringCase("size")
            .containsIgnoringCase("exceeds");
    }

    @Property(tries = 100)
    @Label("AccessDeniedException with String userId should contain clear error message")
    void accessDeniedExceptionWithStringUserIdShouldContainDetails(
        @ForAll("fileIds") String fileId,
        @ForAll("userIds") String userId
    ) {
        // Given: A file ID and user ID
        
        // When: Creating AccessDeniedException
        AccessDeniedException exception = new AccessDeniedException(fileId, userId);
        
        // Then: Exception should have correct error code
        assertThat(exception.getCode()).isEqualTo("ACCESS_DENIED");
        
        // And: Message should contain file ID and user ID
        assertThat(exception.getMessage())
            .isNotNull()
            .isNotEmpty()
            .contains(fileId)
            .contains(userId)
            .containsIgnoringCase("user")
            .containsIgnoringCase("permission")
            .containsIgnoringCase("file");
    }

    @Property(tries = 100)
    @Label("AccessDeniedException with Long userId should contain clear error message")
    void accessDeniedExceptionWithLongUserIdShouldContainDetails(
        @ForAll("fileIds") String fileId,
        @ForAll("positiveLongs") Long userId
    ) {
        // Given: A file ID and user ID
        
        // When: Creating AccessDeniedException
        AccessDeniedException exception = new AccessDeniedException(fileId, userId);
        
        // Then: Exception should have correct error code
        assertThat(exception.getCode()).isEqualTo("ACCESS_DENIED");
        
        // And: Message should contain file ID and user ID
        assertThat(exception.getMessage())
            .isNotNull()
            .isNotEmpty()
            .contains(fileId)
            .contains(String.valueOf(userId))
            .containsIgnoringCase("user")
            .containsIgnoringCase("permission")
            .containsIgnoringCase("file");
    }

    @Property(tries = 100)
    @Label("All exceptions should have non-null and non-empty error codes")
    void allExceptionsShouldHaveValidErrorCodes(@ForAll("tenantIds") String tenantId) {
        // Given: Various exception types
        
        // When: Creating different exceptions
        TenantNotFoundException ex1 = new TenantNotFoundException(tenantId);
        TenantSuspendedException ex2 = new TenantSuspendedException(tenantId);
        QuotaExceededException ex3 = new QuotaExceededException("Storage", 100L, 50L);
        FileTooLargeException ex4 = new FileTooLargeException(100L, 50L);
        AccessDeniedException ex5 = new AccessDeniedException("file1", "user1");
        
        // Then: All should have non-null and non-empty error codes
        assertThat(ex1.getCode()).isNotNull().isNotEmpty();
        assertThat(ex2.getCode()).isNotNull().isNotEmpty();
        assertThat(ex3.getCode()).isNotNull().isNotEmpty();
        assertThat(ex4.getCode()).isNotNull().isNotEmpty();
        assertThat(ex5.getCode()).isNotNull().isNotEmpty();
    }

    // Providers for generating test data
    
    @Provide
    Arbitrary<String> tenantIds() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(1)
            .ofMaxLength(32);
    }

    @Provide
    Arbitrary<String> fileIds() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(1)
            .ofMaxLength(64);
    }

    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(1)
            .ofMaxLength(64);
    }

    @Provide
    Arbitrary<Long> positiveLongs() {
        return Arbitraries.longs()
            .between(1L, Long.MAX_VALUE);
    }

    @Provide
    Arbitrary<Long> positiveNumbers() {
        return Arbitraries.longs()
            .between(1L, 1_000_000_000L);
    }

    @Provide
    Arbitrary<String> quotaTypes() {
        return Arbitraries.of("Storage", "FileCount", "SingleFileSize");
    }
}
