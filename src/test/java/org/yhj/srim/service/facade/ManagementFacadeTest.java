package org.yhj.srim.service.facade;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManagementFacadeTest {

    @InjectMocks
    ManagementFacade managementFacade;

    @Mock
    FinancialFacadeService financialFacadeService;

    @Mock
    StockService stockService;

    @Mock
    PriceChartFacadeService priceChartFacadeService;

    @Mock
    CompanyResetService companyResetService;

    @Test
    @DisplayName("step1은 시장 동기화 결과를 반환하고 채권수익률을 수집한다.")
    void step1_market_sync_and_bond_yield() {
        CrawlAllMarketsResult result = new CrawlAllMarketsResult(10, 8);
        given(financialFacadeService.marketCrawling()).willReturn(result);

        CrawlAllMarketsResult actual = managementFacade.collectMarketData();

        assertThat(actual.getCrawledCount()).isEqualTo(10);
        assertThat(actual.getMappedCount()).isEqualTo(8);
        verify(financialFacadeService).marketCrawling();
        verify(financialFacadeService).crawlAndSaveBondYield(any(LocalDate.class), any(LocalDate.class));
    }


    @Test
    @DisplayName("단일 종목 reset은 stockId 조회 후 reset 서비스를 호출한다.")
    void reset_single_company_by_ticker() {
        given(stockService.getStockIdByTickerKrx("005930")).willReturn(1L);

        managementFacade.resetSingleCompanyByTickerKrx("005930");

        verify(stockService).getStockIdByTickerKrx("005930");
        verify(companyResetService).resetByStockId(1L);
    }
}
