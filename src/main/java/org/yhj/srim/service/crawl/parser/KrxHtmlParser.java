package org.yhj.srim.service.crawl.parser;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.yhj.srim.service.crawl.dto.StockCodeDraft;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class KrxHtmlParser implements KrxParser {

    @Override
    public boolean supports(String content) {
        if (content == null) return false;
        String t = content.trim();
        return t.startsWith("<") || t.contains("<html") || t.contains("<table");
    }

    @Override
    public List<StockCodeDraft> parse(String htmlContent, String defaultMarket) {
        List<StockCodeDraft> stockCodes = new ArrayList<>();

        try {
            Document doc = Jsoup.parse(htmlContent);
            Elements rows = doc.select("tr");

            log.info("HTML 테이블 행 수: {}", rows.size());

            boolean isFirstRow = true;
            for (Element row : rows) {
                if (isFirstRow) {
                    isFirstRow = false;
                    log.info("헤더 행: {}", row.text());
                    continue;
                }

                Elements cols = row.select("td");
                if (cols.isEmpty()) {
                    continue;
                }

                try {
                    if (cols.size() < 4) {
                        log.debug("컬럼 수 부족: {}", cols.size());
                        continue;
                    }

                    String companyName = cols.get(0).text().trim();
                    String marketFromData = cols.get(1).text().trim();
                    String tickerKrx = normalizeTicker(cols.get(2).text().trim());
                    String industry = cols.size() > 3 ? cols.get(3).text().trim() : null;

                    if (tickerKrx.isEmpty() || companyName.isEmpty()) {
                        log.debug("필수 데이터 누락: 회사명={}, 티커={}", companyName, tickerKrx);
                        continue;
                    }

                    LocalDate listingDate = cols.size() > 5 ? parseDate(cols.get(5).text().trim()) : null;
                    Integer fiscalMonth = cols.size() > 6 ? parseMonth(cols.get(6).text().trim()) : null;
                    String homepage = cols.size() > 8 ? cols.get(8).text().trim() : null;
                    String region = cols.size() > 9 ? cols.get(9).text().trim() : null;

                    String market = defaultMarket;
                    if (marketFromData != null && !marketFromData.isEmpty()) {
                        if (marketFromData.contains("코스피") || marketFromData.contains("KOSPI")) {
                            market = "KOSPI";
                        } else if (marketFromData.contains("코스닥") || marketFromData.contains("KOSDAQ")) {
                            market = "KOSDAQ";
                        } else if (marketFromData.contains("코넥스") || marketFromData.contains("KONEX")) {
                            market = "KONEX";
                        }
                    }
                    if (market == null) {
                        market = "KOSPI";
                    }

                    StockCodeDraft stockCodeDraft = StockCodeDraft.builder()
                            .tickerKrx(tickerKrx)
                            .companyName(companyName)
                            .industry(industry)
                            .listingDate(listingDate)
                            .fiscalYearEndMonth(fiscalMonth)
                            .homepageUrl(homepage)
                            .region(region)
                            .market(market)
                            .build();

                    stockCodes.add(stockCodeDraft);

                } catch (Exception e) {
                    log.debug("행 파싱 실패: {}", row.text(), e);
                }
            }

            log.info("HTML 파싱 완료 - {} 개 종목", stockCodes.size());

        } catch (Exception e) {
            log.error("HTML 파싱 오류", e);
        }

        return stockCodes;
    }

    private String normalizeTicker(String code) {
        if (code == null) return "";
        return code.replace("\u00A0", " ").trim();
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty() || dateStr.equals("-")) {
            return null;
        }

        DateTimeFormatter[] formatters = {
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("yyyy.MM.dd"),
                DateTimeFormatter.ofPattern("yyyyMMdd")
        };

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private Integer parseMonth(String monthStr) {
        if (monthStr == null || monthStr.isEmpty() || monthStr.equals("-")) {
            return null;
        }

        Pattern pattern = Pattern.compile("(\\d+)");
        Matcher matcher = pattern.matcher(monthStr);

        if (matcher.find()) {
            int month = Integer.parseInt(matcher.group(1));
            if (month >= 1 && month <= 12) {
                return month;
            }
        }
        return null;
    }
}
