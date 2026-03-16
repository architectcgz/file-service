package com.platform.fileservice.core.web.exception;

import com.platform.fileservice.core.domain.exception.FileAccessDeniedException;
import com.platform.fileservice.core.domain.exception.FileAccessMutationException;
import com.platform.fileservice.core.domain.exception.FileAssetNotFoundException;
import com.platform.fileservice.core.domain.exception.UploadSessionAccessDeniedException;
import com.platform.fileservice.core.domain.exception.UploadSessionInvalidRequestException;
import com.platform.fileservice.core.domain.exception.UploadSessionMutationException;
import com.platform.fileservice.core.domain.exception.UploadSessionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * V1 facade 的最小异常映射。
 */
@RestControllerAdvice
public class V1GlobalExceptionHandler {

    @ExceptionHandler(FileAssetNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(FileAssetNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "message", ex.getMessage(),
                        "status", HttpStatus.NOT_FOUND.value()
                ));
    }

    @ExceptionHandler(FileAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(FileAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "message", ex.getMessage(),
                        "status", HttpStatus.FORBIDDEN.value()
                ));
    }

    @ExceptionHandler(UploadSessionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUploadSessionNotFound(UploadSessionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "message", ex.getMessage(),
                        "status", HttpStatus.NOT_FOUND.value()
                ));
    }

    @ExceptionHandler(UploadSessionAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleUploadSessionAccessDenied(UploadSessionAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "message", ex.getMessage(),
                        "status", HttpStatus.FORBIDDEN.value()
                ));
    }

    @ExceptionHandler(UploadSessionInvalidRequestException.class)
    public ResponseEntity<Map<String, Object>> handleUploadSessionInvalidRequest(UploadSessionInvalidRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "message", ex.getMessage(),
                        "status", HttpStatus.BAD_REQUEST.value()
                ));
    }

    @ExceptionHandler(FileAccessMutationException.class)
    public ResponseEntity<Map<String, Object>> handleMutationFailure(FileAccessMutationException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "message", ex.getMessage(),
                        "status", HttpStatus.INTERNAL_SERVER_ERROR.value()
                ));
    }

    @ExceptionHandler(UploadSessionMutationException.class)
    public ResponseEntity<Map<String, Object>> handleUploadSessionMutationFailure(UploadSessionMutationException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "message", ex.getMessage(),
                        "status", HttpStatus.INTERNAL_SERVER_ERROR.value()
                ));
    }

}
