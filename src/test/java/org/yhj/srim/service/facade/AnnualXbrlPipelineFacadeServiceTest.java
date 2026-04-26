package org.yhj.srim.service.facade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CrawlingError;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.DartFsFiling;
import org.yhj.srim.repository.entity.StockCode;
import org.yhj.srim.service.domain.AnnualXbrlMetricProcessor;
import org.yhj.srim.service.domain.FinancialMetricService;
import org.yhj.srim.service.domain.FinancialService;
import org.yhj.srim.service.domain.XbrlAnnualDocumentLocator;
import org.yhj.srim.service.dto.XbrlAnnualDocumentRef;
import org.yhj.srim.service.facade.dto.CollectXbrlRawCommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnualXbrlPipelineFacadeServiceTest {

    @InjectMocks
    AnnualXbrlPipelineFacadeService annualXbrlPipelineFacadeService;

    @Mock
    AnnualXbrlCollector annualXbrlCollector;

    @Mock
    AnnualXbrlRunPolicy annualXbrlRunPolicy;

    @Mock
    FinancialService financialService;

    @Mock
    AnnualXbrlMetricProcessor annualXbrlMetricProcessor;

    @Mock
    FinancialMetricService financialMetricService;

    @Mock
    XbrlAnnualDocumentLocator xbrlAnnualDocumentLocator;

    @Test
    @DisplayName("이미 저장된 XBRL 문서가 있으면 다운로드 없이 기존 문서 ID를 반환한다.")
    void collectXbrlRaw_returnsExistingDocumentId() {
        CollectXbrlRawCommand command = new CollectXbrlRawCommand(
                1L,
                "00126380",
                "20240321000001",
                2024,
                org.yhj.srim.client.DartReportType.ANNUAL,
                "CFS"
        );
        when(annualXbrlCollector.collectXbrlRaw(command)).thenReturn(99L);

        Long documentId = annualXbrlPipelineFacadeService.collectXbrlRaw(command);

        assertThat(documentId).isEqualTo(99L);
        verify(annualXbrlCollector).collectXbrlRaw(command);
    }

    @Test
    @DisplayName("XBRL 연간 metric 처리는 stockId로 회사를 확보한 뒤 metric processor에 위임한다.")
    void processAnnualMetricsFromXbrl_delegatesToMetricProcessor() {
        Company company = Company.builder().companyId(7L).currency("KRW").build();
        when(financialService.getOrCreateCompanyWithStockCode(1L)).thenReturn(company);
        when(annualXbrlRunPolicy.resolveAnnualProcessingFsDiv(7L, 2024, "CFS")).thenReturn("CFS");
        when(annualXbrlMetricProcessor.processAnnualMetricsFromXbrl(7L, 2024, "CFS")).thenReturn(8);
        when(financialMetricService.rebuildAnnualSupplementalMetricsFromXbrl(7L, 2024)).thenReturn(3);

        int savedCount = annualXbrlPipelineFacadeService.processAnnualMetricsFromXbrl(1L, 2024, "CFS");

        assertThat(savedCount).isEqualTo(11);
        verify(financialService).getOrCreateCompanyWithStockCode(1L);
        verify(annualXbrlRunPolicy).resolveAnnualProcessingFsDiv(7L, 2024, "CFS");
        verify(annualXbrlMetricProcessor).processAnnualMetricsFromXbrl(7L, 2024, "CFS");
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
        DartFsFiling filing = DartFsFiling.builder()
                .fsFilingId(21L)
                .rceptNo("20250311001234")
                .reprtCode("11011")
                .bsnsYear(2024)
                .fsDiv("CFS")
                .build();

        when(financialService.getOrCreateCompanyWithStockCode(1L)).thenReturn(company);
        when(annualXbrlCollector.collectAnnualFilingMetadata(company, 2024, "CFS")).thenReturn(filing);

        Long filingId = annualXbrlPipelineFacadeService.collectAnnualFilingMetadata(1L, 2024, "CFS");

        assertThat(filingId).isEqualTo(21L);
        verify(financialService).getOrCreateCompanyWithStockCode(1L);
        verify(annualXbrlCollector).collectAnnualFilingMetadata(company, 2024, "CFS");
    }

    @Test
    @DisplayName("XBRL 연간 파이프라인은 raw 수집 후 metric 전체 처리를 순서대로 실행한다.")
    void runAnnualXbrlPipeline_collectsAndProcessesMetrics() {
        Company company = Company.builder().companyId(7L).currency("KRW").build();
        when(financialService.getOrCreateCompanyWithStockCode(1L)).thenReturn(company);
        when(annualXbrlCollector.collectAnnualInputs(company, "00126380", "20240321000001", 2024, "CFS"))
                .thenReturn(99L);
        when(annualXbrlMetricProcessor.processAnnualMetricsFromXbrl(7L, 2024, "CFS")).thenReturn(8);
        when(financialMetricService.rebuildAnnualSupplementalMetricsFromXbrl(7L, 2024)).thenReturn(3);

        Long documentId = annualXbrlPipelineFacadeService.runAnnualXbrlPipeline(
                1L,
                "00126380",
                "20240321000001",
                2024,
                "CFS"
        );

        assertThat(documentId).isEqualTo(99L);

        verify(financialService).getOrCreateCompanyWithStockCode(1L);
        verify(annualXbrlCollector).collectAnnualInputs(company, "00126380", "20240321000001", 2024, "CFS");
        verify(annualXbrlMetricProcessor).processAnnualMetricsFromXbrl(7L, 2024, "CFS");
        verify(financialMetricService).rebuildAnnualSupplementalMetricsFromXbrl(7L, 2024);
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
        DartFsFiling filing = DartFsFiling.builder()
                .fsFilingId(21L)
                .rceptNo("20240321000001")
                .reprtCode("11011")
                .bsnsYear(2024)
                .fsDiv("CFS")
                .build();
        XbrlAnnualDocumentRef documentRef = new XbrlAnnualDocumentRef("00126380", "20240321000001", 2024, "CFS");

        when(financialService.getOrCreateCompanyWithStockCode(1L)).thenReturn(company);
        when(annualXbrlCollector.collectAnnualFilingMetadata(company, 2024, "CFS")).thenReturn(filing);
        when(xbrlAnnualDocumentLocator.resolve(7L, 2024, "CFS")).thenReturn(documentRef);
        when(annualXbrlCollector.collectAnnualInputs(company, "00126380", "20240321000001", 2024, "CFS"))
                .thenReturn(99L);
        when(annualXbrlMetricProcessor.processAnnualMetricsFromXbrl(7L, 2024, "CFS")).thenReturn(8);
        when(financialMetricService.rebuildAnnualSupplementalMetricsFromXbrl(7L, 2024)).thenReturn(3);

        Long documentId = annualXbrlPipelineFacadeService.runAnnualXbrlPipeline(1L, 2024, "CFS");

        assertThat(documentId).isEqualTo(99L);
        verify(financialService).getOrCreateCompanyWithStockCode(1L);
        verify(annualXbrlCollector).collectAnnualFilingMetadata(company, 2024, "CFS");
        verify(xbrlAnnualDocumentLocator).resolve(7L, 2024, "CFS");
        verify(annualXbrlCollector).collectAnnualInputs(company, "00126380", "20240321000001", 2024, "CFS");
        verify(annualXbrlMetricProcessor).processAnnualMetricsFromXbrl(7L, 2024, "CFS");
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
        DartFsFiling filing = DartFsFiling.builder()
                .fsFilingId(21L)
                .rceptNo("20240321000001")
                .reprtCode("11011")
                .bsnsYear(2024)
                .fsDiv("OFS")
                .build();
        XbrlAnnualDocumentRef documentRef = new XbrlAnnualDocumentRef("00126380", "20240321000001", 2024, "OFS");

        CustomException disclosureNotFound = new CustomException(CrawlingError.DART_DISCLOSURE_NOT_FOUND);

        when(financialService.getOrCreateCompanyWithStockCode(1L)).thenReturn(company);
        when(annualXbrlCollector.collectAnnualFilingMetadata(company, 2024, "CFS"))
                .thenThrow(disclosureNotFound);
        when(annualXbrlRunPolicy.shouldFallbackToOfs("CFS", disclosureNotFound)).thenReturn(true);
        when(annualXbrlCollector.collectAnnualFilingMetadata(company, 2024, "OFS")).thenReturn(filing);
        when(xbrlAnnualDocumentLocator.resolve(7L, 2024, "OFS")).thenReturn(documentRef);
        when(annualXbrlCollector.collectAnnualInputs(company, "00126380", "20240321000001", 2024, "OFS"))
                .thenReturn(99L);
        when(annualXbrlMetricProcessor.processAnnualMetricsFromXbrl(7L, 2024, "OFS")).thenReturn(8);
        when(financialMetricService.rebuildAnnualSupplementalMetricsFromXbrl(7L, 2024)).thenReturn(3);

        Long documentId = annualXbrlPipelineFacadeService.runAnnualXbrlPipeline(1L, 2024, "CFS");

        assertThat(documentId).isEqualTo(99L);
        verify(annualXbrlCollector).collectAnnualFilingMetadata(company, 2024, "CFS");
        verify(annualXbrlCollector).collectAnnualFilingMetadata(company, 2024, "OFS");
        verify(xbrlAnnualDocumentLocator).resolve(7L, 2024, "OFS");
        verify(annualXbrlCollector).collectAnnualInputs(company, "00126380", "20240321000001", 2024, "OFS");
        verify(annualXbrlMetricProcessor).processAnnualMetricsFromXbrl(7L, 2024, "OFS");
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
        DartFsFiling cfsFiling = DartFsFiling.builder().fsFilingId(21L).rceptNo("20240321000001").reprtCode("11011").bsnsYear(2024).fsDiv("CFS").build();
        DartFsFiling ofsFiling = DartFsFiling.builder().fsFilingId(22L).rceptNo("20240321000002").reprtCode("11011").bsnsYear(2024).fsDiv("OFS").build();
        XbrlAnnualDocumentRef cfsDocumentRef = new XbrlAnnualDocumentRef("00126380", "20240321000001", 2024, "CFS");
        XbrlAnnualDocumentRef ofsDocumentRef = new XbrlAnnualDocumentRef("00126380", "20240321000002", 2024, "OFS");

        when(financialService.getOrCreateCompanyWithStockCode(1L)).thenReturn(company);
        when(annualXbrlCollector.collectAnnualFilingMetadata(company, 2024, "CFS")).thenReturn(cfsFiling);
        when(annualXbrlCollector.collectAnnualFilingMetadata(company, 2024, "OFS")).thenReturn(ofsFiling);
        when(xbrlAnnualDocumentLocator.resolve(7L, 2024, "CFS")).thenReturn(cfsDocumentRef);
        when(xbrlAnnualDocumentLocator.resolve(7L, 2024, "OFS")).thenReturn(ofsDocumentRef);
        CustomException missingXbrl = new CustomException(CrawlingError.DART_XBRL_NOT_AVAILABLE);
        when(annualXbrlCollector.collectAnnualInputs(company, "00126380", "20240321000001", 2024, "CFS"))
                .thenThrow(missingXbrl);
        when(annualXbrlRunPolicy.shouldFallbackToOfsOnMissingXbrl("CFS", "CFS", missingXbrl)).thenReturn(true);
        when(annualXbrlCollector.collectAnnualInputs(company, "00126380", "20240321000002", 2024, "OFS"))
                .thenReturn(99L);
        when(annualXbrlMetricProcessor.processAnnualMetricsFromXbrl(7L, 2024, "OFS")).thenReturn(8);
        when(financialMetricService.rebuildAnnualSupplementalMetricsFromXbrl(7L, 2024)).thenReturn(3);

        Long documentId = annualXbrlPipelineFacadeService.runAnnualXbrlPipeline(1L, 2024, "CFS");

        assertThat(documentId).isEqualTo(99L);
        verify(annualXbrlCollector).collectAnnualFilingMetadata(company, 2024, "CFS");
        verify(xbrlAnnualDocumentLocator).resolve(7L, 2024, "CFS");
        verify(annualXbrlCollector).collectAnnualFilingMetadata(company, 2024, "OFS");
        verify(xbrlAnnualDocumentLocator).resolve(7L, 2024, "OFS");
        verify(annualXbrlMetricProcessor).processAnnualMetricsFromXbrl(7L, 2024, "OFS");
    }

    @Test
    @DisplayName("연간 XBRL 범위 run은 마지막에 현재 연도 주가를 오늘까지 추가 수집한다.")
    void runAnnualXbrlPipeline_range_backfillsCurrentYearPrice() {
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
        DartFsFiling filing = DartFsFiling.builder()
                .fsFilingId(21L)
                .rceptNo("20240321000001")
                .reprtCode("11011")
                .bsnsYear(2024)
                .fsDiv("CFS")
                .build();
        XbrlAnnualDocumentRef documentRef = new XbrlAnnualDocumentRef("00126380", "20240321000001", 2024, "CFS");

        when(financialService.getOrCreateCompanyWithStockCode(1L)).thenReturn(company);
        when(annualXbrlCollector.collectAnnualFilingMetadata(company, 2024, "CFS")).thenReturn(filing);
        when(xbrlAnnualDocumentLocator.resolve(7L, 2024, "CFS")).thenReturn(documentRef);
        when(annualXbrlCollector.collectAnnualInputs(company, "00126380", "20240321000001", 2024, "CFS"))
                .thenReturn(99L);
        when(annualXbrlRunPolicy.resolveAnnualProcessingFsDiv(7L, 2024, "CFS")).thenReturn("CFS");
        when(annualXbrlMetricProcessor.processAnnualMetricsFromXbrl(7L, 2024, "CFS")).thenReturn(8);
        when(financialMetricService.rebuildAnnualSupplementalMetricsFromXbrl(7L, 2024)).thenReturn(3);

        int completedYears = annualXbrlPipelineFacadeService.runAnnualXbrlPipeline(1L, 2024, 2024, "CFS");

        assertThat(completedYears).isEqualTo(1);
        verify(annualXbrlCollector).collectAnnualInputs(company, "00126380", "20240321000001", 2024, "CFS");
        verify(annualXbrlCollector).collectCurrentYearPriceData(company);
    }
}
