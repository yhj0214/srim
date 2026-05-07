package org.yhj.srim.service.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yhj.srim.repository.entity.FinPeriod;
import org.yhj.srim.service.domain.calculator.AnnualXbrlBaseMetricCalculator;
import org.yhj.srim.service.domain.calculator.AnnualXbrlDerivedMetricCalculator;
import org.yhj.srim.service.domain.calculator.AnnualXbrlPerShareMetricCalculator;
import org.yhj.srim.service.dto.FsRawBundle;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuarterXbrlMetricProcessorTest {

    @InjectMocks
    QuarterXbrlMetricProcessor quarterXbrlMetricProcessor;

    @Mock
    FinancialService financialService;

    @Mock
    AnnualXbrlBaseMetricCalculator annualXbrlBaseMetricCalculator;

    @Mock
    AnnualXbrlDerivedMetricCalculator annualXbrlDerivedMetricCalculator;

    @Mock
    AnnualXbrlPerShareMetricCalculator annualXbrlPerShareMetricCalculator;

    @Test
    @DisplayName("분기 XBRL metric processor는 base/derived/per-share 저장을 순서대로 위임한다.")
    void processQuarterMetricsFromXbrl_delegatesStagesInOrder() {
        FsRawBundle rawBundle = new FsRawBundle(new LinkedHashMap<>(), new LinkedHashMap<>());
        Map<String, BigDecimal> metrics = Map.of();
        FinPeriod period = FinPeriod.builder()
                .periodId(11L)
                .fiscalYear(2024)
                .fiscalQuarter(1)
                .periodType("QTR")
                .build();

        when(financialService.loadQuarterXbrlRawBundle(7L, 2024, 1, "CFS")).thenReturn(rawBundle);
        when(financialService.findQuarterPeriod(7L, 2024, 1)).thenReturn(Optional.of(period));
        when(annualXbrlBaseMetricCalculator.calculate(rawBundle.curr())).thenReturn(metrics);
        when(annualXbrlDerivedMetricCalculator.calculate(rawBundle.curr(), rawBundle.prev(), 2024)).thenReturn(metrics);
        when(annualXbrlPerShareMetricCalculator.calculate(7L, rawBundle.curr(), 2024)).thenReturn(metrics);
        when(financialService.replaceMetrics(7L, period, MetricStage.BASE, metrics)).thenReturn(4);
        when(financialService.replaceMetrics(7L, period, MetricStage.DERIVED, metrics)).thenReturn(3);
        when(financialService.replaceMetrics(7L, period, MetricStage.PER_SHARE, metrics)).thenReturn(1);

        int savedCount = quarterXbrlMetricProcessor.processQuarterMetricsFromXbrl(7L, 2024, 1, "CFS");

        assertThat(savedCount).isEqualTo(8);
        verify(financialService).replaceMetrics(7L, period, MetricStage.BASE, metrics);
        verify(financialService).replaceMetrics(7L, period, MetricStage.DERIVED, metrics);
        verify(financialService).replaceMetrics(7L, period, MetricStage.PER_SHARE, metrics);
    }
}
