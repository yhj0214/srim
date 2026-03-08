package org.yhj.srim.service.crawl.client;

public interface NaverPriceHttpClient {
    String getDailyPricePage(String tickerKrx, int page);
}
