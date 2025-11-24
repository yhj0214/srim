package org.yhj.srim.client;

import org.springframework.beans.factory.annotation.Value;
import org.yhj.srim.client.dto.DartFsRow;
import org.yhj.srim.client.dto.DartShareStatusRow;

import java.util.List;

/**
 * dart Client
 * - 재무제표
 * - 주식총수 크롤링
 */
public class DartClient {


    private static final String DART_FS_URL = "https://opendart.fss.or.kr/api/fnlttSinglAcnt.json";
    private static final String DART_SHARE_URL = "https://opendart.fss.or.kr/api/stockTotqySttus.json";

    @Value("${dart.api.key}")
    private String apiKey;


    /**
     * 사업보고서 기준 연간 재무제표 조회
     * @param corpCode : dart 코드
     * @param year : 조사 연도
     * @return
     */
    List<DartFsRow> fetchAnnualFinancialStatements(String corpCode, int year){
        return null;
    }

    /**
     * 특정 연도의 주식수(주식총수) 현황 조회.
     * @param corpCode : dart 코드
     * @param year : 조사 연도
     * @return
     */
    List<DartShareStatusRow> fetchShareStatus(String corpCode, int year){
        return null;
    }

}
