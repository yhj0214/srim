package org.yhj.srim.common.exception.code;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CrawlingError implements ErrorCode {

    KRX_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "CRAWL-001", "KRX 서버 요청에 실패했습니다."),
    JSON_PARSE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CRAWL-002", "응답 JSON 파싱에 실패하였습니다."),
    NAVER_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "CRAWL-003", "NAVER 서버 요청에 실패했습니다."),
    KIS_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "CRAWL-004", "KIS 서버 요청에 실패했습니다."),
    KIS_PARSE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CRAWL-005", "KIS 응답 파싱에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    CrawlingError(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
