package org.yhj.srim.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.yhj.srim.client.dto.KisSpreadRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.util.NumberUtils.parseNumber;

@Component
@Slf4j
@RequiredArgsConstructor
public class KisSpreadClient {

    private final WebClient kisWebClient;
    private static final DateTimeFormatter KIS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    public List<KisSpreadRow> fetchSpreadRows(LocalDate date){
        String html = fetchSpreadHtml(date);
        return parseSpreadTable(html);
    }

    private List<KisSpreadRow> parseSpreadTable(String html) {
        Document doc = Jsoup.parse(html);

        Elements tables = doc.select("table");

        Element targetTable = null;

        int count = 0;
        for (Element table : tables) {
            if (table.text().contains("검색결과")) {
                count++;
                if (count == 1) {          // 1-금리, 2-스프레드 테이블
                    targetTable = table;
                    break;
                }
            }
        }
        if (targetTable == null) {
            log.warn("KIS 스프레드 표를 찾지 못했습니다.");
            return null;
        }

        List<KisSpreadRow> rows = new ArrayList<>();

        // tr 순회하면서 header(검색결과 ...)는 스킵하고 나머지만 파싱
        for (Element tr : targetTable.select("tr")) {
            Elements cells = tr.select("th, td");
            if (cells.isEmpty()) {
                continue;
            }

            String first = cells.get(0).text().trim();

            // 헤더 라인(검색결과 구분 3월 6월...) 스킵
            if (first.contains("검색결과") || first.contains("구분")) {
                continue;
            }

            // 내용 행: 국고채, AAA, AA+ ...
            String category = first;
            if (category.isBlank()) {
                continue;
            }

            BigDecimal m3   = getCellAsDecimal(cells, 1);
            BigDecimal m6   = getCellAsDecimal(cells, 2);
            BigDecimal m9   = getCellAsDecimal(cells, 3);
            BigDecimal y1   = getCellAsDecimal(cells, 4);
            BigDecimal y1_6 = getCellAsDecimal(cells, 5);
            BigDecimal y2   = getCellAsDecimal(cells, 6);
            BigDecimal y3   = getCellAsDecimal(cells, 7);
            BigDecimal y5   = getCellAsDecimal(cells, 8);

            rows.add(new KisSpreadRow(
                    category, m3, m6, m9, y1, y1_6, y2, y3, y5
            ));
        }

        return rows;
    }

    private BigDecimal getCellAsDecimal(Elements cells, int index) {
        if (index >= cells.size()) {
            return null;
        }
        String text = cells.get(index).text();
        return parseNumber(text);
    }

    private BigDecimal parseNumber(String text) {
        if (text == null) return null;
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
    public String fetchSpreadHtml(LocalDate date) {
        String startDt = date.format(KIS_DATE_FORMAT);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("startDt", startDt);

        log.info("KIS 스프레드 조회 호출 startDt={}", startDt);

        return kisWebClient.post()
                .uri("/ratingsStatistics/statics_spread.do")
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
