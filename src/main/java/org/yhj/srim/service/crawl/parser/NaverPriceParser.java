package org.yhj.srim.service.crawl.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.yhj.srim.client.dto.DaliyPrice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class NaverPriceParser {

    private static final DateTimeFormatter NAVER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    public List<DaliyPrice> parse(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return List.of();
        }

        Document document = Jsoup.parse(htmlContent);
        Elements rows = document.select("table.type2 tr");
        if (rows.isEmpty()) {
            return List.of();
        }

        List<DaliyPrice> result = new ArrayList<>();
        for (Element row : rows) {
            Elements tds = row.select("td");
            if (tds.size() < 7) {
                continue;
            }

            String dateText = tds.get(0).text().trim();
            String closeText = tds.get(1).text().trim();
            String openText = tds.get(3).text().trim();
            String highText = tds.get(4).text().trim();
            String lowText = tds.get(5).text().trim();
            String volText = tds.get(6).text().trim();

            if (dateText.isEmpty() || closeText.isEmpty()) {
                continue;
            }

            result.add(new DaliyPrice(
                    parseDate(dateText),
                    parseDecimal(openText),
                    parseDecimal(highText),
                    parseDecimal(lowText),
                    parseDecimal(closeText),
                    parseLong(volText)
            ));
        }

        return result;
    }

    private LocalDate parseDate(String text) {
        return LocalDate.parse(text, NAVER_DATE_FORMAT);
    }

    private BigDecimal parseDecimal(String text) {
        String cleaned = text.replace(",", "").trim();
        if (cleaned.isEmpty() || "-".equals(cleaned)) {
            return null;
        }
        return new BigDecimal(cleaned);
    }

    private Long parseLong(String text) {
        String cleaned = text.replace(",", "").trim();
        if (cleaned.isEmpty() || "-".equals(cleaned)) {
            return null;
        }
        return Long.parseLong(cleaned);
    }
}
