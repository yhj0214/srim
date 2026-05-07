package org.yhj.srim.service.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.DartFsFiling;
import org.yhj.srim.repository.entity.StockCode;
import org.yhj.srim.service.domain.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnualXbrlPipelineFacadeServiceTest {

    @InjectMocks
    AnnualXbrlPipelineOrchestrator annualXbrlPipelineFacadeService;

    @Mock
    AnnualXbrlCollector annualXbrlCollector;

    @Mock
    AnnualXbrlExecutionService annualXbrlExecutionService;

    @Mock
    AnnualXbrlRunPolicy annualXbrlRunPolicy;

    @Mock
    FinancialService financialService;

    @Mock
    AnnualXbrlMetricProcessor annualXbrlMetricProcessor;

    @Mock
    FinancialMetricService financialMetricService;

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
        when(financialService.getOrCreateCompanyWithStockCode(1L)).thenReturn(company);
        when(annualXbrlExecutionService.collectAnnualInputs(1L, company, 2024, "CFS"))
                .thenReturn(new AnnualXbrlExecutionService.ExecutionResult(99L, "CFS"));
        when(annualXbrlRunPolicy.resolveAnnualProcessingFsDiv(7L, 2024, "CFS")).thenReturn("CFS");
        when(annualXbrlMetricProcessor.processAnnualMetricsFromXbrl(7L, 2024, "CFS")).thenReturn(8);
        when(financialMetricService.rebuildAnnualSupplementalMetricsFromXbrl(7L, 2024)).thenReturn(3);

        int completedYears = annualXbrlPipelineFacadeService.runAnnualXbrlPipeline(1L, 2024, 2024, "CFS");

        assertThat(completedYears).isEqualTo(1);
        verify(annualXbrlExecutionService).collectAnnualInputs(1L, company, 2024, "CFS");
        verify(annualXbrlCollector).collectCurrentYearPriceData(company);
    }
}
