package org.yhj.srim.service.crawl.parser;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.yhj.srim.client.dto.KisSpreadRow;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CrawlingError;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class KisSpreadParser {

    public List<KisSpreadRow> parseSpreadTable(String html) {
        Document doc = Jsoup.parse(html);
        Elements tables = doc.select("table");

        Element targetTable = null;
        int count = 0;
        for (Element table : tables) {
            if (table.text().contains("검색결과")) {
                count++;
                if (count == 1) {
                    targetTable = table;
                    break;
                }
            }
        }

        if (targetTable == null) {
            log.error("KIS 스프레드 표를 찾지 못했습니다.");
            throw new CustomException(CrawlingError.KIS_PARSE_FAILED);
        }

        List<KisSpreadRow> rows = new ArrayList<>();
        for (Element tr : targetTable.select("tr")) {
            Elements cells = tr.select("th, td");
            if (cells.isEmpty()) {
                continue;
            }

            String first = cells.get(0).text().trim();
            if (first.contains("검색결과") || first.contains("구분")) {
                continue;
            }

            String category = first;
            if (category.isBlank()) {
                continue;
            }

            BigDecimal m3 = getCellAsDecimal(cells, 1);
            BigDecimal m6 = getCellAsDecimal(cells, 2);
            BigDecimal m9 = getCellAsDecimal(cells, 3);
            BigDecimal y1 = getCellAsDecimal(cells, 4);
            BigDecimal y1_6 = getCellAsDecimal(cells, 5);
            BigDecimal y2 = getCellAsDecimal(cells, 6);
            BigDecimal y3 = getCellAsDecimal(cells, 7);
            BigDecimal y5 = getCellAsDecimal(cells, 8);

            rows.add(new KisSpreadRow(category, m3, m6, m9, y1, y1_6, y2, y3, y5));
        }

        return rows;
    }

    private BigDecimal getCellAsDecimal(Elements cells, int index) {
        if (index >= cells.size()) {
            return null;
        }
        return parseNumber(cells.get(index).text());
    }

    private BigDecimal parseNumber(String text) {
        if (text == null) {
            return null;
        }

        String cleaned = text
                .replace("%", "")
                .replace(",", "")
                .trim();

        if (cleaned.isEmpty()) {
            return null;
        }

        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            log.warn("숫자 파싱 실패 text='{}'", text, e);
            return null;
        }
    }
}
