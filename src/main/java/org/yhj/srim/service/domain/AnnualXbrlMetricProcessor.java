package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.service.dto.FsRawBundle;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnnualXbrlMetricProcessor {
    private final FinancialService financialService;

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> buildAnnualBaseMetricsFromXbrl(Long companyId, int fiscalYear, String fsDiv) {
        FsRawBundle rawBundle = financialService.loadXbrlRawBundle(companyId, fiscalYear, fsDiv);
        return financialService.buildMetrics(companyId, fiscalYear, rawBundle, MetricStage.BASE);
    }

    @Transactional
    public int replaceAnnualBaseMetricsFromXbrl(Long companyId, int fiscalYear, String fsDiv) {
        Map<String, BigDecimal> metrics = buildAnnualBaseMetricsFromXbrl(companyId, fiscalYear, fsDiv);
        return financialService.replaceMetrics(companyId, fiscalYear, MetricStage.BASE, metrics);
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> buildAnnualDerivedMetricsFromXbrl(Long companyId, int fiscalYear, String fsDiv) {
        FsRawBundle rawBundle = financialService.loadXbrlRawBundle(companyId, fiscalYear, fsDiv);
        return financialService.buildMetrics(companyId, fiscalYear, rawBundle, MetricStage.DERIVED);
    }

    @Transactional
    public int replaceAnnualDerivedMetricsFromXbrl(Long companyId, int fiscalYear, String fsDiv) {
        Map<String, BigDecimal> metrics = buildAnnualDerivedMetricsFromXbrl(companyId, fiscalYear, fsDiv);
        return financialService.replaceMetrics(companyId, fiscalYear, MetricStage.DERIVED, metrics);
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> buildAnnualPerShareMetricsFromXbrl(Long companyId, int fiscalYear, String fsDiv) {
        FsRawBundle rawBundle = financialService.loadXbrlRawBundle(companyId, fiscalYear, fsDiv);
        return financialService.buildMetrics(companyId, fiscalYear, rawBundle, MetricStage.PER_SHARE);
    }

    @Transactional
    public int replaceAnnualPerShareMetricsFromXbrl(Long companyId, int fiscalYear, String fsDiv) {
        Map<String, BigDecimal> metrics = buildAnnualPerShareMetricsFromXbrl(companyId, fiscalYear, fsDiv);
        return financialService.replaceMetrics(companyId, fiscalYear, MetricStage.PER_SHARE, metrics);
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
}
