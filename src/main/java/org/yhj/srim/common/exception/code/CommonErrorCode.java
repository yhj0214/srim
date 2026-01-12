package org.yhj.srim.common.exception.code;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CommonErrorCode implements ErrorCode{

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COM-001", "잘못된 요청입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COM-002", "회사채 수익률 정보가 없습니다."),
    NOT_FOUND2(HttpStatus.NOT_FOUND, "COM-003", "유통주식수 데이터가 없습니다."),
    NOT_FOUND3(HttpStatus.NOT_FOUND, "COM-004", "지배주주지분데이터가 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    CommonErrorCode(HttpStatus status, String code, String message) {
        this.httpStatus = status;
        this.code = code;
        this.message = message;
    }
}
