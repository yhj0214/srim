package org.yhj.srim.common.exception.code;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum StockError implements ErrorCode {

    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "STOCK-001", "해당 종목을 찾을 수 없습니다."),
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "STOCK-002", "회사 정보 조회에 실패했습니다."),
    DART_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "STOCK-003", "DART 코드가 없습니다."),
    DART_CORP_CODE_INVALID(HttpStatus.BAD_REQUEST, "STOCK-004", "유효하지 않은 DART 코드입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    StockError(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
