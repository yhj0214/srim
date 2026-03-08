package org.yhj.srim.service.crawl.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.yhj.srim.client.dto.DartFsRow;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CrawlingError;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DartFinancialStatementParser {

    private final ObjectMapper objectMapper;

    public List<DartFsRow> parse(String json) {
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
}
