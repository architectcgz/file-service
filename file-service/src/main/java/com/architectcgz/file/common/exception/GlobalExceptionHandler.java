package com.architectcgz.file.common.exception;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.result.ApiResponse;
import com.platform.fileservice.core.domain.exception.FileAssetNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Global exception handler for REST controllers
 * Converts exceptions to appropriate HTTP responses
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Handle TenantNotFoundException
     * Returns 404 Not Found
     */
    @ExceptionHandler(TenantNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleTenantNotFoundException(TenantNotFoundException ex) {
        log.warn("Tenant not found: {}", ex.getMessage());
        return ApiResponse.error(404, ex.getErrorCode(), ex.getMessage());
    }
    
    /**
     * Handle validation errors from @Valid annotation
     * Returns 400 Bad Request with detailed field errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Validation error: {}", errorMessage);
        return ApiResponse.error(
                400,
                FileServiceErrorCodes.VALIDATION_ERROR,
                String.format(FileServiceErrorMessages.VALIDATION_FAILED, errorMessage)
        );
    }
    
    /**
     * Handle missing required headers
     * Returns 400 Bad Request
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMissingHeaderException(MissingRequestHeaderException ex) {
        log.warn("Missing required header: {}", ex.getHeaderName());
        return ApiResponse.error(
                400,
                FileServiceErrorCodes.MISSING_REQUEST_HEADER,
                String.format(FileServiceErrorMessages.MISSING_REQUEST_HEADER, ex.getHeaderName())
        );
    }
    
    /**
     * Handle TenantSuspendedException
     * Returns 403 Forbidden
     */
    @ExceptionHandler(TenantSuspendedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleTenantSuspendedException(TenantSuspendedException ex) {
        log.warn("Tenant suspended: {}", ex.getMessage());
        return ApiResponse.error(403, ex.getErrorCode(), ex.getMessage());
    }
    
    /**
     * Handle QuotaExceededException
     * Returns 413 Payload Too Large
     */
    @ExceptionHandler(QuotaExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiResponse<Void> handleQuotaExceededException(QuotaExceededException ex) {
        log.warn("Quota exceeded: {}", ex.getMessage());
        return ApiResponse.error(413, ex.getErrorCode(), ex.getMessage());
    }
    
    /**
     * Handle FileTooLargeException
     * Returns 413 Payload Too Large
     */
    @ExceptionHandler(FileTooLargeException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiResponse<Void> handleFileTooLargeException(FileTooLargeException ex) {
        log.warn("File too large: {}", ex.getMessage());
        return ApiResponse.error(413, ex.getErrorCode(), ex.getMessage());
    }
    
    /**
     * Handle AccessDeniedException
     * Returns 403 Forbidden
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ApiResponse.error(403, ex.getErrorCode(), ex.getMessage());
    }
    
    /**
     * Handle FileNotFoundException
     * Returns 404 Not Found
     */
    @ExceptionHandler(FileNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleFileNotFoundException(FileNotFoundException ex) {
        log.warn("File not found: {}", ex.getMessage());
        return ApiResponse.error(404, ex.getErrorCode(), ex.getMessage());
    }

    /**
     * Handle file-core not found exceptions exposed by v1 facade controllers.
     * Returns 404 Not Found instead of falling back to the generic 500 handler.
     */
    @ExceptionHandler(FileAssetNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleCoreFileNotFoundException(FileAssetNotFoundException ex) {
        log.warn("Core file not found: {}", ex.getMessage());
        return ApiResponse.error(404, FileServiceErrorCodes.FILE_NOT_FOUND, ex.getMessage());
    }
    
    /**
     * Handle BusinessException
     * Returns 400 Bad Request
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBusinessException(BusinessException ex) {
        log.warn("Business exception: {}", ex.getMessage());
        return ApiResponse.error(400, ex.getErrorCode(), ex.getMessage());
    }
    
    /**
     * Handle generic exceptions
     * Returns 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception ex) {
        log.error("Unexpected error", ex);
        return ApiResponse.error(
                500,
                FileServiceErrorCodes.INTERNAL_SERVER_ERROR,
                String.format(FileServiceErrorMessages.INTERNAL_SERVER_ERROR, ex.getMessage())
        );
    }
}
