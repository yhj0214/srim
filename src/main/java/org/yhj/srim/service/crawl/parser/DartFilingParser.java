package org.yhj.srim.service.crawl.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.yhj.srim.client.dto.DartFilingRow;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CrawlingError;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DartFilingParser {

    private final ObjectMapper objectMapper;

    public List<DartFilingRow> parse(String json) {
        List<DartFilingRow> result = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode listNode = root.get("list");
            if (listNode == null || !listNode.isArray()) {
                return result;
            }

            for (JsonNode node : listNode) {
                DartFilingRow row = new DartFilingRow();
                row.setCorpCode(getText(node, "corp_code"));
                row.setStockCode(getText(node, "stock_code"));
                row.setReportNm(getText(node, "report_nm"));
                row.setRceptNo(getText(node, "rcept_no"));
                row.setRceptDt(getText(node, "rcept_dt"));
                row.setRm(getText(node, "rm"));
                row.setRawJson(node.toString());
                result.add(row);
            }
        } catch (Exception e) {
            throw new CustomException(CrawlingError.JSON_PARSE_FAILED);
        }

        return result;
    }

    private String getText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
