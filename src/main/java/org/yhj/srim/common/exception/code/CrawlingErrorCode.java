package org.yhj.srim.common.exception.code;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CrawlingErrorCode implements ErrorCode{

    KRX_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "CRW-001", "DART 서버 요청에 실패했습니다."),
    JSON_PARSE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CRW-002", "크롤링 응답 JSON 파싱에 실패하였습니다.")
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    CrawlingErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
