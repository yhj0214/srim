package org.yhj.srim.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.yhj.srim.client.dto.DartFsRow;
import org.yhj.srim.client.dto.DartShareStatusRow;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CrawlingErrorCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * dart Client
 * - 재무제표
 * - 주식총수 크롤링
 */
@Component
@Slf4j
public class DartClient {


    private static final String DART_FS_URL = "https://opendart.fss.or.kr/api/fnlttSinglAcntAll.json";
    private static final String DART_SHARE_URL = "https://opendart.fss.or.kr/api/stockTotqySttus.json";

    private final String apiKey;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DartClient(@Value("${dart.api.key}") String apiKey,
                      @Qualifier("dartRestTemplate") RestTemplate restTemplate,
                      ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }



    /**
     * 사업보고서 기준 연간 재무제표 조회
     * @param corpCode : dart 코드
     * @param year : 조사 연도
     * @return
     */
    public List<DartFsRow> fetchAnnualFinancialStatements(String corpCode, int year){
        String url = DART_FS_URL
                + "?crtfc_key=" + apiKey
                + "&corp_code=" + corpCode
                + "&bsns_year=" + year
                + "&reprt_code=11011"
                + "&fs_div=CFS"; // CFS-연결재무제표, OFS

        log.debug("사업보고서 조회 url : {}", url);

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        String body = response.getBody();
        log.debug(body);
        return parseFsResponse(body);
    }

    private List<DartFsRow> parseFsResponse(String json) {
        List<DartFsRow> result = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode listNode = root.get("list");
            if (listNode == null || !listNode.isArray()) {
                return result;
            }
            for (JsonNode node : listNode) {
                DartFsRow row = new DartFsRow();

                row.setRceptNo(getText(node, "rcept_no"));
                row.setReprtCode(getText(node, "reprt_code"));
                row.setBsnsYear(getInt(node, "bsns_year"));
                row.setFsDiv(getText(node, "fs_div"));
                row.setRceptDt(getText(node, "rcept_dt"));

                row.setSjDiv(getText(node, "sj_div"));
                row.setSjNm(getText(node, "sj_nm"));
                row.setAccountId(getText(node, "account_id"));
                row.setAccountNm(getText(node, "account_nm"));
                row.setAccountDetail(getText(node, "account_detail"));
                row.setOrd(getInteger(node, "ord"));

                row.setThstrmNm(getText(node, "thstrm_nm"));
                row.setThstrmAmount(getBigDecimal(node, "thstrm_amount"));
                row.setThstrmAddAmount(getBigDecimal(node, "thstrm_add_amount"));

                row.setFrmtrmNm(getText(node, "frmtrm_nm"));
                row.setFrmtrmAmount(getBigDecimal(node, "frmtrm_amount"));

                row.setBfefrmtrmNm(getText(node, "bfefrmtrm_nm"));
                row.setBfefrmtrmAmount(getBigDecimal(node, "bfefrmtrm_amount"));

                row.setCurrency(getText(node, "currency"));

                row.setRawJson(node.toString());

                result.add(row);
            }

        } catch (Exception e) {
            throw new CustomException(CrawlingErrorCode.JSON_PARSE_FAILED);
        }

        return result;
    }

    /**
     * 특정 연도의 주식수(주식총수) 현황 조회.
     * @param corpCode : dart 코드
     * @param year : 조사 연도
     * @return
     */
    public List<DartShareStatusRow> fetchShareStatus(String corpCode, int year){
        String url = DART_SHARE_URL
                + "?crtfc_key=" + apiKey
                + "&corp_code=" + corpCode
                + "&bsns_year=" + year
                + "&reprt_code=11011";
        log.debug("DART 주식수 현황 조회 url : {}", url);
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        String body = response.getBody();
        log.debug("주식현황 파싱 전 data : {}", body);
        return parseShareResponse(body);
    }
    private List<DartShareStatusRow> parseShareResponse(String json) {
        List<DartShareStatusRow> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode listNode = root.get("list");
            if (listNode == null || !listNode.isArray()) {
                log.warn("DART 주식수 응답에 list 필드가 없거나 배열이 아닙니다. json={}", json);
                return result;
            }

            log.debug("RAW NODE: {}", listNode.toString());

            for (JsonNode node : listNode) {

                String se = getText(node, "se");
                if ("비고".equals(se)) {
                    log.debug("비고 행 스킵: {}", node.toString());
                    continue;
                }

                DartShareStatusRow row = new DartShareStatusRow();

                // 공시 메타 정보
                row.setRceptNo(getText(node, "rcept_no"));
                row.setCorpCls(getText(node, "corp_cls"));
                row.setCorpCode(getText(node, "corp_code"));
                row.setCorpName(getText(node, "corp_name"));

                row.setSe(se);

                // 사업연도
                Integer bsnsYear = getInteger(node, "bsns_year");
                row.setBsnsYear(bsnsYear);

                // 결산일 (2023-12-31)
                String stlmDtStr = getText(node, "stlm_dt");
                LocalDate stlmDt = null;
                if (stlmDtStr != null && !stlmDtStr.isBlank()) {
                    stlmDt = LocalDate.parse(stlmDtStr.trim());
                }
                row.setStlmDt(stlmDt);

                row.setIsuStockTotqy(parseLong(node, "isu_stock_totqy"));
                row.setNowToIsuStockTotqy(parseLong(node, "now_to_isu_stock_totqy"));
                row.setNowToDcrsStockTotqy(parseLong(node, "now_to_dcrs_stock_totqy"));
                row.setRedc(parseLong(node, "redc"));
                row.setProfitIncnr(parseLong(node, "profit_incnr"));
                row.setRdmstkRepy(parseLong(node, "rdmstk_repy"));
                row.setEtc(parseLong(node, "etc"));
                row.setIstcTotqy(parseLong(node, "istc_totqy"));
                row.setTesstkCo(parseLong(node, "tesstk_co"));

                Long distb = parseLong(node, "distb_stock_co");

                // 없으면 발행주식 - 자기주식으로 계산
                if (distb == null && row.getIstcTotqy() != null && row.getTesstkCo() != null) {
                    distb = row.getIstcTotqy() - row.getTesstkCo();
                }
                row.setDistbStockCo(distb);

                row.setRawJson(node.toString());

                result.add(row);
            }
        } catch (Exception e) {
            log.error("DART 주식수 응답 파싱 실패. json={}", json, e);
            throw new CustomException(CrawlingErrorCode.JSON_PARSE_FAILED);
        }
        return result;
    }


    private boolean isNullLike(String s) {
        if (s == null) return true;
        String t = s.trim();
        if (t.isEmpty()) return true;

        // [ADD] null/대시류/NA 류 방어
        String upper = t.toUpperCase();
        return "-".equals(t)
                || "—".equals(t)
                || "–".equals(t)
                || "NULL".equals(upper)
                || "N/A".equals(upper);
    }

    private String normalizeNumberText(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (isNullLike(t)) return null;

        t = t.replace(",", "");

        // 괄호 음수: (123) -> -123
        if (t.startsWith("(") && t.endsWith(")")) {
            t = "-" + t.substring(1, t.length() - 1).trim();
        }

        return t;
    }


    private String getText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;

        String t = v.asText();
        return isNullLike(t) ? null : t;
    }

    private int getInt(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return 0;

        String t = normalizeNumberText(v.asText());
        if (t == null) return 0;
        return Integer.parseInt(t);
    }
    private Integer getInteger(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;

        String t = normalizeNumberText(v.asText());
        if (t == null) return null;
        return Integer.valueOf(t);
    }

    private BigDecimal getBigDecimal(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;

        String t = normalizeNumberText(v.asText());
        if (t == null) return null;
        return new BigDecimal(t);
    }

    private Long parseLong(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        String t = normalizeNumberText(v.asText());
        if (t == null) return null;
        return Long.parseLong(t);
    }
}
