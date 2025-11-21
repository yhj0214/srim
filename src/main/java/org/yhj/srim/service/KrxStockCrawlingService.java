package org.yhj.srim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.repository.StockCodeRepository;
import org.yhj.srim.repository.entity.StockCode;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * KRX 상장법인목록 크롤링 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KrxStockCrawlingService {

    private final StockCodeRepository stockCodeRepository;

    private static final String KRX_CORP_LIST_URL = "https://kind.krx.co.kr/corpgeneral/corpList.do";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    // https://kind.krx.co.kr/corpgeneral/corpList.do?method=download&searchType=13&currentPageSize=5000&pageIndex=1&marketType=stockMkt&OrderMode=3&orderStat=D&fiscalYearEnd=all&location=all
    @Transactional
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public int crawlAndSaveStockList(String marketType) {
        try {
            log.info("KRX 상장법인 목록 크롤링 시작 - 시장구분: {}", marketType != null ? marketType : "전체");

            String searchType = "13";
            String marketTypeParam = "";
            if ("KOSPI".equalsIgnoreCase(marketType)) {
                marketTypeParam = "&marketType=stockMkt";
            } else if ("KOSDAQ".equalsIgnoreCase(marketType)) {
                marketTypeParam = "&marketType=kosdaqMkt";
            }

            // URL에 파라미터를 직접 포함시켜서 전체 데이터 요청
            // method=download&searchType=13&currentPageSize=5000&pageIndex=1&marketType=stockMkt&OrderMode=3&orderStat=D&fiscalYearEnd=all&location=all
            String fullUrl = KRX_CORP_LIST_URL
                    + "?method=download"
                    + "&searchType=" + searchType
                    + "&currentPageSize=5000"    // 전체 데이터 요청
                    + "&pageIndex=1"
                    + marketTypeParam
                    + "&OrderMode=3"
                    + "&orderStat=D"
                    + "&fiscalYearEnd=all"
                    + "&location=all";

            log.info("요청 URL: {}", fullUrl);

            Connection.Response response = Jsoup.connect(fullUrl)
                    .method(Connection.Method.GET)
                    .userAgent(USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml")
                    .header("Accept-Language", "ko-KR,ko;q=0.9")
                    .header("Referer", "https://kind.krx.co.kr/")
                    .ignoreContentType(true)
                    .timeout(30000)
                    .maxBodySize(0)   // 무제한 크기 허용
                    .execute();

            // EUC-KR 인코딩 처리
            byte[] bodyBytes = response.bodyAsBytes();
            String content = new String(bodyBytes, "EUC-KR");
            String contentType = response.contentType();

            log.info("응답 길이: {}", content.length());
            log.info("응답 컨텐츠 타입: {}", contentType);
            log.info("응답 내용 첫 500자:");
            log.info(content.substring(0, Math.min(500, content.length())));

            List<StockCode> stockCodes;

            // HTML인지 CSV인지 판단
            if (content.trim().startsWith("<") || content.contains("<html") || content.contains("<table")) {
                log.info("HTML 형식으로 판단, HTML 파싱 시도");
                stockCodes = parseHtmlData(content, marketType);
            } else {
                log.info("CSV 형식으로 판단, CSV 파싱 시도");
                stockCodes = parseCsvData(content, marketType);
            }

            if (stockCodes.isEmpty()) {
                log.warn("크롤링된 데이터가 없습니다.");
                return 0;
            }

            int savedCount = 0;
            for (StockCode stockCode : stockCodes) {
                try {
                    Optional<StockCode> existing = stockCodeRepository
                            .findByMarketAndTickerKrx(stockCode.getMarket(), stockCode.getTickerKrx());

                    if (existing.isPresent()) {
                        StockCode existingStock = existing.get();
                        existingStock.setCompanyName(stockCode.getCompanyName());
                        existingStock.setIndustry(stockCode.getIndustry());
                        existingStock.setListingDate(stockCode.getListingDate());
                        existingStock.setRegion(stockCode.getRegion());
                        stockCodeRepository.save(existingStock);
                    } else {
                        stockCodeRepository.save(stockCode);
                    }

                    savedCount++;

                } catch (Exception e) {
                    log.error("종목 저장 실패: {}", stockCode.getCompanyName(), e);
                }
            }

            log.info("KRX 크롤링 완료 - {} 건 처리", savedCount);
            return savedCount;

        } catch (IOException e) {
            log.error("KRX 크롤링 실패", e);
            throw new RuntimeException("KRX 서버 연결 실패: " + e.getMessage(), e);
        }
    }


    /**
     * HTML 테이블 파싱
     */
    private List<StockCode> parseHtmlData(String htmlContent, String defaultMarket) {
        List<StockCode> stockCodes = new ArrayList<>();

        try {
            Document doc = Jsoup.parse(htmlContent);
            Elements rows = doc.select("tr");
            
            log.info("HTML 테이블 행 수: {}", rows.size());
            
            boolean isFirstRow = true;
            for (Element row : rows) {
                // 헤더 행 건너뛰기
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
                    // 컬럼 순서: 회사명, 시장구분, 종목코드, 업종, 주요제품, 상장일, 결산월, 대표자명, 홈페이지, 지역
                    if (cols.size() < 4) {
                        log.debug("컬럼 수 부족: {}", cols.size());
                        continue;
                    }
                    
                    String companyName = cols.get(0).text().trim();
                    String marketFromData = cols.get(1).text().trim();  // 시장구분
                    String tickerKrx = extractNumericCode(cols.get(2).text().trim());
                    String industry = cols.size() > 3 ? cols.get(3).text().trim() : null;
                    
                    if (tickerKrx.isEmpty() || companyName.isEmpty()) {
                        log.debug("필수 데이터 누락: 회사명={}, 티커={}", companyName, tickerKrx);
                        continue;
                    }
                    
                    LocalDate listingDate = cols.size() > 5 ? parseDate(cols.get(5).text().trim()) : null;
                    Integer fiscalMonth = cols.size() > 6 ? parseMonth(cols.get(6).text().trim()) : null;
                    String homepage = cols.size() > 8 ? cols.get(8).text().trim() : null;
                    String region = cols.size() > 9 ? cols.get(9).text().trim() : null;
                    
                    // 시장 구분 결정 (데이터에서 온 값 우선, 없으면 파라미터 사용)
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
                    
                    StockCode stockCode = StockCode.builder()
                            .tickerKrx(tickerKrx)
                            .companyName(companyName)
                            .industry(industry)
                            .listingDate(listingDate)
                            .fiscalYearEndMonth(fiscalMonth)
                            .homepageUrl(homepage)
                            .region(region)
                            .market(market)
                            .build();
                    
                    stockCodes.add(stockCode);
                    
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

    /**
     * CSV 데이터 파싱
     */
    private List<StockCode> parseCsvData(String csvContent, String defaultMarket) {
        List<StockCode> stockCodes = new ArrayList<>();

        try {
            String[] lines = csvContent.split("\n");
            
            log.info("CSV 총 라인 수: {}", lines.length);
            
            if (lines.length < 2) {
                log.warn("CSV 데이터가 너무 짧습니다. 라인 수: {}", lines.length);
                return stockCodes;
            }
            
            // 헤더 확인
            log.info("CSV 헤더: {}", lines[0]);
            
            // 첫 번째 데이터 행 확인
            if (lines.length > 1) {
                log.info("첫 번째 데이터 행: {}", lines[1]);
            }

            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                try {
                    StockCode stockCode = parseCsvLine(line, defaultMarket);
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

    private StockCode parseCsvLine(String line, String defaultMarket) {
        List<String> columns = parseCsvColumns(line);

        if (columns.size() < 3) {
            return null;
        }

        String companyName = columns.get(0).trim();
        String tickerKrx = extractNumericCode(columns.get(1).trim());
        String industry = columns.size() > 2 ? columns.get(2).trim() : null;
        
        if (tickerKrx.isEmpty() || companyName.isEmpty()) {
            return null;
        }

        LocalDate listingDate = columns.size() > 4 ? parseDate(columns.get(4).trim()) : null;
        Integer fiscalMonth = columns.size() > 5 ? parseMonth(columns.get(5).trim()) : null;
        String homepage = columns.size() > 7 ? columns.get(7).trim() : null;
        String region = columns.size() > 8 ? columns.get(8).trim() : null;

        String market = defaultMarket != null ? defaultMarket : "KOSPI";

        return StockCode.builder()
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

    private String extractNumericCode(String code) {
        return code != null ? code.replaceAll("[^0-9]", "") : "";
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

    @Transactional
    public int crawlAllMarkets() {
        log.info("전체 시장 크롤링 시작");
        
        int total = 0;
        
        try {
            total += crawlAndSaveStockList("KOSPI");
            Thread.sleep(2000);
            total += crawlAndSaveStockList("KOSDAQ");
        } catch (Exception e) {
            log.error("크롤링 실패", e);
        }

        return total;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getStockCountByMarket() {
        List<StockCode> allStocks = stockCodeRepository.findAll();
        
        Map<String, Long> countMap = new HashMap<>();
        for (StockCode stock : allStocks) {
            String market = stock.getMarket() != null ? stock.getMarket() : "UNKNOWN";
            countMap.put(market, countMap.getOrDefault(market, 0L) + 1);
        }
        
        return countMap;
    }
}
