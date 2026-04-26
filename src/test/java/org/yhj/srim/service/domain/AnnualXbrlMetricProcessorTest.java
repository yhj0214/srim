package org.yhj.srim.service.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yhj.srim.service.dto.FsRawBundle;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnualXbrlMetricProcessorTest {

    @InjectMocks
    AnnualXbrlMetricProcessor annualXbrlMetricProcessor;

    @Mock
    FinancialService financialService;

    @Mock
    AnnualXbrlBaseMetricCalculator annualXbrlBaseMetricCalculator;

    @Mock
    AnnualXbrlDerivedMetricCalculator annualXbrlDerivedMetricCalculator;

    @Mock
    AnnualXbrlPerShareMetricCalculator annualXbrlPerShareMetricCalculator;

    @Test
    @DisplayName("연간 XBRL metric processor는 base/derived/per-share 저장을 순서대로 위임한다.")
    void processAnnualMetricsFromXbrl_delegatesStagesInOrder() {
        FsRawBundle rawBundle = new FsRawBundle(new LinkedHashMap<>(), new LinkedHashMap<>());
        Map<String, java.math.BigDecimal> metrics = Map.of();

        when(financialService.loadXbrlRawBundle(7L, 2024, "CFS")).thenReturn(rawBundle);
        when(annualXbrlBaseMetricCalculator.calculate(rawBundle.curr())).thenReturn(metrics);
        when(annualXbrlDerivedMetricCalculator.calculate(rawBundle.curr(), rawBundle.prev(), 2024)).thenReturn(metrics);
        when(annualXbrlPerShareMetricCalculator.calculate(7L, rawBundle.curr(), 2024)).thenReturn(metrics);
        when(financialService.replaceMetrics(7L, 2024, MetricStage.BASE, metrics)).thenReturn(4);
        when(financialService.replaceMetrics(7L, 2024, MetricStage.DERIVED, metrics)).thenReturn(3);
        when(financialService.replaceMetrics(7L, 2024, MetricStage.PER_SHARE, metrics)).thenReturn(1);

        int savedCount = annualXbrlMetricProcessor.processAnnualMetricsFromXbrl(7L, 2024, "CFS");

        assertThat(savedCount).isEqualTo(8);
        verify(financialService).replaceMetrics(7L, 2024, MetricStage.BASE, metrics);
        verify(financialService).replaceMetrics(7L, 2024, MetricStage.DERIVED, metrics);
        verify(financialService).replaceMetrics(7L, 2024, MetricStage.PER_SHARE, metrics);
    }
}
