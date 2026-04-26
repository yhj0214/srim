package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.service.dto.FsRawBundle;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class AnnualXbrlMetricProcessor {
    private final AnnualXbrlBaseMetricCalculator annualXbrlBaseMetricCalculator;
    private final AnnualXbrlDerivedMetricCalculator annualXbrlDerivedMetricCalculator;
    private final AnnualXbrlPerShareMetricCalculator annualXbrlPerShareMetricCalculator;
    private final FinancialService financialService;

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> buildAnnualBaseMetricsFromXbrl(Long companyId, int fiscalYear, String fsDiv) {
        return buildStageMetrics(companyId, fiscalYear, fsDiv,
                rawBundle -> annualXbrlBaseMetricCalculator.calculate(rawBundle.curr()));
    }

    @Transactional
    public int replaceAnnualBaseMetricsFromXbrl(Long companyId, int fiscalYear, String fsDiv) {
        return replaceStageMetrics(
                companyId,
                fiscalYear,
                buildAnnualBaseMetricsFromXbrl(companyId, fiscalYear, fsDiv),
                MetricStage.BASE
        );
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> buildAnnualDerivedMetricsFromXbrl(Long companyId, int fiscalYear, String fsDiv) {
        return buildStageMetrics(companyId, fiscalYear, fsDiv,
                rawBundle -> annualXbrlDerivedMetricCalculator.calculate(rawBundle.curr(), rawBundle.prev(), fiscalYear));
    }

    @Transactional
    public int replaceAnnualDerivedMetricsFromXbrl(Long companyId, int fiscalYear, String fsDiv) {
        return replaceStageMetrics(
                companyId,
                fiscalYear,
                buildAnnualDerivedMetricsFromXbrl(companyId, fiscalYear, fsDiv),
                MetricStage.DERIVED
        );
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> buildAnnualPerShareMetricsFromXbrl(Long companyId, int fiscalYear, String fsDiv) {
        return buildStageMetrics(companyId, fiscalYear, fsDiv,
                rawBundle -> annualXbrlPerShareMetricCalculator.calculate(companyId, rawBundle.curr(), fiscalYear));
    }

    @Transactional
    public int replaceAnnualPerShareMetricsFromXbrl(Long companyId, int fiscalYear, String fsDiv) {
        return replaceStageMetrics(
                companyId,
                fiscalYear,
                buildAnnualPerShareMetricsFromXbrl(companyId, fiscalYear, fsDiv),
                MetricStage.PER_SHARE
        );
    }

    @Transactional(readOnly = true)
    public boolean hasAnnualXbrlRaw(Long companyId, int fiscalYear, String fsDiv) {
        return !financialService.loadXbrlRawBundle(companyId, fiscalYear, fsDiv).curr().isEmpty();
    }

    public int processAnnualMetricsFromXbrl(Long companyId, int fiscalYear, String fsDiv) {
        int savedCount = 0;
        savedCount += replaceAnnualBaseMetricsFromXbrl(companyId, fiscalYear, fsDiv);
        savedCount += replaceAnnualDerivedMetricsFromXbrl(companyId, fiscalYear, fsDiv);
        savedCount += replaceAnnualPerShareMetricsFromXbrl(companyId, fiscalYear, fsDiv);
        return savedCount;
    }

    private Map<String, BigDecimal> buildStageMetrics(Long companyId,
                                                      int fiscalYear,
                                                      String fsDiv,
                                                      Function<FsRawBundle, Map<String, BigDecimal>> calculator) {
        FsRawBundle rawBundle = financialService.loadXbrlRawBundle(companyId, fiscalYear, fsDiv);
        return calculator.apply(rawBundle);
    }

    private int replaceStageMetrics(Long companyId,
                                    int fiscalYear,
                                    Map<String, BigDecimal> metrics,
                                    MetricStage stage) {
        return financialService.replaceMetrics(companyId, fiscalYear, stage, metrics);
    }
}
