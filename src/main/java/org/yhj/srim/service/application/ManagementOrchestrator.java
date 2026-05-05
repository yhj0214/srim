package org.yhj.srim.service.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.controller.dto.CrawlAllMarketsResult;
import org.yhj.srim.service.domain.CompanyResetService;
import org.yhj.srim.service.domain.StockService;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManagementOrchestrator {
    private static final int DEFAULT_START_YEAR = 2015;
    private static final String DEFAULT_FS_DIV = "CFS";

    private final FinancialApplicationService financialApplicationService;
    private final StockService stockService;
    private final CompanyResetService companyResetService;

    // 초기 실행용으로 시장 수집 후 전체 회사 초기화를 순차 실행
    public CrawlAllMarketsResult runInitialSync() {
        return runInitialSync(DEFAULT_START_YEAR, DEFAULT_FS_DIV);
    }

    public CrawlAllMarketsResult runInitialSync(int startYear, String fsDiv) {
        CrawlAllMarketsResult result = collectMarketData();
        syncAllCompanies(startYear, fsDiv);
        return result;
    }

    // 시장 종목/채권수익률 초기 수집 수행
    public CrawlAllMarketsResult collectMarketData() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = LocalDate.of(DEFAULT_START_YEAR, 1, 1);

        // 기업 리스트 크롤링 후 저장 및 dartCorpCode갱신
        CrawlAllMarketsResult result = financialApplicationService.marketCrawling();
        // KE 회사채수익률 크롤링 및 저장
        financialApplicationService.crawlAndSaveBondYield(startDate, endDate);

        log.info("STEP1 종료");
        return result;
    }

    // 저장된 전체 종목 회사별 재무제표 수집
    // to-do 개별 회사 실패 시 재실행 구조
    public void syncAllCompanies() {
        syncAllCompanies(DEFAULT_START_YEAR, DEFAULT_FS_DIV);
    }

    public void syncAllCompanies(int startYear, String fsDiv) {
        int endYear = LocalDate.now().getYear() - 1;
        // 기업조회
        List<Long> stockIds = stockService.findAllStockIds();
        int successCount = 0;
        int failureCount = 0;

        for (Long stockId : stockIds) {
            try {
                initCompanyByXbrl(stockId, startYear, endYear, fsDiv);
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
        initCompanyByXbrl(stockId, DEFAULT_START_YEAR, LocalDate.now().getYear() - 1, DEFAULT_FS_DIV);
    }

    public void resetSingleCompanyByTickerKrx(String tickerKrx) {
        Long stockId = stockService.getStockIdByTickerKrx(tickerKrx);
        companyResetService.resetByStockId(stockId);
    }

    // 개별 회사 단위 XBRL 연간 파이프라인 초기화
    private void initCompanyByXbrl(Long stockId, int startYear, int endYear, String fsDiv) {
        financialApplicationService.runAnnualXbrlPipeline(stockId, startYear, endYear, fsDiv);
    }
}
