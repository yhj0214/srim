package org.yhj.srim.service.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yhj.srim.client.DartReportType;
import org.yhj.srim.client.dto.DartFilingRow;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.StockError;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.DartFsFiling;
import org.yhj.srim.repository.entity.StockCode;
import org.yhj.srim.service.application.dto.CollectXbrlRawCommand;
import org.yhj.srim.service.crawl.DartCrawlingService;
import org.yhj.srim.service.crawl.XbrlFinancialStatementCrawlingService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuarterXbrlCollectorTest {

    @InjectMocks
    QuarterXbrlCollector quarterXbrlCollector;

    @Mock
    DartCrawlingService dartCrawlingService;

    @Mock
    DartFsFilingService dartFsFilingService;

    @Mock
    XbrlFinancialStatementCrawlingService xbrlFinancialStatementCrawlingService;

    @Mock
    XbrlRawService xbrlRawService;

    @Test
    @DisplayName("이미 저장된 분기 XBRL 문서가 있으면 다운로드 없이 기존 문서 ID를 반환한다.")
    void collectXbrlRaw_returnsExistingDocumentId() {
        CollectXbrlRawCommand command = new CollectXbrlRawCommand(
                1L, "00126380", "20240516000001", 2024, DartReportType.FIRST_QUARTER, "CFS"
        );
        when(xbrlRawService.findStoredDocumentId("20240516000001", "11013", "CFS"))
                .thenReturn(Optional.of(99L));

        Long documentId = quarterXbrlCollector.collectXbrlRaw(command);

        assertThat(documentId).isEqualTo(99L);
        verify(xbrlRawService).findStoredDocumentId("20240516000001", "11013", "CFS");
        verifyNoInteractions(xbrlFinancialStatementCrawlingService);
    }

    @Test
    @DisplayName("분기 filing 메타 수집은 보고서 유형에 맞는 최신 공시 메타를 조회해 저장한다.")
    void collectQuarterFilingMetadata_savesLatestQuarterFiling() {
        StockCode stockCode = StockCode.builder().dartCorpCode("00126380").build();
        Company company = Company.builder().companyId(7L).stockCode(stockCode).build();
        DartFilingRow filingRow = new DartFilingRow();
        filingRow.setRceptNo("20240516000001");
        DartFsFiling filing = DartFsFiling.builder().fsFilingId(21L).build();

        when(dartCrawlingService.crawlLatestFiling("00126380", 2024, DartReportType.FIRST_QUARTER)).thenReturn(filingRow);
        when(dartFsFilingService.saveFilingMetadata("00126380", 7L, 2024, filingRow, DartReportType.FIRST_QUARTER, "CFS"))
                .thenReturn(filing);

        DartFsFiling saved = quarterXbrlCollector.collectQuarterFilingMetadata(
                company, 2024, DartReportType.FIRST_QUARTER, "CFS"
        );

        assertThat(saved).isSameAs(filing);
        verify(dartCrawlingService).crawlLatestFiling("00126380", 2024, DartReportType.FIRST_QUARTER);
        verify(dartFsFilingService).saveFilingMetadata("00126380", 7L, 2024, filingRow, DartReportType.FIRST_QUARTER, "CFS");
    }

    @Test
    @DisplayName("corpCode가 없으면 분기 filing 메타 수집은 예외를 던진다.")
    void collectQuarterFilingMetadata_throwsWhenCorpCodeInvalid() {
        Company company = Company.builder()
                .companyId(7L)
                .stockCode(StockCode.builder().dartCorpCode("123").build())
                .build();

        assertThatThrownBy(() -> quarterXbrlCollector.collectQuarterFilingMetadata(
                company, 2024, DartReportType.FIRST_QUARTER, "CFS"
        ))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(StockError.DART_CORP_CODE_INVALID.getMessage());
    }

    @Test
    @DisplayName("분기 입력 수집은 filing 메타를 저장한 뒤 해당 보고서 유형의 XBRL raw를 수집한다.")
    void collectQuarterInputs_collectsQuarterRaw() {
        StockCode stockCode = StockCode.builder().dartCorpCode("00126380").build();
        Company company = Company.builder().companyId(7L).stockCode(stockCode).build();
        DartFilingRow filingRow = new DartFilingRow();
        filingRow.setRceptNo("20240516000001");
        DartFsFiling filing = DartFsFiling.builder()
                .corpCode("00126380")
                .rceptNo("20240516000001")
                .fsFilingId(21L)
                .build();
        XbrlFinancialStatementCrawlingService.XbrlRawBatch batch =
                mock(XbrlFinancialStatementCrawlingService.XbrlRawBatch.class);

        when(dartCrawlingService.crawlLatestFiling("00126380", 2024, DartReportType.FIRST_QUARTER)).thenReturn(filingRow);
        when(dartFsFilingService.saveFilingMetadata("00126380", 7L, 2024, filingRow, DartReportType.FIRST_QUARTER, "CFS"))
                .thenReturn(filing);
        when(xbrlRawService.findStoredDocumentId("20240516000001", "11013", "CFS")).thenReturn(Optional.empty());
        when(xbrlFinancialStatementCrawlingService.crawlFinancialStatementsXbrl(
                "00126380", "20240516000001", 2024, DartReportType.FIRST_QUARTER, "CFS"
        )).thenReturn(batch);
        when(xbrlRawService.saveFinancialStatementsXbrl("00126380", 7L, batch)).thenReturn(99L);

        Long documentId = quarterXbrlCollector.collectQuarterInputs(company, 2024, 1, "CFS");

        assertThat(documentId).isEqualTo(99L);
        verify(dartCrawlingService).crawlLatestFiling("00126380", 2024, DartReportType.FIRST_QUARTER);
        verify(dartFsFilingService).saveFilingMetadata("00126380", 7L, 2024, filingRow, DartReportType.FIRST_QUARTER, "CFS");
        verify(xbrlRawService).findStoredDocumentId("20240516000001", "11013", "CFS");
        verify(xbrlFinancialStatementCrawlingService).crawlFinancialStatementsXbrl(
                "00126380", "20240516000001", 2024, DartReportType.FIRST_QUARTER, "CFS"
        );
        verify(xbrlRawService).saveFinancialStatementsXbrl("00126380", 7L, batch);
    }
}
