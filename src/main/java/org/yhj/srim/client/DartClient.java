package org.yhj.srim.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.yhj.srim.client.dto.DartFsRow;
import org.yhj.srim.client.dto.DartShareStatusRow;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CrawlingErrorCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * dart Client
 * - 재무제표
 * - 주식총수 크롤링
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DartClient {


    private static final String DART_FS_URL = "https://opendart.fss.or.kr/api/fnlttSinglAcntAll.json";
    private static final String DART_SHARE_URL = "https://opendart.fss.or.kr/api/stockTotqySttus.json";

    @Value("${dart.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();


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
                log.debug("RAW NODE: {}", node.toString());
                log.debug("NODE account_id={}, account_nm={}",
                        getText(node, "account_id"),
                        getText(node, "account_nm"));
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

    private String getText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private int getInt(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.asText().isBlank()) return 0;
        return v.asInt();
    }
    private Integer getInteger(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.asText().isBlank()) return null;
        return v.asInt();
    }

    private BigDecimal getBigDecimal(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        String text = v.asText().replaceAll(",", "").trim();
        if (text.isEmpty() || "-".equals(text)) return null;
        return new BigDecimal(text);
    }

    private Long parseLong(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        String text = v.asText().replaceAll(",", "").trim();
        if (text.isEmpty() || "-".equals(text)) return null;
        return Long.parseLong(text);
    }
    /**
     * 특정 연도의 주식수(주식총수) 현황 조회.
     * @param corpCode : dart 코드
     * @param year : 조사 연도
     * @return
     */
    public List<DartShareStatusRow> fetchShareStatus(String corpCode, int year){
        return null;
    }

}
