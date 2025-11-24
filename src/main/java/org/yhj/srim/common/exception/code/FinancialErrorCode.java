package org.yhj.srim.common.exception.code;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum FinancialErrorCode implements ErrorCode{

    FINANCIAL_DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "FIN-404", "해당 기간의 재무 데이터를 찾을 수 없습니다.")
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    FinancialErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
