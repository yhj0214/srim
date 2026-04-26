package org.yhj.srim.service.facade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yhj.srim.client.DartReportType;
import org.yhj.srim.client.dto.DartFilingRow;
import org.yhj.srim.client.dto.DartShareStatusRow;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.StockError;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.DartFsFiling;
import org.yhj.srim.repository.entity.StockCode;
import org.yhj.srim.service.crawl.DartCrawlingService;
import org.yhj.srim.service.crawl.XbrlFinancialStatementCrawlingService;
import org.yhj.srim.service.domain.DartFsFilingService;
import org.yhj.srim.service.domain.StockService;
import org.yhj.srim.service.domain.XbrlRawService;
import org.yhj.srim.service.facade.dto.CollectXbrlRawCommand;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnualXbrlCollectorTest {

    @InjectMocks
    AnnualXbrlCollector annualXbrlCollector;

    @Mock
    DartCrawlingService dartCrawlingService;

    @Mock
    XbrlFinancialStatementCrawlingService xbrlFinancialStatementCrawlingService;

    @Mock
    StockService stockService;

    @Mock
    DartFsFilingService dartFsFilingService;

    @Mock
    XbrlRawService xbrlRawService;

    @Mock
    PriceChartFacadeService priceChartFacadeService;

    @Test
    @DisplayName("이미 저장된 XBRL 문서가 있으면 다운로드 없이 기존 문서 ID를 반환한다.")
    void collectXbrlRaw_returnsExistingDocumentId() {
        CollectXbrlRawCommand command = new CollectXbrlRawCommand(
                1L, "00126380", "20240321000001", 2024, DartReportType.ANNUAL, "CFS"
        );
        when(xbrlRawService.findStoredDocumentId("20240321000001", "11011", "CFS"))
                .thenReturn(Optional.of(99L));

        Long documentId = annualXbrlCollector.collectXbrlRaw(command);

        assertThat(documentId).isEqualTo(99L);
        verify(xbrlRawService).findStoredDocumentId("20240321000001", "11011", "CFS");
        verifyNoInteractions(xbrlFinancialStatementCrawlingService);
    }

    @Test
    @DisplayName("연간 filing 메타 수집은 최신 사업보고서 메타를 조회해 저장한다.")
    void collectAnnualFilingMetadata_savesLatestAnnualFiling() {
        StockCode stockCode = StockCode.builder().dartCorpCode("00126380").build();
        Company company = Company.builder().companyId(7L).stockCode(stockCode).build();
        DartFilingRow filingRow = new DartFilingRow();
        filingRow.setRceptNo("20250311001234");
        DartFsFiling filing = DartFsFiling.builder().fsFilingId(21L).build();

        when(dartCrawlingService.crawlLatestAnnualFiling("00126380", 2024)).thenReturn(filingRow);
        when(dartFsFilingService.saveAnnualFilingMetadata("00126380", 7L, 2024, filingRow, "CFS")).thenReturn(filing);

        DartFsFiling saved = annualXbrlCollector.collectAnnualFilingMetadata(company, 2024, "CFS");

        assertThat(saved).isSameAs(filing);
        verify(dartCrawlingService).crawlLatestAnnualFiling("00126380", 2024);
        verify(dartFsFilingService).saveAnnualFilingMetadata("00126380", 7L, 2024, filingRow, "CFS");
    }

    @Test
    @DisplayName("corpCode가 없으면 연간 filing 메타 수집은 예외를 던진다.")
    void collectAnnualFilingMetadata_throwsWhenCorpCodeInvalid() {
        Company company = Company.builder()
                .companyId(7L)
                .stockCode(StockCode.builder().dartCorpCode("123").build())
                .build();

        assertThatThrownBy(() -> annualXbrlCollector.collectAnnualFilingMetadata(company, 2024, "CFS"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(StockError.DART_CORP_CODE_INVALID.getMessage());
    }

    @Test
    @DisplayName("연간 입력 수집은 주식수, 주가, XBRL raw 수집을 순서대로 수행한다.")
    void collectAnnualInputs_collectsSharePriceAndRaw() {
        Company company = Company.builder().companyId(7L).build();
        List<DartShareStatusRow> shareStatusRows = List.of(mock(DartShareStatusRow.class));
        CollectXbrlRawCommand command = new CollectXbrlRawCommand(
                7L, "00126380", "20240321000001", 2024, DartReportType.ANNUAL, "CFS"
        );

        when(dartCrawlingService.crawlShareStatus("00126380", 2024)).thenReturn(shareStatusRows);
        when(xbrlRawService.findStoredDocumentId("20240321000001", "11011", "CFS"))
                .thenReturn(Optional.of(99L));

        Long documentId = annualXbrlCollector.collectAnnualInputs(company, "00126380", "20240321000001", 2024, "CFS");

        assertThat(documentId).isEqualTo(99L);
        verify(stockService).replaceShareStatus(company, 2024, shareStatusRows);
        verify(priceChartFacadeService).ensurePriceData(7L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        verify(xbrlRawService).findStoredDocumentId(command.rceptNo(), command.reportType().code(), command.fsDiv());
    }
}
