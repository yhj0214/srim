package org.yhj.srim.service.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yhj.srim.controller.dto.CrawlAllMarketsResult;
import org.yhj.srim.service.domain.CompanyResetService;
import org.yhj.srim.service.domain.StockService;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManagementOrchestratorTest {

    @InjectMocks
    ManagementOrchestrator managementOrchestrator;

    @Mock
    MarketInitializationApplicationService marketInitializationApplicationService;

    @Mock
    AnnualXbrlPipelineOrchestrator annualXbrlPipelineOrchestrator;

    @Mock
    StockService stockService;

    @Mock
    CompanyResetService companyResetService;

    @Test
    @DisplayName("step1은 시장 동기화 결과를 반환하고 채권수익률을 수집한다.")
    void step1_market_sync_and_bond_yield() {
        CrawlAllMarketsResult result = new CrawlAllMarketsResult(10, 8);
        given(marketInitializationApplicationService.marketCrawling()).willReturn(result);

        CrawlAllMarketsResult actual = managementOrchestrator.collectMarketData();

        assertThat(actual.getCrawledCount()).isEqualTo(10);
        assertThat(actual.getMappedCount()).isEqualTo(8);
        verify(marketInitializationApplicationService).marketCrawling();
        verify(marketInitializationApplicationService).crawlAndSaveBondYield(any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    @DisplayName("초기 동기화는 step1 후 전체 회사 동기화를 순차 실행한다.")
    void run_initial_sync_runs_market_then_company_sync() {
        CrawlAllMarketsResult result = new CrawlAllMarketsResult(10, 8);
        given(marketInitializationApplicationService.marketCrawling()).willReturn(result);
        given(stockService.findAllStockIds()).willReturn(List.of(1L));

        CrawlAllMarketsResult actual = managementOrchestrator.runInitialSync(2015, "CFS");

        assertThat(actual.getCrawledCount()).isEqualTo(10);
        assertThat(actual.getMappedCount()).isEqualTo(8);
        verify(marketInitializationApplicationService).marketCrawling();
        verify(stockService).findAllStockIds();
        verify(annualXbrlPipelineOrchestrator).runAnnualXbrlPipeline(1L, 2015, LocalDate.now().getYear() - 1, "CFS");
    }

    @Test
    @DisplayName("step2는 저장된 전체 stockId를 순회하며 XBRL 전체 run을 실행한다.")
    void sync_all_companies_runs_for_all_stock_ids() {
        given(stockService.findAllStockIds()).willReturn(List.of(1L, 2L));

        managementOrchestrator.syncAllCompanies(2015, "CFS");

        verify(stockService).findAllStockIds();
        verify(annualXbrlPipelineOrchestrator).runAnnualXbrlPipeline(1L, 2015, LocalDate.now().getYear() - 1, "CFS");
        verify(annualXbrlPipelineOrchestrator).runAnnualXbrlPipeline(2L, 2015, LocalDate.now().getYear() - 1, "CFS");
    }


    @Test
    @DisplayName("단일 종목 reset은 stockId 조회 후 reset 서비스를 호출한다.")
    void reset_single_company_by_ticker() {
        given(stockService.getStockIdByTickerKrx("005930")).willReturn(1L);

        managementOrchestrator.resetSingleCompanyByTickerKrx("005930");

        verify(stockService).getStockIdByTickerKrx("005930");
        verify(companyResetService).resetByStockId(1L);
    }
}
