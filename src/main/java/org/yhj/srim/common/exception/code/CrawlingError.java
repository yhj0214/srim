package org.yhj.srim.common.exception.code;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CrawlingError implements ErrorCode {

    DART_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "CRAWL-000", "DART 서버 요청에 실패했습니다."),
    KRX_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "CRAWL-001", "KRX 서버 요청에 실패했습니다."),
    JSON_PARSE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CRAWL-002", "응답 JSON 파싱에 실패하였습니다."),
    NAVER_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "CRAWL-003", "NAVER 서버 요청에 실패했습니다."),
    KIS_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "CRAWL-004", "KIS 서버 요청에 실패했습니다."),
    KIS_PARSE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CRAWL-005", "KIS 응답 파싱에 실패했습니다."),
    DART_DISCLOSURE_NOT_FOUND(HttpStatus.NOT_FOUND, "CRAWL-006", "조건에 맞는 DART 공시 메타데이터가 없습니다."),
    DART_XBRL_NOT_AVAILABLE(HttpStatus.NOT_FOUND, "CRAWL-007", "해당 공시에 XBRL 파일이 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    CrawlingError(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
