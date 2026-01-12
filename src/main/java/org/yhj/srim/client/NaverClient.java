package org.yhj.srim.client;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.yhj.srim.client.dto.DaliyPrice;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverClient {

    private static final String BASE_URL =
            "https://finance.naver.com/item/sise_day.naver?code=%s&page=%d";


    private static final DateTimeFormatter NAVER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /**
     * 지정한 기간 start~end 에 해당하는 일별 시세를 크롤링하여 반환.
     *
     * @param tickerKrx  6자리 KRX 종목코드
     * @param start      조회 시작일 (포함)
     * @param end        조회 종료일 (포함)
     */
    public List<DaliyPrice> fetchDailyPrices(String tickerKrx, LocalDate start, LocalDate end){

        List<DaliyPrice> result = new ArrayList<>();

        int page = 1;
        boolean done = false;
        final int MAX_PAGE = 1000;

        LocalDate lastMinDate = null;

        while(!done) {
            String url = String.format(BASE_URL, tickerKrx, page);
            try{
                log.debug("네이버 일별 시세 요청 : url = {}", url);

                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0")
                        .referrer("https://finance.naver.com")
                        .get();

                Elements rows = doc.select("table.type2 tr");
                boolean hasDataInPage = false;
                LocalDate pageMinDate = null; // 이 페이지에서 가장 오래된 날짜

                for(Element row : rows){
                    Elements tds = row.select("td");

                    if (tds.size() < 7) {
                        continue;
                    }

                    String dateText = tds.get(0).text().trim();
                    String closeText = tds.get(1).text().trim();
                    String openText  = tds.get(3).text().trim();
                    String highText  = tds.get(4).text().trim();
                    String lowText   = tds.get(5).text().trim();
                    String volText   = tds.get(6).text().trim();

                    if (dateText.isEmpty() || closeText.isEmpty()) continue;
                    hasDataInPage = true;

                    LocalDate date = parseDate(dateText);

                    if (pageMinDate == null || date.isBefore(pageMinDate)) {
                        pageMinDate = date;
                    }

                    if (date.isAfter(end)) continue;

                    if (date.isBefore(start)) {
                        done = true;
                        break; // 이 페이지 루프 종료
                    }

                    BigDecimal open  = parseDecimal(openText);
                    BigDecimal high  = parseDecimal(highText);
                    BigDecimal low   = parseDecimal(lowText);
                    BigDecimal close = parseDecimal(closeText);
                    Long volume      = parseLong(volText);

                    result.add(new DaliyPrice(date, open, high, low, close, volume));
                }
                if (!hasDataInPage) {
                    log.debug("더 이상 데이터가 없는 페이지(page={})입니다. 크롤링 종료.", page);
                    break;
                }

                if (pageMinDate != null && lastMinDate != null
                        && !pageMinDate.isBefore(lastMinDate)) {
                    log.debug("이전 페이지보다 더 오래된 데이터가 없어 반복이 감지되어 종료합니다. " +
                            "page={}, pageMinDate={}, lastMinDate={}", page, pageMinDate, lastMinDate);
                    break;
                }
                lastMinDate = pageMinDate;

                if (done) {
                    log.debug("요청 start 이전 날짜까지 도달하여 크롤링 종료. page={}", page);
                    break;
                }

                page++;

            } catch (Exception e){
                log.error("네이버 일별 시세 크롤링 중 오류 발생 - code={}, page={}, msg={}",
                        tickerKrx, page, e.getMessage(), e);
                break;
            }
        }

        log.info("네이버 일별 시세 크롤링 완료 - code={}, start={}, end={}, pages={}, count={}",
                tickerKrx, start, end, page - 1, result.size());

        return result;
    }
    private LocalDate parseDate(String text) {
        return LocalDate.parse(text, NAVER_DATE_FORMAT);
    }

    private BigDecimal parseDecimal(String text) {
        String cleaned = text.replace(",", "").trim();
        if (cleaned.isEmpty() || cleaned.equals("-")) {
            return null;
        }
        return new BigDecimal(cleaned);
    }

    private Long parseLong(String text) {
        String cleaned = text.replace(",", "").trim();
        if (cleaned.isEmpty() || cleaned.equals("-")) {
            return null;
        }
        return Long.parseLong(cleaned);
    }



}
