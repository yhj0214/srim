package org.yhj.srim.service.crawl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.client.dto.DaliyPrice;
import org.yhj.srim.service.crawl.client.NaverPriceHttpClient;
import org.yhj.srim.service.crawl.parser.NaverPriceParser;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NaverCrawlingService {

    private static final int MAX_PAGE = 1000;

    private final NaverPriceHttpClient naverPriceHttpClient;
    private final NaverPriceParser naverPriceParser;

    public List<DaliyPrice> fetchDailyPrices(String tickerKrx, LocalDate start, LocalDate end) {
        log.debug("NAVER 일별 시세 수집 시작 - ticker={}, start={}, end={}", tickerKrx, start, end);

        List<DaliyPrice> result = new ArrayList<>();
        boolean done = false;
        LocalDate lastMinDate = null;
        int lastPage = 0;

        for (int page = 1; page <= MAX_PAGE && !done; page++) {
            String content = naverPriceHttpClient.getDailyPricePage(tickerKrx, page);
            List<DaliyPrice> pagePrices = naverPriceParser.parse(content);

            if (pagePrices.isEmpty()) {
                log.debug("더 이상 데이터가 없는 페이지(page={})입니다.", page);
                break;
            }

            LocalDate pageMinDate = null;
            for (DaliyPrice price : pagePrices) {
                LocalDate date = price.getDate();
                if (date == null) {
                    continue;
                }

                if (pageMinDate == null || date.isBefore(pageMinDate)) {
                    pageMinDate = date;
                }

                if (date.isAfter(end)) {
                    continue;
                }

                if (date.isBefore(start)) {
                    done = true;
                    break;
                }

                result.add(price);
            }

            if (pageMinDate != null && lastMinDate != null && !pageMinDate.isBefore(lastMinDate)) {
                log.debug("이전 페이지보다 더 오래된 데이터가 없어 반복이 감지되어 종료합니다. page={}, pageMinDate={}, lastMinDate={}",
                        page, pageMinDate, lastMinDate);
                break;
            }

            lastMinDate = pageMinDate;
            lastPage = page;
        }

        log.info("NAVER 일별 시세 수집 완료 - ticker={}, start={}, end={}, pages={}, count={}",
                tickerKrx, start, end, lastPage, result.size());

        return result;
    }
}
