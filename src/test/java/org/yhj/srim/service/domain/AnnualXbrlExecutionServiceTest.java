package org.yhj.srim.service.domain;

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
import org.yhj.srim.service.domain.resolver.XbrlAnnualDocumentLocator;
import org.yhj.srim.service.dto.XbrlAnnualDocumentRef;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnualXbrlExecutionServiceTest {

    @InjectMocks
    AnnualXbrlExecutionService annualXbrlExecutionService;

    @Mock
    AnnualXbrlCollector annualXbrlCollector;

    @Mock
    AnnualXbrlRunPolicy annualXbrlRunPolicy;

    @Mock
    XbrlAnnualDocumentLocator xbrlAnnualDocumentLocator;

    @Test
    @DisplayName("연간 입력 수집은 선택된 문서 기준으로 raw 수집을 수행하고 resolved fsDiv를 반환한다.")
    void collectAnnualInputs_returnsDocumentIdAndResolvedFsDiv() {
        Company company = company();
        DartFsFiling filing = DartFsFiling.builder()
                .fsFilingId(21L)
                .rceptNo("20240321000001")
                .reprtCode("11011")
                .bsnsYear(2024)
                .fsDiv("CFS")
                .build();
        XbrlAnnualDocumentRef documentRef = new XbrlAnnualDocumentRef("00126380", "20240321000001", 2024, "CFS");

        when(annualXbrlCollector.collectAnnualFilingMetadata(company, 2024, "CFS")).thenReturn(filing);
        when(xbrlAnnualDocumentLocator.resolve(7L, 2024, "CFS")).thenReturn(documentRef);
        when(annualXbrlCollector.collectAnnualInputs(company, "00126380", "20240321000001", 2024, "CFS"))
                .thenReturn(99L);

        AnnualXbrlExecutionService.ExecutionResult result =
                annualXbrlExecutionService.collectAnnualInputs(1L, company, 2024, "CFS");

        assertThat(result.documentId()).isEqualTo(99L);
        assertThat(result.resolvedFsDiv()).isEqualTo("CFS");
        verify(annualXbrlCollector).collectAnnualFilingMetadata(company, 2024, "CFS");
        verify(xbrlAnnualDocumentLocator).resolve(7L, 2024, "CFS");
        verify(annualXbrlCollector).collectAnnualInputs(company, "00126380", "20240321000001", 2024, "CFS");
    }

    @Test
    @DisplayName("CFS 메타 수집이 실패하면 OFS 문서로 fallback 한다.")
    void collectAnnualInputs_fallsBackToOfsWhenDisclosureMissing() {
        Company company = company();
        CustomException disclosureNotFound = new CustomException(CrawlingError.DART_DISCLOSURE_NOT_FOUND);
        DartFsFiling filing = DartFsFiling.builder()
                .fsFilingId(22L)
                .rceptNo("20240321000002")
                .reprtCode("11011")
                .bsnsYear(2024)
                .fsDiv("OFS")
                .build();
        XbrlAnnualDocumentRef documentRef = new XbrlAnnualDocumentRef("00126380", "20240321000002", 2024, "OFS");

        when(annualXbrlCollector.collectAnnualFilingMetadata(company, 2024, "CFS")).thenThrow(disclosureNotFound);
        when(annualXbrlRunPolicy.shouldFallbackToOfs("CFS", disclosureNotFound)).thenReturn(true);
        when(annualXbrlCollector.collectAnnualFilingMetadata(company, 2024, "OFS")).thenReturn(filing);
        when(xbrlAnnualDocumentLocator.resolve(7L, 2024, "OFS")).thenReturn(documentRef);
        when(annualXbrlCollector.collectAnnualInputs(company, "00126380", "20240321000002", 2024, "OFS"))
                .thenReturn(99L);

        AnnualXbrlExecutionService.ExecutionResult result =
                annualXbrlExecutionService.collectAnnualInputs(1L, company, 2024, "CFS");

        assertThat(result.documentId()).isEqualTo(99L);
        assertThat(result.resolvedFsDiv()).isEqualTo("OFS");
        verify(annualXbrlCollector).collectAnnualFilingMetadata(company, 2024, "CFS");
        verify(annualXbrlCollector).collectAnnualFilingMetadata(company, 2024, "OFS");
        verify(xbrlAnnualDocumentLocator).resolve(7L, 2024, "OFS");
    }

    @Test
    @DisplayName("CFS XBRL이 없으면 OFS 문서로 fallback 한다.")
    void collectAnnualInputs_fallsBackToOfsWhenXbrlMissing() {
        Company company = company();
        DartFsFiling cfsFiling = DartFsFiling.builder()
                .fsFilingId(21L)
                .rceptNo("20240321000001")
                .reprtCode("11011")
                .bsnsYear(2024)
                .fsDiv("CFS")
                .build();
        DartFsFiling ofsFiling = DartFsFiling.builder()
                .fsFilingId(22L)
                .rceptNo("20240321000002")
                .reprtCode("11011")
                .bsnsYear(2024)
                .fsDiv("OFS")
                .build();
        XbrlAnnualDocumentRef cfsDocumentRef = new XbrlAnnualDocumentRef("00126380", "20240321000001", 2024, "CFS");
        XbrlAnnualDocumentRef ofsDocumentRef = new XbrlAnnualDocumentRef("00126380", "20240321000002", 2024, "OFS");
        CustomException missingXbrl = new CustomException(CrawlingError.DART_XBRL_NOT_AVAILABLE);

        when(annualXbrlCollector.collectAnnualFilingMetadata(company, 2024, "CFS")).thenReturn(cfsFiling);
        when(annualXbrlCollector.collectAnnualFilingMetadata(company, 2024, "OFS")).thenReturn(ofsFiling);
        when(xbrlAnnualDocumentLocator.resolve(7L, 2024, "CFS")).thenReturn(cfsDocumentRef);
        when(xbrlAnnualDocumentLocator.resolve(7L, 2024, "OFS")).thenReturn(ofsDocumentRef);
        when(annualXbrlCollector.collectAnnualInputs(company, "00126380", "20240321000001", 2024, "CFS"))
                .thenThrow(missingXbrl);
        when(annualXbrlRunPolicy.shouldFallbackToOfsOnMissingXbrl("CFS", "CFS", missingXbrl)).thenReturn(true);
        when(annualXbrlCollector.collectAnnualInputs(company, "00126380", "20240321000002", 2024, "OFS"))
                .thenReturn(99L);

        AnnualXbrlExecutionService.ExecutionResult result =
                annualXbrlExecutionService.collectAnnualInputs(1L, company, 2024, "CFS");

        assertThat(result.documentId()).isEqualTo(99L);
        assertThat(result.resolvedFsDiv()).isEqualTo("OFS");
        verify(xbrlAnnualDocumentLocator).resolve(7L, 2024, "CFS");
        verify(annualXbrlCollector).collectAnnualFilingMetadata(company, 2024, "OFS");
        verify(xbrlAnnualDocumentLocator).resolve(7L, 2024, "OFS");
    }

    private Company company() {
        StockCode stockCode = StockCode.builder()
                .dartCorpCode("00126380")
                .tickerKrx("900001")
                .companyName("TEST")
                .market("TEST")
                .build();
        return Company.builder()
                .companyId(7L)
                .stockCode(stockCode)
                .currency("KRW")
                .build();
    }
}
