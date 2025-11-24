package org.yhj.srim.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.yhj.srim.common.exception.code.CommonErrorCode;
import org.yhj.srim.common.exception.code.ErrorCode;
import org.yhj.srim.controller.dto.ApiResponse;

import static org.yhj.srim.controller.dto.ApiResponse.error;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {

        ErrorCode errorCode = e.getErrorCode();

        log.warn("CustomException 발생 - code: {}, message: {}",
                errorCode.getCode(), e.getMessage());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(error(errorCode.getCode(), errorCode.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("unhandled exception 발생", e);
        ErrorCode errorCode = CommonErrorCode.INVALID_INPUT;

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(error(errorCode.getCode(), errorCode.getMessage()));
    }
}
