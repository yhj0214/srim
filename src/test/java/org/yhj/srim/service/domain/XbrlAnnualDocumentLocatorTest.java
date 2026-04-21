package org.yhj.srim.service.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.repository.CompanyRepository;
import org.yhj.srim.repository.DartFsFilingRepository;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.DartFsFiling;
import org.yhj.srim.repository.entity.StockCode;
import org.yhj.srim.service.dto.XbrlAnnualDocumentRef;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XbrlAnnualDocumentLocatorTest {

    @InjectMocks
    XbrlAnnualDocumentLocator xbrlAnnualDocumentLocator;

    @Mock
    CompanyRepository companyRepository;

    @Mock
    DartFsFilingRepository dartFsFilingRepository;

    @Test
    @DisplayName("회사와 filing 정보가 있으면 연간 XBRL 문서 참조 정보를 반환한다.")
    void resolve_returnsAnnualDocumentRef() {
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
                .companyId(7L)
                .corpCode("00126380")
                .rceptNo("20240321000001")
                .reprtCode("11011")
                .bsnsYear(2024)
                .fsDiv("CFS")
                .build();

        when(companyRepository.findWithStockCodeByCompanyId(7L)).thenReturn(Optional.of(company));
        when(dartFsFilingRepository.findTopByCompanyIdAndBsnsYearAndReprtCodeAndFsDivOrderByRceptDtDescRceptNoDesc(
                7L, 2024, "11011", "CFS"
        )).thenReturn(Optional.of(filing));

        XbrlAnnualDocumentRef documentRef = xbrlAnnualDocumentLocator.resolve(7L, 2024, "CFS");

        assertThat(documentRef.corpCode()).isEqualTo("00126380");
        assertThat(documentRef.rceptNo()).isEqualTo("20240321000001");
        assertThat(documentRef.fiscalYear()).isEqualTo(2024);
        assertThat(documentRef.fsDiv()).isEqualTo("CFS");
    }

    @Test
    @DisplayName("연간 filing 정보가 없으면 예외를 던진다.")
    void resolve_withoutAnnualFiling_throwsException() {
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

        when(companyRepository.findWithStockCodeByCompanyId(7L)).thenReturn(Optional.of(company));
        when(dartFsFilingRepository.findTopByCompanyIdAndBsnsYearAndReprtCodeAndFsDivOrderByRceptDtDescRceptNoDesc(
                7L, 2024, "11011", "CFS"
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> xbrlAnnualDocumentLocator.resolve(7L, 2024, "CFS"))
                .isInstanceOf(CustomException.class);
    }
}
