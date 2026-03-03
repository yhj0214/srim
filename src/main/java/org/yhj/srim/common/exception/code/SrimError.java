package org.yhj.srim.common.exception.code;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum SrimError implements ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "SRIM-001", "S-RIM 요청이 올바르지 않습니다."),
    INVALID_DISCOUNT_RATE(HttpStatus.BAD_REQUEST, "SRIM-002", "할인율(Ke)이 유효하지 않습니다."),
    INSUFFICIENT_ROE_DATA(HttpStatus.NOT_FOUND, "SRIM-003", "ROE 계산에 필요한 데이터가 부족합니다."),
    ROE_NOT_FOUND(HttpStatus.NOT_FOUND, "SRIM-004", "ROE 데이터가 없습니다."),
    EQUITY_OWNER_NOT_FOUND(HttpStatus.NOT_FOUND, "SRIM-005", "지배주주지분 데이터가 없습니다."),
    SHARES_OUTSTANDING_NOT_FOUND(HttpStatus.NOT_FOUND, "SRIM-006", "유통주식수 데이터가 없습니다."),
    BOND_YIELD_NOT_FOUND(HttpStatus.NOT_FOUND, "SRIM-007", "회사채 수익률 정보가 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    SrimError(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
