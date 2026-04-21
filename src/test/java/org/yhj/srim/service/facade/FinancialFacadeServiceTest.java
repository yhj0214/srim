package org.yhj.srim.service.facade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yhj.srim.controller.dto.CrawlAllMarketsResult;
import org.yhj.srim.service.crawl.KrxStockCrawlingService;
import org.yhj.srim.service.crawl.XbrlFinancialStatementCrawlingService;
import org.yhj.srim.service.crawl.dto.StockCodeDraft;
import org.yhj.srim.service.domain.DartCorpCodeSyncService;
import org.yhj.srim.service.domain.FailedJobService;
import org.yhj.srim.service.domain.FinancialMetricService;
import org.yhj.srim.service.domain.FinancialService;
import org.yhj.srim.service.domain.StockService;
import org.yhj.srim.service.domain.BondYieldCurveService;
import org.yhj.srim.service.domain.XbrlRawService;
import org.yhj.srim.service.facade.dto.CollectXbrlRawCommand;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CrawlingError;
import org.yhj.srim.client.DartReportType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.yhj.srim.repository.entity.Company;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinancialFacadeServiceTest {

    @InjectMocks
    FinancialFacadeService financialFacadeService;

    @Mock
    KrxStockCrawlingService krxStockCrawlingService;

    @Mock
    StockService stockService;

    @Mock
    DartCorpCodeSyncService dartCorpCodeSyncService;

    @Mock
    XbrlFinancialStatementCrawlingService xbrlFinancialStatementCrawlingService;

    @Mock
    XbrlRawService xbrlRawService;

    @Mock
    FinancialService financialService;

    @Mock
    FinancialMetricService financialMetricService;

    @Mock
    BondYieldCurveService bondYieldCurveService;

    @Mock
    FailedJobService failedJobService;

    @Mock
    ThreadPoolTaskExecutor bondYieldTaskExecutor;

    @Test
    @DisplayName("KOSPI종목을 크롤링하고 저장한 후 매핑 결과를 반환한다.")
    void marketCrawlingAndMapping_success() {

        // given
        List<StockCodeDraft> drafts = List.of(
                new StockCodeDraft("KOSPI", "217590", "티엠씨", "절연선 및 케이블 제조업",
                        LocalDate.parse("2025-12-15"), "충청남도", "http://www.tmccable.com", 12),
                new StockCodeDraft("KOSPI", "0126Z0", "삼성에피스홀딩스", "기타 금융업",
                        LocalDate.parse("2025-11-24"), "인천광역시", "http://www.samsungepisholdings.com", 12)
        );

        // stub
        when(krxStockCrawlingService.fetchStockList("KOSPI")).thenReturn(drafts);
        when(stockService.saveStockDrafts(drafts)).thenReturn(2);
        when(dartCorpCodeSyncService.syncFromXml()).thenReturn(2);

        // when
        CrawlAllMarketsResult result = financialFacadeService.marketCrawling();

        // then
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
        // given
        CustomException ex = new CustomException(CrawlingError.KRX_REQUEST_FAILED);
        when(krxStockCrawlingService.fetchStockList("KOSPI")).thenThrow(ex);

        // when / then
        assertThatThrownBy(() -> financialFacadeService.marketCrawling())
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CrawlingError.KRX_REQUEST_FAILED.getMessage());

        verify(krxStockCrawlingService, times(1)).fetchStockList("KOSPI");
        verifyNoMoreInteractions(krxStockCrawlingService, stockService, dartCorpCodeSyncService);
    }

    @Test
    @DisplayName("이미 저장된 XBRL 문서가 있으면 다운로드 없이 기존 문서 ID를 반환한다.")
    void collectXbrlRaw_returnsExistingDocumentId() {
        when(xbrlRawService.findStoredDocumentId("20240321000001", "11011", "CFS"))
                .thenReturn(Optional.of(99L));

        Long documentId = financialFacadeService.collectXbrlRaw(
                new CollectXbrlRawCommand(
                        1L,
                        "00126380",
                        "20240321000001",
                        2024,
                        DartReportType.ANNUAL,
                        "CFS"
                )
        );

        assertThat(documentId).isEqualTo(99L);

        verify(xbrlRawService).findStoredDocumentId("20240321000001", "11011", "CFS");
        verifyNoInteractions(xbrlFinancialStatementCrawlingService);
    }

    @Test
    @DisplayName("XBRL 연간 metric 처리는 stockId로 회사를 확보한 뒤 FinancialService에 위임한다.")
    void processAnnualMetricsFromXbrl_delegatesToFinancialService() {
        Company company = Company.builder().companyId(7L).currency("KRW").build();
        when(financialService.getOrCreateCompany(1L)).thenReturn(company);
        when(financialService.processAnnualMetricsFromXbrl(7L, 2024, "CFS")).thenReturn(8);

        int savedCount = financialFacadeService.processAnnualMetricsFromXbrl(1L, 2024, "CFS");

        assertThat(savedCount).isEqualTo(8);
        verify(financialService).getOrCreateCompany(1L);
        verify(financialService).processAnnualMetricsFromXbrl(7L, 2024, "CFS");
    }

    @Test
    @DisplayName("XBRL 연간 파이프라인은 raw 수집 후 metric 전체 처리를 순서대로 실행한다.")
    void runAnnualXbrlPipeline_collectsAndProcessesMetrics() {
        Company company = Company.builder().companyId(7L).currency("KRW").build();
        when(financialService.getOrCreateCompany(1L)).thenReturn(company);
        when(xbrlRawService.findStoredDocumentId("20240321000001", "11011", "CFS"))
                .thenReturn(Optional.of(99L));
        when(financialService.processAnnualMetricsFromXbrl(7L, 2024, "CFS")).thenReturn(8);

        Long documentId = financialFacadeService.runAnnualXbrlPipeline(
                1L,
                "00126380",
                "20240321000001",
                2024,
                "CFS"
        );

        assertThat(documentId).isEqualTo(99L);

        verify(financialService).getOrCreateCompany(1L);
        verify(xbrlRawService).findStoredDocumentId("20240321000001", "11011", "CFS");
        verify(financialService).processAnnualMetricsFromXbrl(7L, 2024, "CFS");
        verifyNoInteractions(xbrlFinancialStatementCrawlingService);
    }

}
