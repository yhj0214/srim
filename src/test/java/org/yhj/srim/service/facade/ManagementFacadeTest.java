package org.yhj.srim.service.facade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yhj.srim.controller.dto.CrawlAllMarketsResult;
import org.yhj.srim.service.domain.PriceBasedMetricService;

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
    PriceChartFacadeService priceChartFacadeService;

    @Mock
    PriceBasedMetricService priceBasedMetricService;

    @Mock
    org.yhj.srim.repository.StockCodeRepository stockCodeRepository;

    @Test
    @DisplayName("step1은 시장 동기화 결과를 반환하고 채권수익률을 수집한다.")
    void step1_market_sync_and_bond_yield() {
        CrawlAllMarketsResult result = new CrawlAllMarketsResult(10, 8);
        given(financialFacadeService.marketCrawling()).willReturn(result);

        CrawlAllMarketsResult actual = managementFacade.step1MarketSync();

        assertThat(actual.getCrawledCount()).isEqualTo(10);
        assertThat(actual.getMappedCount()).isEqualTo(8);
        verify(financialFacadeService).marketCrawling();
        verify(financialFacadeService).CrawlAndSaveBondYield(any(LocalDate.class), any(LocalDate.class));
    }


}
