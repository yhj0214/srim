package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yhj.srim.repository.entity.FinPeriod;
import org.yhj.srim.service.dto.FsRawBundle;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialMetricService {

    private final FinancialService financialService;

    private static final String METRIC_EQUITY_OWNER = "TOTAL_EQUITY_OWNER";
    private static final String METRIC_EPS = "EPS";
    private static final String METRIC_BPS = "BPS";
    private static final String METRIC_PER = "PER";
    private static final String METRIC_PBR = "PBR";

    @Transactional
    public int rebuildCompanyMetrics(Long companyId, int startYear, int endYear) {

        int updated = 0;
        for (int year = endYear - 1; year >= startYear; year--) {
            updated += rebuildAnnualMetrics(companyId, year);
        }
        updated += rebuildMarketMetrics(companyId, startYear, endYear);

        return updated;
    }

    public int rebuildAnnualMetrics(Long companyId, int year) {
        int total = 0;
        FsRawBundle rawBundle = financialService.loadRawBundle(companyId, year);
        total += rebuildStage(companyId, year, MetricStage.BASE, rawBundle);
        total += rebuildStage(companyId, year, MetricStage.DERIVED, rawBundle);
        int epsSaved = rebuildStage(companyId, year, MetricStage.PER_SHARE, rawBundle);
        int bpsSaved = rebuildBpsMetric(companyId, year);

        total += epsSaved + bpsSaved;
        return total;
    }

    public int rebuildMarketMetrics(Long companyId, int startYear, int endYear) {
        List<FinPeriod> periods = financialService.findYearlyPeriods(companyId);
        int updated = 0;

        for (FinPeriod period : periods) {
            Integer fiscalYear = period.getFiscalYear();
            if (fiscalYear == null || fiscalYear < startYear || fiscalYear >= endYear) {
                continue;
            }

            Map<String, BigDecimal> metrics = buildMarketMetrics(companyId, period, fiscalYear);
            updated += rebuildStage(companyId, fiscalYear, MetricStage.MARKET, metrics);
        }

        return updated;
    }

    private Map<String, BigDecimal> buildMarketMetrics(Long companyId, FinPeriod period, int fiscalYear) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();

        BigDecimal price = financialService.findYearEndPrice(companyId, fiscalYear).orElse(null);
        if (price == null) {
            log.debug("[FIN-METRIC][MARKET] year-end price missing - companyId={}, year={}", companyId, fiscalYear);
            return result;
        }

        financialService.findMetricValue(companyId, period, METRIC_EPS)
                .filter(v -> v.compareTo(BigDecimal.ZERO) != 0)
                .map(v -> price.divide(v, 2, RoundingMode.HALF_UP))
                .ifPresent(v -> result.put(METRIC_PER, v));

        financialService.findMetricValue(companyId, period, METRIC_BPS)
                .filter(v -> v.compareTo(BigDecimal.ZERO) != 0)
                .map(v -> price.divide(v, 2, RoundingMode.HALF_UP))
                .ifPresent(v -> result.put(METRIC_PBR, v));

        return result;
    }

    private int rebuildStage(Long companyId, int fiscalYear, MetricStage stage, FsRawBundle rawBundle) {
        Map<String, BigDecimal> metrics = financialService.buildMetrics(companyId, fiscalYear, rawBundle, stage);
        int saved = financialService.replaceMetrics(companyId, fiscalYear, stage, metrics);
        return saved;
    }

    private int rebuildStage(Long companyId, int fiscalYear, MetricStage stage, Map<String, BigDecimal> metrics) {
        return financialService.replaceMetrics(companyId, fiscalYear, stage, metrics);
    }

    private int rebuildBpsMetric(Long companyId, int fiscalYear) {
        FinPeriod period = financialService.findAnnualPeriod(companyId, fiscalYear).orElse(null);
        if (period == null) {
            return 0;
        }

        BigDecimal equityOwner = financialService.findMetricValue(companyId, period, METRIC_EQUITY_OWNER)
                .orElse(null);
        if (equityOwner == null) {
            return 0;
        }

        BigDecimal shares = financialService.findLatestShareCountForPeriod(companyId, period)
                .or(() -> financialService.findCompanyShareCount(companyId))
                .orElse(null);
        if (shares == null || shares.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }

        BigDecimal bps = equityOwner.divide(shares, 0, RoundingMode.HALF_UP);
        return financialService.replaceSingleMetric(companyId, fiscalYear, METRIC_BPS, bps);
    }
}
