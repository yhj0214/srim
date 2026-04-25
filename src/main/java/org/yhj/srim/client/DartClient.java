package org.yhj.srim.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CrawlingError;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class DartClient {

    private static final String DART_DISCLOSURE_LIST_URL = "https://opendart.fss.or.kr/api/list.json";
    private static final String DART_FS_URL = "https://opendart.fss.or.kr/api/fnlttSinglAcntAll.json";
    private static final String DART_SHARE_URL = "https://opendart.fss.or.kr/api/stockTotqySttus.json";
    private static final String DART_XBRL_URL = "https://opendart.fss.or.kr/api/fnlttXbrl.xml";

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

    public String fetchAnnualFilingListBody(String corpCode, int fiscalYear) {
        int disclosureYear = fiscalYear + 1;
        String url = DART_DISCLOSURE_LIST_URL
                + "?crtfc_key=" + apiKey
                + "&corp_code=" + corpCode
                + "&bgn_de=" + disclosureYear + "0101"
                + "&end_de=" + disclosureYear + "1231"
                + "&pblntf_ty=A"
                + "&pblntf_detail_ty=A001"
                + "&page_no=1"
                + "&page_count=100";

        log.debug("DART 공시목록 조회 url : {}", url);
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getBody();
        } catch (Exception e) {
            throw new CustomException(CrawlingError.DART_REQUEST_FAILED);
        }
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

    public byte[] fetchFinancialStatementsXbrlArchive(String rceptNo, DartReportType reportType) {
        String url = buildFinancialStatementsXbrlUrl(rceptNo, reportType);

        log.debug("DART XBRL 조회 url : {}", url);

        ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
        byte[] body = response.getBody();
        if (!isZipArchive(body)) {
            MediaType contentType = response.getHeaders().getContentType();
            String bodyPrefix = previewBody(body);
            log.warn("DART XBRL 응답이 zip이 아닙니다. rceptNo={}, reprtCode={}, statusCode={}, contentType={}, contentLength={}, bodyPrefix={}",
                    rceptNo,
                    reportType.code(),
                    response.getStatusCode(),
                    contentType,
                    body == null ? 0 : body.length,
                    bodyPrefix);
            if (bodyPrefix.contains("<status>014</status>")) {
                throw new CustomException(
                        CrawlingError.DART_XBRL_NOT_AVAILABLE,
                        "rceptNo=" + rceptNo + ", reprtCode=" + reportType.code()
                );
            }
            throw new CustomException(
                    CrawlingError.DART_REQUEST_FAILED,
                    "rceptNo=" + rceptNo + ", reprtCode=" + reportType.code()
            );
        }
        return body;
    }

    public String buildFinancialStatementsXbrlUrl(String rceptNo, DartReportType reportType) {
        return DART_XBRL_URL
                + "?crtfc_key=" + apiKey
                + "&rcept_no=" + rceptNo
                + "&reprt_code=" + reportType.code();
    }

    private boolean isZipArchive(byte[] body) {
        return body != null
                && body.length >= 4
                && body[0] == 'P'
                && body[1] == 'K'
                && body[2] == 3
                && body[3] == 4;
    }

    private String previewBody(byte[] body) {
        if (body == null || body.length == 0) {
            return "<empty>";
        }

        int previewLength = Math.min(body.length, 200);
        return new String(body, 0, previewLength, StandardCharsets.UTF_8)
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
