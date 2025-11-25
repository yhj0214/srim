package org.yhj.srim.common.exception.code;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum StockErrorCode implements ErrorCode{

    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "STK-40401", "해당 종목을 찾을 수 없습니다."),
    DART_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "STK-40402", "해당 종목의 유효한 DART corp_code가 없습니다."),
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "STK-40403", "회사 정보 조회 중 오류가 발생하였습니다.")
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    StockErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
