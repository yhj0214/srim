package org.yhj.srim.service.crawl.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yhj.srim.client.dto.DartShareStatusRow;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CrawlingError;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DartShareStatusParser {

    private final ObjectMapper objectMapper;

    public List<DartShareStatusRow> parse(String json) {
        List<DartShareStatusRow> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode listNode = root.get("list");
            if (listNode == null || !listNode.isArray()) {
                log.warn("DART 주식수 응답에 list 필드가 없거나 배열이 아닙니다. json={}", json);
                return result;
            }

            for (JsonNode node : listNode) {
                String se = getText(node, "se");
                if ("비고".equals(se)) {
                    continue;
                }

                DartShareStatusRow row = new DartShareStatusRow();
                row.setRceptNo(getText(node, "rcept_no"));
                row.setCorpCls(getText(node, "corp_cls"));
                row.setCorpCode(getText(node, "corp_code"));
                row.setCorpName(getText(node, "corp_name"));
                row.setSe(se);
                row.setBsnsYear(getInteger(node, "bsns_year"));

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
                if (distb == null && row.getIstcTotqy() != null && row.getTesstkCo() != null) {
                    distb = row.getIstcTotqy() - row.getTesstkCo();
                }
                row.setDistbStockCo(distb);
                row.setRawJson(node.toString());

                result.add(row);
            }
        } catch (Exception e) {
            log.error("DART 주식수 응답 파싱 실패. json={}", json, e);
            throw new CustomException(CrawlingError.JSON_PARSE_FAILED);
        }
        return result;
    }

    private boolean isNullLike(String s) {
        if (s == null) return true;
        String t = s.trim();
        if (t.isEmpty()) return true;

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

    private Integer getInteger(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;

        String t = normalizeNumberText(v.asText());
        if (t == null) return null;
        return Integer.valueOf(t);
    }

    private Long parseLong(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        String t = normalizeNumberText(v.asText());
        if (t == null) return null;
        return Long.parseLong(t);
    }
}
