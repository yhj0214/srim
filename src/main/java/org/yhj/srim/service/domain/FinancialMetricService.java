package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.repository.entity.FinPeriod;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialMetricService {

    private final FinancialService financialService;

    private static final String METRIC_EQUITY_OWNER = "TOTAL_EQUITY_OWNER";
    private static final String METRIC_EQUITY = "TOTAL_EQUITY";
    private static final String METRIC_EPS = "EPS";
    private static final String METRIC_BPS = "BPS";
    private static final String METRIC_PER = "PER";
    private static final String METRIC_PBR = "PBR";
    @Transactional
    public int rebuildAnnualSupplementalMetricsFromXbrl(Long companyId, int fiscalYear) {
        int total = 0;
        total += rebuildAnnualBpsMetricFromStoredValues(companyId, fiscalYear);
        total += rebuildAnnualMarketMetricsFromStoredValues(companyId, fiscalYear);
        return total;
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

    private int rebuildAnnualBpsMetricFromStoredValues(Long companyId, int fiscalYear) {
        FinPeriod period = financialService.findAnnualPeriod(companyId, fiscalYear).orElse(null);
        if (period == null) {
            return 0;
        }

        BigDecimal equityOwner = financialService.findMetricValue(companyId, period, METRIC_EQUITY_OWNER)
                .or(() -> financialService.findMetricValue(companyId, period, METRIC_EQUITY))
                .orElse(null);
        if (equityOwner == null) {
            return financialService.replaceAnnualMetricsByCodes(
                    companyId, fiscalYear, Map.of(), List.of(METRIC_BPS), "XBRL"
            );
        }

        BigDecimal shares = financialService.findLatestShareCountForPeriod(companyId, period)
                .or(() -> financialService.findCompanyShareCount(companyId))
                .orElse(null);
        if (shares == null || shares.compareTo(BigDecimal.ZERO) == 0) {
            return financialService.replaceAnnualMetricsByCodes(
                    companyId, fiscalYear, Map.of(), List.of(METRIC_BPS), "XBRL"
            );
        }

        BigDecimal bps = equityOwner.divide(shares, 0, RoundingMode.HALF_UP);
        return financialService.replaceAnnualMetricsByCodes(
                companyId, fiscalYear, Map.of(METRIC_BPS, bps), List.of(METRIC_BPS), "XBRL"
        );
    }

    private int rebuildAnnualMarketMetricsFromStoredValues(Long companyId, int fiscalYear) {
        FinPeriod period = financialService.findAnnualPeriod(companyId, fiscalYear).orElse(null);
        if (period == null) {
            return 0;
        }

        Map<String, BigDecimal> metrics = buildMarketMetrics(companyId, period, fiscalYear);
        return financialService.replaceAnnualMetricsByCodes(
                companyId, fiscalYear, metrics, List.of(METRIC_PER, METRIC_PBR), "XBRL"
        );
    }
}
