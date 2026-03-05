package org.yhj.srim.service.crawl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CrawlingError;
import org.yhj.srim.service.crawl.client.KrxHttpClient;
import org.yhj.srim.service.crawl.dto.StockCodeDraft;
import org.yhj.srim.service.crawl.parser.KrxParser;

import java.util.List;

/**
 * KRX 상장법인목록 크롤링 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KrxStockCrawlingService {

    private static final String KRX_CORP_LIST_URL = "https://kind.krx.co.kr/corpgeneral/corpList.do";

    private final KrxHttpClient krxHttpClient;
    private final List<KrxParser> parsers;

    // https://kind.krx.co.kr/corpgeneral/corpList.do?method=download&searchType=13&currentPageSize=5000&pageIndex=1&marketType=stockMkt&OrderMode=3&orderStat=D&fiscalYearEnd=all&location=all
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public List<StockCodeDraft> fetchStockList(String marketType) {
        String fullUrl = buildUrl(marketType);
        String content = krxHttpClient.get(fullUrl);

        KrxParser parser = parsers.stream()
                .filter(p -> p.supports(content))
                .findFirst()
                .orElseThrow(() -> new CustomException(CrawlingError.KRX_REQUEST_FAILED));

        return parser.parse(content, marketType);
    }

    private String buildUrl(String marketType) {

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

        return fullUrl;
    }
}
