package org.yhj.srim.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class DartClient {

    private static final String DART_FS_URL = "https://opendart.fss.or.kr/api/fnlttSinglAcntAll.json";
    private static final String DART_SHARE_URL = "https://opendart.fss.or.kr/api/stockTotqySttus.json";

    private final String apiKey;
    private final RestTemplate restTemplate;

    public DartClient(@Value("${dart.api.key}") String apiKey,
                      @Qualifier("dartRestTemplate") RestTemplate restTemplate) {
        this.apiKey = apiKey;
        this.restTemplate = restTemplate;
    }

    public String fetchFinancialStatementsBody(String corpCode, int year, DartReportType reportType) {
        String url = DART_FS_URL
                + "?crtfc_key=" + apiKey
                + "&corp_code=" + corpCode
                + "&bsns_year=" + year
                + "&reprt_code=" + reportType.code()
                + "&fs_div=CFS";

        log.debug("DART 재무제표 조회 url : {}", url);

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        return response.getBody();
    }

    public String fetchShareStatusBody(String corpCode, int year) {
        String url = DART_SHARE_URL
                + "?crtfc_key=" + apiKey
                + "&corp_code=" + corpCode
                + "&bsns_year=" + year
                + "&reprt_code=11011";

        log.debug("DART 주식수 현황 조회 url : {}", url);

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        return response.getBody();
    }
}
