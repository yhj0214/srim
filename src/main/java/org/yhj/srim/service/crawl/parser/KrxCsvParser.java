package org.yhj.srim.service.crawl.parser;

import lombok.extern.slf4j.Slf4j;
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
public class KrxCsvParser implements KrxParser {

    @Override
    public boolean supports(String content) {
        if (content == null) return false;
        String t = content.trim();
        return !(t.startsWith("<") || t.contains("<html") || t.contains("<table"));
    }

    @Override
    public List<StockCodeDraft> parse(String csvContent, String defaultMarket) {
        List<StockCodeDraft> stockCodes = new ArrayList<>();

        try {
            String[] lines = csvContent.split("\n");

            log.info("CSV 총 라인 수: {}", lines.length);

            if (lines.length < 2) {
                log.warn("CSV 데이터가 너무 짧습니다. 라인 수: {}", lines.length);
                return stockCodes;
            }

            log.info("CSV 헤더: {}", lines[0]);
            if (lines.length > 1) {
                log.info("첫 번째 데이터 행: {}", lines[1]);
            }

            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                try {
                    StockCodeDraft stockCode = parseCsvLine(line, defaultMarket);
                    if (stockCode != null) {
                        stockCodes.add(stockCode);
                    }
                } catch (Exception e) {
                    log.debug("라인 파싱 실패 [{}]: {}", i, e.getMessage());
                }
            }

            log.info("CSV 파싱 완료 - {} 개 종목", stockCodes.size());

        } catch (Exception e) {
            log.error("CSV 파싱 오류", e);
        }

        return stockCodes;
    }

    private StockCodeDraft parseCsvLine(String line, String defaultMarket) {
        List<String> columns = parseCsvColumns(line);

        if (columns.size() < 3) {
            return null;
        }

        String companyName = columns.get(0).trim();
        String tickerKrx = normalizeTicker(columns.get(1).trim());
        String industry = columns.size() > 2 ? columns.get(2).trim() : null;

        if (tickerKrx.isEmpty() || companyName.isEmpty()) {
            return null;
        }

        LocalDate listingDate = columns.size() > 4 ? parseDate(columns.get(4).trim()) : null;
        Integer fiscalMonth = columns.size() > 5 ? parseMonth(columns.get(5).trim()) : null;
        String homepage = columns.size() > 7 ? columns.get(7).trim() : null;
        String region = columns.size() > 8 ? columns.get(8).trim() : null;

        String market = defaultMarket != null ? defaultMarket : "KOSPI";

        return StockCodeDraft.builder()
                .tickerKrx(tickerKrx)
                .companyName(companyName)
                .industry(industry)
                .listingDate(listingDate)
                .fiscalYearEndMonth(fiscalMonth)
                .homepageUrl(homepage)
                .region(region)
                .market(market)
                .build();
    }

    private List<String> parseCsvColumns(String line) {
        List<String> columns = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                columns.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        columns.add(current.toString());
        return columns;
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
