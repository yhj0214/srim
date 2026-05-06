package org.yhj.srim.service.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CrawlingError;
import org.yhj.srim.controller.dto.CrawlAllMarketsResult;
import org.yhj.srim.service.crawl.KisSpreadCrawlingService;
import org.yhj.srim.service.crawl.KrxStockCrawlingService;
import org.yhj.srim.service.crawl.dto.StockCodeDraft;
import org.yhj.srim.service.domain.BondYieldCurveService;
import org.yhj.srim.service.domain.DartCorpCodeSyncService;
import org.yhj.srim.service.domain.FailedJobService;
import org.yhj.srim.service.domain.StockService;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketInitializationApplicationServiceTest {

    @InjectMocks
    MarketInitializationApplicationService marketInitializationApplicationService;

    @Mock
    KrxStockCrawlingService krxStockCrawlingService;

    @Mock
    KisSpreadCrawlingService kisSpreadCrawlingService;

    @Mock
    StockService stockService;

    @Mock
    DartCorpCodeSyncService dartCorpCodeSyncService;

    @Mock
    BondYieldCurveService bondYieldCurveService;

    @Mock
    FailedJobService failedJobService;

    @Mock
    ThreadPoolTaskExecutor bondYieldTaskExecutor;

    @Test
    @DisplayName("KOSPI종목을 크롤링하고 저장한 후 매핑 결과를 반환한다.")
    void marketCrawlingAndMapping_success() {
        List<StockCodeDraft> drafts = List.of(
                new StockCodeDraft("KOSPI", "217590", "티엠씨", "절연선 및 케이블 제조업",
                        LocalDate.parse("2025-12-15"), "충청남도", "http://www.tmccable.com", 12),
                new StockCodeDraft("KOSPI", "0126Z0", "삼성에피스홀딩스", "기타 금융업",
                        LocalDate.parse("2025-11-24"), "인천광역시", "http://www.samsungepisholdings.com", 12)
        );

        when(krxStockCrawlingService.fetchStockList("KOSPI")).thenReturn(drafts);
        when(stockService.saveStockDrafts(drafts)).thenReturn(2);
        when(dartCorpCodeSyncService.syncFromXml()).thenReturn(2);

        CrawlAllMarketsResult result = marketInitializationApplicationService.marketCrawling();

        assertThat(result).isNotNull();
        assertThat(result.getCrawledCount()).isEqualTo(2);
        assertThat(result.getMappedCount()).isEqualTo(2);

        verify(krxStockCrawlingService, times(1)).fetchStockList("KOSPI");
        verify(stockService, times(1)).saveStockDrafts(drafts);
        verify(dartCorpCodeSyncService, times(1)).syncFromXml();

        verifyNoMoreInteractions(krxStockCrawlingService, stockService, dartCorpCodeSyncService);
    }

    @Test
    @DisplayName("KOSPI 크롤링 실패 시 예외를 그대로 전파한다.")
    void marketCrawling_fetchStockList_fail() {
        CustomException ex = new CustomException(CrawlingError.KRX_REQUEST_FAILED);
        when(krxStockCrawlingService.fetchStockList("KOSPI")).thenThrow(ex);

        assertThatThrownBy(() -> marketInitializationApplicationService.marketCrawling())
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CrawlingError.KRX_REQUEST_FAILED.getMessage());

        verify(krxStockCrawlingService, times(1)).fetchStockList("KOSPI");
        verifyNoMoreInteractions(krxStockCrawlingService, stockService, dartCorpCodeSyncService);
    }
}
