package org.yhj.srim.service.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.service.domain.FinancialService;
import org.yhj.srim.service.domain.QuarterXbrlCollector;
import org.yhj.srim.service.domain.QuarterXbrlMetricProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuarterXbrlPipelineOrchestratorTest {

    @InjectMocks
    QuarterXbrlPipelineOrchestrator quarterXbrlPipelineOrchestrator;

    @Mock
    QuarterXbrlCollector quarterXbrlCollector;

    @Mock
    QuarterXbrlMetricProcessor quarterXbrlMetricProcessor;

    @Mock
    FinancialService financialService;

    @Test
    @DisplayName("분기 XBRL run skeleton은 raw 수집 후 metric 처리를 순서대로 위임한다.")
    void runQuarterXbrlPipeline_delegatesToCollectorAndProcessor() {
        Company company = Company.builder()
                .companyId(7L)
                .currency("KRW")
                .build();
        when(financialService.getOrCreateCompanyWithStockCode(1L)).thenReturn(company);
        when(quarterXbrlCollector.collectQuarterInputs(company, 2024, 1, "CFS")).thenReturn(99L);
        when(quarterXbrlMetricProcessor.processQuarterMetricsFromXbrl(7L, 2024, 1, "CFS")).thenReturn(8);

        int savedMetricCount = quarterXbrlPipelineOrchestrator.runQuarterXbrlPipeline(1L, 2024, 1, "CFS");

        assertThat(savedMetricCount).isEqualTo(8);
        verify(quarterXbrlCollector).collectQuarterInputs(company, 2024, 1, "CFS");
        verify(quarterXbrlMetricProcessor).processQuarterMetricsFromXbrl(7L, 2024, 1, "CFS");
    }
}
