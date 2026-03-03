package org.yhj.srim.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.yhj.srim.common.exception.code.ErrorCode;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiError {
    private String code;
    private String message;
    private String detail;
    private String path;
    private String timestamp;

    public static ApiError from(ErrorCode errorCode, String detail, String path) {
        return ApiError.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .detail(detail)
                .path(path)
                .timestamp(OffsetDateTime.now().toString())
                .build();
    }
}
