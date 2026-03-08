package org.yhj.srim.service.crawl.client;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CrawlingError;

@Component
@Slf4j
public class JsoupNaverPriceHttpClient implements NaverPriceHttpClient {

    private static final String BASE_URL =
            "https://finance.naver.com/item/sise_day.naver?code=%s&page=%d";

    @Override
    public String getDailyPricePage(String tickerKrx, int page) {
        String url = String.format(BASE_URL, tickerKrx, page);

        try {
            return Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .referrer("https://finance.naver.com")
                    .get()
                    .outerHtml();
        } catch (Exception e) {
            log.error("NAVER 일별 시세 페이지 조회 실패 - code={}, page={}", tickerKrx, page, e);
            throw new CustomException(CrawlingError.NAVER_REQUEST_FAILED, "code=" + tickerKrx + ", page=" + page);
        }
    }
}
