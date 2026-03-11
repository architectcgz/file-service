package com.platform.filegateway.common.exception;

import com.platform.filegateway.common.result.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GatewayException.class)
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> handleGatewayException(GatewayException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatus().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        if (status.is5xxServerError()) {
            log.error("Gateway request failed: {}", ex.getMessage(), ex);
        } else {
            log.warn("Gateway request rejected: {}", ex.getMessage());
        }

        return org.springframework.http.ResponseEntity.status(status)
                .body(ApiResponse.error(status.value(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleUnexpectedException(Exception ex) {
        log.error("Unexpected gateway error", ex);
        return ApiResponse.error(500, "Internal server error");
    }
}
