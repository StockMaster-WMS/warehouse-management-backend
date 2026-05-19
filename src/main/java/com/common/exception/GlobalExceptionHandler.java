package com.common.exception;

import com.common.api.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Object>> handleAppException(AppException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        String message = ex.getMessage() != null ? ex.getMessage() : errorCode.getMessage();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(message, errorCode.getCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = validationMessage(ex);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(message, "VALIDATION_ERROR"));
    }

    private String validationMessage(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        if (fieldError != null && fieldError.getDefaultMessage() != null) {
            return fieldError.getDefaultMessage();
        }

        ObjectError globalError = ex.getBindingResult().getGlobalError();
        if (globalError != null && globalError.getDefaultMessage() != null) {
            return globalError.getDefaultMessage();
        }

        return ErrorCode.VALIDATION_ERROR.getMessage();
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ex.getMessage(), "VALIDATION_ERROR"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(Exception ex) {
        String errorId = UUID.randomUUID().toString();
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        log.error("Unexpected error id={} rootCause={} message={}",
                errorId, root.getClass().getName(), root.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Lỗi hệ thống. Mã lỗi: " + errorId, "INTERNAL_SERVER_ERROR"));
    }
}
