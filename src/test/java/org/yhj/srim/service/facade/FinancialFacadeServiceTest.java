package org.yhj.srim.service.facade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yhj.srim.client.dto.DartFilingRow;
import org.yhj.srim.client.dto.DartShareStatusRow;
import org.yhj.srim.controller.dto.CrawlAllMarketsResult;
import org.yhj.srim.service.crawl.DartCrawlingService;
import org.yhj.srim.service.crawl.KrxStockCrawlingService;
import org.yhj.srim.service.crawl.XbrlFinancialStatementCrawlingService;
import org.yhj.srim.service.crawl.dto.StockCodeDraft;
import org.yhj.srim.service.domain.DartCorpCodeSyncService;
import org.yhj.srim.service.domain.DartFsFilingService;
import org.yhj.srim.service.domain.FailedJobService;
import org.yhj.srim.service.domain.FinancialMetricService;
import org.yhj.srim.service.domain.FinancialService;
import org.yhj.srim.service.domain.SrimService;
import org.yhj.srim.service.domain.XbrlAnnualDocumentLocator;
import org.yhj.srim.service.domain.StockService;
import org.yhj.srim.service.domain.BondYieldCurveService;
import org.yhj.srim.service.domain.XbrlRawService;
import org.yhj.srim.service.facade.PriceChartFacadeService;
import org.yhj.srim.service.facade.dto.CollectXbrlRawCommand;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CrawlingError;
import org.yhj.srim.client.DartReportType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.DartFsFiling;
import org.yhj.srim.repository.entity.StockCode;
import org.yhj.srim.service.dto.XbrlAnnualDocumentRef;

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
    DartCrawlingService dartCrawlingService;

    @Mock
    DartCorpCodeSyncService dartCorpCodeSyncService;

    @Mock
    DartFsFilingService dartFsFilingService;

    @Mock
    XbrlFinancialStatementCrawlingService xbrlFinancialStatementCrawlingService;

    @Mock
    XbrlRawService xbrlRawService;

    @Mock
    FinancialService financialService;

    @Mock
    FinancialMetricService financialMetricService;

    @Mock
    SrimService srimService;

    @Mock
    XbrlAnnualDocumentLocator xbrlAnnualDocumentLocator;

    @Mock
    BondYieldCurveService bondYieldCurveService;

    @Mock
    FailedJobService failedJobService;

    @Mock
    ThreadPoolTaskExecutor bondYieldTaskExecutor;

    @Mock
    PriceChartFacadeService priceChartFacadeService;

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
        when(financialService.getOrCreateCompanyWithStockCode(1L)).thenReturn(company);
        when(financialService.hasAnnualXbrlRaw(7L, 2024, "CFS")).thenReturn(true);
        when(financialService.processAnnualMetricsFromXbrl(7L, 2024, "CFS")).thenReturn(8);
        when(financialMetricService.rebuildAnnualSupplementalMetricsFromXbrl(7L, 2024)).thenReturn(3);

        int savedCount = financialFacadeService.processAnnualMetricsFromXbrl(1L, 2024, "CFS");

        assertThat(savedCount).isEqualTo(11);
        verify(financialService).getOrCreateCompanyWithStockCode(1L);
        verify(financialService).hasAnnualXbrlRaw(7L, 2024, "CFS");
        verify(financialService).processAnnualMetricsFromXbrl(7L, 2024, "CFS");
        verify(financialMetricService).rebuildAnnualSupplementalMetricsFromXbrl(7L, 2024);
    }

    @Test
    @DisplayName("연간 filing 메타 수집은 최신 사업보고서 메타를 조회해 dart_fs_filing에 저장한다.")
    void collectAnnualFilingMetadata_savesLatestAnnualFiling() {
        StockCode stockCode = StockCode.builder()
                .dartCorpCode("00126380")
                .tickerKrx("900001")
                .companyName("TEST")
                .market("TEST")
                .build();
        Company company = Company.builder()
                .companyId(7L)
                .stockCode(stockCode)
                .currency("KRW")
                .build();
        DartFilingRow filingRow = new DartFilingRow();
        filingRow.setRceptNo("20250311001234");
        filingRow.setRceptDt("20250311");
        filingRow.setReportNm("사업보고서");
        DartFsFiling filing = DartFsFiling.builder()
                .fsFilingId(21L)
                .rceptNo("20250311001234")
                .reprtCode("11011")
                .bsnsYear(2024)
                .fsDiv("CFS")
                .build();

        when(financialService.getOrCreateCompanyWithStockCode(1L)).thenReturn(company);
        when(dartCrawlingService.crawlLatestAnnualFiling("00126380", 2024)).thenReturn(filingRow);
        when(dartFsFilingService.saveAnnualFilingMetadata("00126380", 7L, 2024, filingRow, "CFS"))
                .thenReturn(filing);

        Long filingId = financialFacadeService.collectAnnualFilingMetadata(1L, 2024, "CFS");

        assertThat(filingId).isEqualTo(21L);
        verify(financialService).getOrCreateCompanyWithStockCode(1L);
        verify(dartCrawlingService).crawlLatestAnnualFiling("00126380", 2024);
        verify(dartFsFilingService).saveAnnualFilingMetadata("00126380", 7L, 2024, filingRow, "CFS");
    }

    @Test
    @DisplayName("XBRL 연간 파이프라인은 raw 수집 후 metric 전체 처리를 순서대로 실행한다.")
    void runAnnualXbrlPipeline_collectsAndProcessesMetrics() {
        Company company = Company.builder().companyId(7L).currency("KRW").build();
        List<DartShareStatusRow> shareStatusRows = List.of(mock(DartShareStatusRow.class));
        when(financialService.getOrCreateCompanyWithStockCode(1L)).thenReturn(company);
        when(dartCrawlingService.crawlShareStatus("00126380", 2024)).thenReturn(shareStatusRows);
        when(xbrlRawService.findStoredDocumentId("20240321000001", "11011", "CFS"))
                .thenReturn(Optional.of(99L));
        when(financialService.processAnnualMetricsFromXbrl(7L, 2024, "CFS")).thenReturn(8);
        when(financialMetricService.rebuildAnnualSupplementalMetricsFromXbrl(7L, 2024)).thenReturn(3);

        Long documentId = financialFacadeService.runAnnualXbrlPipeline(
                1L,
                "00126380",
                "20240321000001",
                2024,
                "CFS"
        );

        assertThat(documentId).isEqualTo(99L);

        verify(financialService).getOrCreateCompanyWithStockCode(1L);
        verify(dartCrawlingService).crawlShareStatus("00126380", 2024);
        verify(stockService).replaceShareStatus(company, 2024, shareStatusRows);
        verify(priceChartFacadeService).ensurePriceData(7L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        verify(xbrlRawService).findStoredDocumentId("20240321000001", "11011", "CFS");
        verify(financialService).processAnnualMetricsFromXbrl(7L, 2024, "CFS");
        verify(financialMetricService).rebuildAnnualSupplementalMetricsFromXbrl(7L, 2024);
        verifyNoInteractions(xbrlFinancialStatementCrawlingService);
    }

    @Test
    @DisplayName("연도 기준 XBRL 파이프라인은 최신 연간 filing의 접수번호로 수집/처리를 실행한다.")
    void runAnnualXbrlPipeline_withFiscalYear_resolvesRceptNoFromFiling() {
        StockCode stockCode = StockCode.builder()
                .dartCorpCode("00126380")
                .tickerKrx("900001")
                .companyName("TEST")
                .market("TEST")
                .build();
        Company company = Company.builder()
                .companyId(7L)
                .stockCode(stockCode)
                .currency("KRW")
                .build();
        DartFilingRow filingRow = new DartFilingRow();
        filingRow.setRceptNo("20240321000001");
        filingRow.setRceptDt("20240321");
        filingRow.setReportNm("사업보고서");
        DartFsFiling filing = DartFsFiling.builder()
                .fsFilingId(21L)
                .rceptNo("20240321000001")
                .reprtCode("11011")
                .bsnsYear(2024)
                .fsDiv("CFS")
                .build();
        XbrlAnnualDocumentRef documentRef = new XbrlAnnualDocumentRef("00126380", "20240321000001", 2024, "CFS");
        List<DartShareStatusRow> shareStatusRows = List.of(mock(DartShareStatusRow.class));

        when(financialService.getOrCreateCompanyWithStockCode(1L)).thenReturn(company);
        when(dartCrawlingService.crawlLatestAnnualFiling("00126380", 2024)).thenReturn(filingRow);
        when(dartCrawlingService.crawlShareStatus("00126380", 2024)).thenReturn(shareStatusRows);
        when(dartFsFilingService.saveAnnualFilingMetadata("00126380", 7L, 2024, filingRow, "CFS"))
                .thenReturn(filing);
        when(xbrlAnnualDocumentLocator.resolve(7L, 2024, "CFS")).thenReturn(documentRef);
        when(xbrlRawService.findStoredDocumentId("20240321000001", "11011", "CFS"))
                .thenReturn(Optional.of(99L));
        when(financialService.processAnnualMetricsFromXbrl(7L, 2024, "CFS")).thenReturn(8);
        when(financialMetricService.rebuildAnnualSupplementalMetricsFromXbrl(7L, 2024)).thenReturn(3);

        Long documentId = financialFacadeService.runAnnualXbrlPipeline(1L, 2024, "CFS");

        assertThat(documentId).isEqualTo(99L);
        verify(financialService).getOrCreateCompanyWithStockCode(1L);
        verify(dartCrawlingService).crawlLatestAnnualFiling("00126380", 2024);
        verify(dartFsFilingService).saveAnnualFilingMetadata("00126380", 7L, 2024, filingRow, "CFS");
        verify(xbrlAnnualDocumentLocator).resolve(7L, 2024, "CFS");
        verify(dartCrawlingService).crawlShareStatus("00126380", 2024);
        verify(stockService).replaceShareStatus(company, 2024, shareStatusRows);
        verify(priceChartFacadeService).ensurePriceData(7L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        verify(xbrlRawService).findStoredDocumentId("20240321000001", "11011", "CFS");
        verify(financialService).processAnnualMetricsFromXbrl(7L, 2024, "CFS");
        verify(financialMetricService).rebuildAnnualSupplementalMetricsFromXbrl(7L, 2024);
    }

    @Test
    @DisplayName("연도 기준 XBRL 파이프라인은 CFS 메타 수집이 실패하면 OFS로 fallback 한다.")
    void runAnnualXbrlPipeline_withFiscalYear_fallsBackToOfs() {
        StockCode stockCode = StockCode.builder()
                .dartCorpCode("00126380")
                .tickerKrx("900001")
                .companyName("TEST")
                .market("TEST")
                .build();
        Company company = Company.builder()
                .companyId(7L)
                .stockCode(stockCode)
                .currency("KRW")
                .build();
        DartFilingRow filingRow = new DartFilingRow();
        filingRow.setRceptNo("20240321000001");
        filingRow.setRceptDt("20240321");
        filingRow.setReportNm("사업보고서");
        DartFsFiling filing = DartFsFiling.builder()
                .fsFilingId(21L)
                .rceptNo("20240321000001")
                .reprtCode("11011")
                .bsnsYear(2024)
                .fsDiv("OFS")
                .build();
        XbrlAnnualDocumentRef documentRef = new XbrlAnnualDocumentRef("00126380", "20240321000001", 2024, "OFS");
        List<DartShareStatusRow> shareStatusRows = List.of(mock(DartShareStatusRow.class));

        when(financialService.getOrCreateCompanyWithStockCode(1L)).thenReturn(company);
        when(dartCrawlingService.crawlLatestAnnualFiling("00126380", 2024))
                .thenThrow(new CustomException(CrawlingError.DART_DISCLOSURE_NOT_FOUND))
                .thenReturn(filingRow);
        when(dartCrawlingService.crawlShareStatus("00126380", 2024)).thenReturn(shareStatusRows);
        when(dartFsFilingService.saveAnnualFilingMetadata("00126380", 7L, 2024, filingRow, "OFS"))
                .thenReturn(filing);
        when(xbrlAnnualDocumentLocator.resolve(7L, 2024, "OFS")).thenReturn(documentRef);
        when(xbrlRawService.findStoredDocumentId("20240321000001", "11011", "OFS"))
                .thenReturn(Optional.of(99L));
        when(financialService.processAnnualMetricsFromXbrl(7L, 2024, "OFS")).thenReturn(8);
        when(financialMetricService.rebuildAnnualSupplementalMetricsFromXbrl(7L, 2024)).thenReturn(3);

        Long documentId = financialFacadeService.runAnnualXbrlPipeline(1L, 2024, "CFS");

        assertThat(documentId).isEqualTo(99L);
        verify(dartCrawlingService, times(2)).crawlLatestAnnualFiling("00126380", 2024);
        verify(dartFsFilingService).saveAnnualFilingMetadata("00126380", 7L, 2024, filingRow, "OFS");
        verify(xbrlAnnualDocumentLocator).resolve(7L, 2024, "OFS");
        verify(dartCrawlingService).crawlShareStatus("00126380", 2024);
        verify(stockService).replaceShareStatus(company, 2024, shareStatusRows);
        verify(priceChartFacadeService).ensurePriceData(7L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        verify(xbrlRawService).findStoredDocumentId("20240321000001", "11011", "OFS");
        verify(financialService).processAnnualMetricsFromXbrl(7L, 2024, "OFS");
        verify(financialMetricService).rebuildAnnualSupplementalMetricsFromXbrl(7L, 2024);
    }

    @Test
    @DisplayName("연도 기준 XBRL 파이프라인은 CFS XBRL 파일이 없으면 OFS로 fallback 한다.")
    void runAnnualXbrlPipeline_withFiscalYear_fallsBackToOfsWhenXbrlMissing() {
        StockCode stockCode = StockCode.builder()
                .dartCorpCode("00126380")
                .tickerKrx("900001")
                .companyName("TEST")
                .market("TEST")
                .build();
        Company company = Company.builder()
                .companyId(7L)
                .stockCode(stockCode)
                .currency("KRW")
                .build();
        DartFilingRow cfsFilingRow = new DartFilingRow();
        cfsFilingRow.setRceptNo("20240321000001");
        cfsFilingRow.setRceptDt("20240321");
        cfsFilingRow.setReportNm("사업보고서");
        DartFilingRow ofsFilingRow = new DartFilingRow();
        ofsFilingRow.setRceptNo("20240321000002");
        ofsFilingRow.setRceptDt("20240321");
        ofsFilingRow.setReportNm("사업보고서");
        DartFsFiling cfsFiling = DartFsFiling.builder().fsFilingId(21L).rceptNo("20240321000001").reprtCode("11011").bsnsYear(2024).fsDiv("CFS").build();
        DartFsFiling ofsFiling = DartFsFiling.builder().fsFilingId(22L).rceptNo("20240321000002").reprtCode("11011").bsnsYear(2024).fsDiv("OFS").build();
        XbrlAnnualDocumentRef cfsDocumentRef = new XbrlAnnualDocumentRef("00126380", "20240321000001", 2024, "CFS");
        XbrlAnnualDocumentRef ofsDocumentRef = new XbrlAnnualDocumentRef("00126380", "20240321000002", 2024, "OFS");
        List<DartShareStatusRow> shareStatusRows = List.of(mock(DartShareStatusRow.class));

        when(financialService.getOrCreateCompanyWithStockCode(1L)).thenReturn(company);
        when(dartCrawlingService.crawlLatestAnnualFiling("00126380", 2024))
                .thenReturn(cfsFilingRow)
                .thenReturn(ofsFilingRow);
        when(dartFsFilingService.saveAnnualFilingMetadata("00126380", 7L, 2024, cfsFilingRow, "CFS"))
                .thenReturn(cfsFiling);
        when(dartFsFilingService.saveAnnualFilingMetadata("00126380", 7L, 2024, ofsFilingRow, "OFS"))
                .thenReturn(ofsFiling);
        when(xbrlAnnualDocumentLocator.resolve(7L, 2024, "CFS")).thenReturn(cfsDocumentRef);
        when(xbrlAnnualDocumentLocator.resolve(7L, 2024, "OFS")).thenReturn(ofsDocumentRef);
        when(dartCrawlingService.crawlShareStatus("00126380", 2024)).thenReturn(shareStatusRows);
        when(xbrlRawService.findStoredDocumentId("20240321000001", "11011", "CFS"))
                .thenReturn(Optional.empty());
        when(xbrlRawService.findStoredDocumentId("20240321000002", "11011", "OFS"))
                .thenReturn(Optional.of(99L));
        when(xbrlFinancialStatementCrawlingService.crawlFinancialStatementsXbrl(
                "00126380", "20240321000001", 2024, DartReportType.ANNUAL, "CFS"
        )).thenThrow(new CustomException(CrawlingError.DART_XBRL_NOT_AVAILABLE));
        when(financialService.processAnnualMetricsFromXbrl(7L, 2024, "OFS")).thenReturn(8);
        when(financialMetricService.rebuildAnnualSupplementalMetricsFromXbrl(7L, 2024)).thenReturn(3);

        Long documentId = financialFacadeService.runAnnualXbrlPipeline(1L, 2024, "CFS");

        assertThat(documentId).isEqualTo(99L);
        verify(xbrlAnnualDocumentLocator).resolve(7L, 2024, "CFS");
        verify(xbrlAnnualDocumentLocator).resolve(7L, 2024, "OFS");
        verify(financialService).processAnnualMetricsFromXbrl(7L, 2024, "OFS");
    }

    @Test
    @DisplayName("연간 XBRL 범위 run은 마지막에 현재 연도 주가를 오늘까지 추가 수집한다.")
    void runAnnualXbrlPipeline_range_backfillsCurrentYearPrice() {
        int currentYear = LocalDate.now().getYear();

        StockCode stockCode = StockCode.builder()
                .dartCorpCode("00126380")
                .tickerKrx("900001")
                .companyName("TEST")
                .market("TEST")
                .build();
        Company company = Company.builder()
                .companyId(7L)
                .stockCode(stockCode)
                .currency("KRW")
                .build();
        DartFilingRow filingRow = new DartFilingRow();
        filingRow.setRceptNo("20240321000001");
        filingRow.setRceptDt("20240321");
        filingRow.setReportNm("사업보고서");
        DartFsFiling filing = DartFsFiling.builder()
                .fsFilingId(21L)
                .rceptNo("20240321000001")
                .reprtCode("11011")
                .bsnsYear(2024)
                .fsDiv("CFS")
                .build();
        XbrlAnnualDocumentRef documentRef = new XbrlAnnualDocumentRef("00126380", "20240321000001", 2024, "CFS");
        List<DartShareStatusRow> shareStatusRows = List.of(mock(DartShareStatusRow.class));

        when(financialService.getOrCreateCompanyWithStockCode(1L)).thenReturn(company);
        when(dartCrawlingService.crawlLatestAnnualFiling("00126380", 2024)).thenReturn(filingRow);
        when(dartFsFilingService.saveAnnualFilingMetadata("00126380", 7L, 2024, filingRow, "CFS"))
                .thenReturn(filing);
        when(xbrlAnnualDocumentLocator.resolve(7L, 2024, "CFS")).thenReturn(documentRef);
        when(dartCrawlingService.crawlShareStatus("00126380", 2024)).thenReturn(shareStatusRows);
        when(xbrlRawService.findStoredDocumentId("20240321000001", "11011", "CFS"))
                .thenReturn(Optional.of(99L));
        when(financialService.hasAnnualXbrlRaw(7L, 2024, "CFS")).thenReturn(true);
        when(financialService.processAnnualMetricsFromXbrl(7L, 2024, "CFS")).thenReturn(8);
        when(financialMetricService.rebuildAnnualSupplementalMetricsFromXbrl(7L, 2024)).thenReturn(3);

        int completedYears = financialFacadeService.runAnnualXbrlPipeline(1L, 2024, 2024, "CFS");

        assertThat(completedYears).isEqualTo(1);
        verify(priceChartFacadeService).ensurePriceData(7L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        verify(priceChartFacadeService).ensurePriceData(7L, LocalDate.of(currentYear, 1, 1), LocalDate.now());
    }

}
