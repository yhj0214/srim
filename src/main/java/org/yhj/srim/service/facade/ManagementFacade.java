package org.yhj.srim.service.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.StockError;
import org.yhj.srim.controller.dto.CrawlAllMarketsResult;
import org.yhj.srim.repository.StockCodeRepository;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.StockCode;
import org.yhj.srim.service.domain.PriceBasedMetricService;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManagementFacade {

    private final FinancialFacadeService financialFacadeService;
    private final PriceChartFacadeService priceChartFacadeService;
    private final PriceBasedMetricService priceBasedMetricService;
    private final StockCodeRepository stockCodeRepository;

    private static final int DEFAULT_YEAR = 10;
    private static final LocalDate START_DATE = LocalDate.of(2015, 1, 1);


    public CrawlAllMarketsResult step1MarketSync() {
        LocalDate endDate = LocalDate.now();

        // 기업 리스트 크롤링 후 저장 및 dartCorpCode갱신
        CrawlAllMarketsResult result = financialFacadeService.marketCrawling();
        // KE 회사채수익률 크롤링 및 저장
        financialFacadeService.CrawlAndSaveBondYield(START_DATE, endDate);
        return result;
    }

    public void step2CorpSync() {
        LocalDate endDate = LocalDate.now();

        // 기업조회
        List<StockCode> stockCodes = stockCodeRepository.findAll();

        for(StockCode stockCode : stockCodes) {
            // 회사 정보 및 재무정보 생성
            Company company = financialFacadeService.crawlAnnualTable(stockCode.getStockId(), DEFAULT_YEAR);

            // 주가정보 조회
            priceChartFacadeService.ensurePriceData(company.getCompanyId(), START_DATE, endDate);

            // 주가 기반 지표(PER/PBR 등) 계산
            priceBasedMetricService.recalcAnnualPriceMetrics(company.getCompanyId());
        }

    }


    public CrawlAllMarketsResult initializeAll() {

        // 시장종목 크롤링, dartCorpCode 매핑
        CrawlAllMarketsResult result = step1MarketSync();
        step2CorpSync();

        return result;
    }

    public void findStockInfoByTickerKrx(String tickerKrx){
        StockCode stockCode = stockCodeRepository.findByTickerKrx(tickerKrx)
                .orElseThrow(() -> new CustomException(StockError.STOCK_NOT_FOUND));

        findStockInfo(stockCode);
    }

    public void findStockInfo(StockCode stockCode) {
        LocalDate endDate = LocalDate.now();

        Company company = financialFacadeService.crawlAnnualTable(stockCode.getStockId(), DEFAULT_YEAR);

        priceChartFacadeService.ensurePriceData(company.getCompanyId(), START_DATE, endDate);
        priceBasedMetricService.recalcAnnualPriceMetrics(company.getCompanyId());
    }
}
