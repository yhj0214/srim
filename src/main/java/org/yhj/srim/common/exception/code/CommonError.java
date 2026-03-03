package org.yhj.srim.common.exception.code;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CommonError implements ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON-001", "잘못된 요청입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-002", "서버 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    CommonError(HttpStatus status, String code, String message) {
        this.httpStatus = status;
        this.code = code;
        this.message = message;
    }
}
