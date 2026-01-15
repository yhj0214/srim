package org.yhj.srim.service.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.StockErrorCode;
import org.yhj.srim.controller.dto.CrawlAllMarketsResult;
import org.yhj.srim.repository.CompanyRepository;
import org.yhj.srim.repository.StockCodeRepository;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.StockCode;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManagementFacade {

    private final FinancialFacadeService financialFacadeService;
    private final PriceChartFacadeService priceChartFacadeService;
    private final StockCodeRepository stockCodeRepository;
    private final CompanyRepository companyRepository;

    private static final int DEFAULT_YEAR = 10;

    public CrawlAllMarketsResult step1MarketSync() {
        return financialFacadeService.marketCrawling();
    }

    public CrawlAllMarketsResult initializeAll() {

        // 시장종목 크롤링, dartCorpCode 매핑
        CrawlAllMarketsResult result = financialFacadeService.marketCrawling();

//        // 기업조회
//        List<StockCode> stockCodes = stockCodeRepository.findAll();
//
//
//
//        // 회사 정보 및 재무정보 생성
//        stockCodes.forEach(sc -> financialFacadeService.getAnnualTable(sc.getStockId(), DEFAULT_YEAR));
//
//        // 주가정보 조회
//        List<Company> companies = companyRepository.findAll();
//
//        LocalDate startDate = LocalDate.now().minusYears(DEFAULT_YEAR);
//        LocalDate endDate = LocalDate.now();
//        companies.forEach(company ->
////                priceChartFacadeService.getPriceChart(company.getCompanyId(), startDate, endDate)
//
//                priceChartFacadeService.ensurePriceData(company.getCompanyId(), startDate, endDate)
//        );
//
////          회사채수익률조회
//        financialFacadeService.CrawlAndSaveBondYield(startDate, endDate);

        return result;
    }

    public void processOneStock(StockCode stockCode, int years) {

        // 회사 정보 및 재무정보 생성
        financialFacadeService.getAnnualTable(stockCode.getStockId(), years);

        Company company = companyRepository.findByStockCode_StockId(stockCode.getStockId())
                .orElseThrow(() -> new IllegalStateException("company 매핑 누락 stockId=" + stockCode.getStockId()));

        LocalDate start = LocalDate.now().minusYears(years);
        LocalDate end = LocalDate.now();
        priceChartFacadeService.ensurePriceData(company.getCompanyId(), start, end);
    }

    public void findStockInfo(String stickerKrx) {
        StockCode stockCode = stockCodeRepository.findByTickerKrx(stickerKrx)
                .orElseThrow(() -> new CustomException(StockErrorCode.STOCK_NOT_FOUND));

        processOneStock(stockCode, DEFAULT_YEAR);
    }
}
