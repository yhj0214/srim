package org.yhj.srim.service.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.controller.dto.CrawlAllMarketsResult;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.service.domain.CompanyResetService;
import org.yhj.srim.service.domain.StockService;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManagementFacade {

    private final FinancialFacadeService financialFacadeService;
    private final PriceChartFacadeService priceChartFacadeService;
    private final StockService stockService;
    private final CompanyResetService companyResetService;

    private static final LocalDate START_DATE = LocalDate.of(2015, 1, 1);

    // 초기 실행용으로 시장 수집 후 전체 회사 초기화를 순차 실행
    public CrawlAllMarketsResult runInitialSync() {

        // 시장종목 크롤링, dartCorpCode 매핑
        CrawlAllMarketsResult result = collectMarketData();
        syncAllCompanies();

        return result;
    }

    // 시장 종목/채권수익률 초기 수집 수행
    public CrawlAllMarketsResult collectMarketData() {
        LocalDate endDate = LocalDate.now();

        // 기업 리스트 크롤링 후 저장 및 dartCorpCode갱신
        CrawlAllMarketsResult result = financialFacadeService.marketCrawling();
        // KE 회사채수익률 크롤링 및 저장
        financialFacadeService.crawlAndSaveBondYield(START_DATE, endDate);

        log.info("STEP1 종료");
        return result;
    }

    // 저장된 전체 종목 회사별 재무제표 수집
    // to-do 개별 회사 실패 시 재실행 구조
    public void syncAllCompanies() {
        LocalDate endDate = LocalDate.now();

        // 기업조회
        List<Long> stockIds = stockService.findAllStockIds();
        int successCount = 0;
        int failureCount = 0;

        for (Long stockId : stockIds) {
            try {
                initCompany(stockId, endDate);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                log.error("회사 초기화 실패 - stockId={}", stockId, e);
            }
        }

        log.info("전체 회사 초기화 완료 - total={}, success={}, failure={}",
                stockIds.size(), successCount, failureCount);
    }

    // 단일종목 조회 및 초기화
    public void syncSingleCompanyByTickerKrx(String tickerKrx){
        Long stockId = stockService.getStockIdByTickerKrx(tickerKrx);

        LocalDate endDate = LocalDate.now();
        initCompany(stockId, endDate);
    }

    public void resetSingleCompanyByTickerKrx(String tickerKrx) {
        Long stockId = stockService.getStockIdByTickerKrx(tickerKrx);
        companyResetService.resetByStockId(stockId);
    }

    // 개별 회사 단위 재무/주가/주가기반지표 초기화
    private void initCompany(Long stockId, LocalDate endDate) {

        // 회사 정보 및 재무정보 생성
        Company company = financialFacadeService.crawlAnnualTable(stockId, START_DATE.getYear());
        
        // 주가정보 조회
        priceChartFacadeService.ensurePriceData(company.getCompanyId(), START_DATE, endDate);
        
        // 재무관련 metric 계산 및 저장
        financialFacadeService.rebuildCompanyMetrics(company.getCompanyId(),
                START_DATE.getYear(), endDate.getYear());
    }
}
