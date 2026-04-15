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
    private static final String METRIC_EQUITY = "TOTAL_EQUITY";
    private static final String METRIC_EPS = "EPS";
    private static final String METRIC_BPS = "BPS";
    private static final String METRIC_PER = "PER";
    private static final String METRIC_PBR = "PBR";
    private static final String METRIC_NET_INC_OWNER = "NET_INC_OWNER";
    private static final String METRIC_NET_INC = "NET_INC";
    private static final String METRIC_ROE = "ROE";
    private static final String METRIC_ROA = "ROA";
    private static final String METRIC_TOTAL_ASSETS = "TOTAL_ASSETS";

    @Transactional
    public int rebuildCompanyMetrics(Long companyId, int startYear, int endYear) {

        int updated = 0;
        for (int year = endYear - 1; year >= startYear; year--) {
            updated += rebuildAnnualMetrics(companyId, year);
            updated += rebuildQuarterlyMetrics(companyId, year);
        }
        updated += rebuildQuarterlyRoeMetrics(companyId, startYear, endYear);
        updated += rebuildQuarterlyRoaMetrics(companyId, startYear, endYear);
        updated += rebuildMarketMetrics(companyId, startYear, endYear);

        return updated;
    }

    public int rebuildAnnualMetrics(Long companyId, int year) {
        int total = 0;
        FsRawBundle rawBundle = financialService.loadRawBundle(companyId, year);
        total += rebuildStage(companyId, year, MetricStage.BASE, rawBundle);
        total += rebuildStage(companyId, year, MetricStage.DERIVED, rawBundle);
        int epsSaved = rebuildStage(companyId, year, MetricStage.PER_SHARE, rawBundle);
        int roaSaved = rebuildAnnualRoaMetric(companyId, year, rawBundle);
        int bpsSaved = rebuildBpsMetric(companyId, year);

        total += epsSaved + roaSaved + bpsSaved;
        return total;
    }

    public int rebuildQuarterlyMetrics(Long companyId, int year) {
        int total = 0;

        for (int quarter = 1; quarter <= 4; quarter++) {
            FinPeriod period = financialService.findQuarterPeriod(companyId, year, quarter).orElse(null);
            if (period == null) {
                continue;
            }

            FsRawBundle rawBundle = financialService.loadQuarterRawBundle(companyId, year, quarter);
            if (rawBundle.curr().isEmpty()) {
                continue;
            }

            total += rebuildStage(companyId, period, MetricStage.BASE, rawBundle);
            total += rebuildStage(companyId, period, MetricStage.DERIVED, rawBundle);
            total += rebuildStage(companyId, period, MetricStage.PER_SHARE, rawBundle);
            total += rebuildBpsMetric(companyId, period);
        }

        return total;
    }

    int rebuildQuarterlyRoeMetrics(Long companyId, int startYear, int endYear) {
        int updated = 0;

        for (int year = startYear; year < endYear; year++) {
            for (int quarter = 1; quarter <= 4; quarter++) {
                FinPeriod period = financialService.findQuarterPeriod(companyId, year, quarter).orElse(null);
                if (period == null) {
                    continue;
                }

                int replaced = rebuildQuarterlyRoeMetric(companyId, period);
                if (replaced == 0) {
                    financialService.replaceMetricsByCodes(companyId, period, Map.of(), List.of(METRIC_ROE));
                    continue;
                }
                updated += replaced;
            }
        }

        return updated;
    }

    int rebuildQuarterlyRoaMetrics(Long companyId, int startYear, int endYear) {
        int updated = 0;

        for (int year = startYear; year < endYear; year++) {
            for (int quarter = 1; quarter <= 4; quarter++) {
                FinPeriod period = financialService.findQuarterPeriod(companyId, year, quarter).orElse(null);
                if (period == null) {
                    continue;
                }

                int replaced = rebuildQuarterlyRoaMetric(companyId, period);
                if (replaced == 0) {
                    financialService.replaceMetricsByCodes(companyId, period, Map.of(), List.of(METRIC_ROA));
                    continue;
                }
                updated += replaced;
            }
        }

        return updated;
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

    private int rebuildStage(Long companyId, FinPeriod period, MetricStage stage, FsRawBundle rawBundle) {
        Map<String, BigDecimal> metrics = financialService.buildMetrics(companyId, period.getFiscalYear(), rawBundle, stage);
        return financialService.replaceMetrics(companyId, period, stage, metrics);
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

    private int rebuildBpsMetric(Long companyId, FinPeriod period) {
        // 지배주주 자본 조회
        BigDecimal equityOwner = financialService.findMetricValue(companyId, period, METRIC_EQUITY_OWNER)
                .orElse(null);
        if (equityOwner == null) {
            return 0;
        }

        // 주식 수 조회
        BigDecimal shares = financialService.findLatestShareCountForPeriod(companyId, period)
                .or(() -> financialService.findCompanyShareCount(companyId))
                .orElse(null);
        if (shares == null || shares.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }

        BigDecimal bps = equityOwner.divide(shares, 0, RoundingMode.HALF_UP);
        return financialService.replaceSingleMetric(companyId, period, METRIC_BPS, bps);
    }

    private int rebuildQuarterlyRoeMetric(Long companyId, FinPeriod period) {
        Integer fiscalYear = period.getFiscalYear();
        Integer fiscalQuarter = period.getFiscalQuarter();
        if (fiscalYear == null || fiscalQuarter == null) {
            log.info("[QTR-ROE] skip invalid period companyId={}, periodId={}",
                    companyId, period.getPeriodId());
            return 0;
        }

        log.info("[QTR-ROE] start companyId={}, period={}/{}", companyId, fiscalYear, fiscalQuarter);

        List<FinPeriod> trailingPeriods = financialService.findRecentActualQuarterlyPeriodsUpTo(
                companyId, fiscalYear, fiscalQuarter, 4
        );
        if (trailingPeriods.size() < 4) {
            log.info("[QTR-ROE] skip insufficient trailing periods companyId={}, period={}/{}, count={}",
                    companyId, fiscalYear, fiscalQuarter, trailingPeriods.size());
            return 0;
        }

        BigDecimal trailingNetIncome = trailingPeriods.stream()
                .map(trailingPeriod -> financialService.findMetricValue(companyId, trailingPeriod, METRIC_NET_INC_OWNER)
                        .or(() -> financialService.findMetricValue(companyId, trailingPeriod, METRIC_NET_INC))
                        .orElse(null))
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (trailingNetIncome.compareTo(BigDecimal.ZERO) == 0) {
            log.info("[QTR-ROE] skip zero trailing net income companyId={}, period={}/{}, periods={}",
                    companyId, fiscalYear, fiscalQuarter,
                    trailingPeriods.stream()
                            .map(p -> p.getFiscalYear() + "/" + p.getFiscalQuarter())
                            .toList());
            return 0;
        }

        FinPeriod yearAgoPeriod = financialService.findQuarterPeriod(companyId, fiscalYear - 1, fiscalQuarter).orElse(null);
        if (yearAgoPeriod == null) {
            log.info("[QTR-ROE] skip missing year-ago period companyId={}, period={}/{}",
                    companyId, fiscalYear, fiscalQuarter);
            return 0;
        }

        BigDecimal equityCurrent = findEquityForRoe(companyId, period);
        BigDecimal equityYearAgo = findEquityForRoe(companyId, yearAgoPeriod);
        if (equityCurrent == null || equityYearAgo == null) {
            log.info("[QTR-ROE] skip missing equity companyId={}, period={}/{}, equityCurrent={}, equityYearAgo={}",
                    companyId, fiscalYear, fiscalQuarter, equityCurrent, equityYearAgo);
            return 0;
        }

        BigDecimal averageEquity = equityCurrent.add(equityYearAgo)
                .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
        if (averageEquity.compareTo(BigDecimal.ZERO) == 0) {
            log.info("[QTR-ROE] skip zero average equity companyId={}, period={}/{}, equityCurrent={}, equityYearAgo={}",
                    companyId, fiscalYear, fiscalQuarter, equityCurrent, equityYearAgo);
            return 0;
        }

        BigDecimal roe = trailingNetIncome
                .divide(averageEquity, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        log.info("[QTR-ROE] replace companyId={}, period={}/{}, trailingNetIncome={}, equityCurrent={}, equityYearAgo={}, roe={}",
                companyId, fiscalYear, fiscalQuarter, trailingNetIncome, equityCurrent, equityYearAgo, roe);
        return financialService.replaceSingleMetric(companyId, period, METRIC_ROE, roe);
    }

    private BigDecimal findEquityForRoe(Long companyId, FinPeriod period) {
        return financialService.findMetricValue(companyId, period, METRIC_EQUITY_OWNER)
                .or(() -> financialService.findMetricValue(companyId, period, METRIC_EQUITY))
                .orElse(null);
    }

    private int rebuildAnnualRoaMetric(Long companyId, int fiscalYear, FsRawBundle rawBundle) {
        if (rawBundle == null) {
            return financialService.replaceAnnualMetricsByCodes(companyId, fiscalYear, Map.of(), List.of(METRIC_ROA));
        }

        BigDecimal netIncome = rawBundle.curr().get(METRIC_NET_INC);
        BigDecimal assetsCurrent = rawBundle.curr().get(METRIC_TOTAL_ASSETS);
        BigDecimal assetsPrevious = rawBundle.prev().get(METRIC_TOTAL_ASSETS);
        if (netIncome == null || assetsCurrent == null || assetsPrevious == null) {
            return financialService.replaceAnnualMetricsByCodes(companyId, fiscalYear, Map.of(), List.of(METRIC_ROA));
        }

        BigDecimal averageAssets = assetsCurrent.add(assetsPrevious)
                .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
        if (averageAssets.compareTo(BigDecimal.ZERO) == 0) {
            return financialService.replaceAnnualMetricsByCodes(companyId, fiscalYear, Map.of(), List.of(METRIC_ROA));
        }

        BigDecimal roa = netIncome.divide(averageAssets, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        return financialService.replaceSingleMetric(companyId, fiscalYear, METRIC_ROA, roa);
    }

    private int rebuildQuarterlyRoaMetric(Long companyId, FinPeriod period) {
        Integer fiscalYear = period.getFiscalYear();
        Integer fiscalQuarter = period.getFiscalQuarter();
        if (fiscalYear == null || fiscalQuarter == null) {
            return 0;
        }

        List<FinPeriod> trailingPeriods = financialService.findRecentActualQuarterlyPeriodsUpTo(
                companyId, fiscalYear, fiscalQuarter, 4
        );
        if (trailingPeriods.size() < 4) {
            return 0;
        }

        BigDecimal trailingNetIncome = trailingPeriods.stream()
                .map(trailingPeriod -> financialService.findMetricValue(companyId, trailingPeriod, METRIC_NET_INC).orElse(null))
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (trailingNetIncome.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }

        BigDecimal assetsCurrent = findAssetsForRoa(companyId, fiscalYear, fiscalQuarter);
        BigDecimal assetsYearAgo = findAssetsForRoa(companyId, fiscalYear - 1, fiscalQuarter);
        if (assetsCurrent == null || assetsYearAgo == null) {
            return 0;
        }

        BigDecimal averageAssets = assetsCurrent.add(assetsYearAgo)
                .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
        if (averageAssets.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }

        BigDecimal roa = trailingNetIncome.divide(averageAssets, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        return financialService.replaceSingleMetric(companyId, period, METRIC_ROA, roa);
    }

    private BigDecimal findAssetsForRoa(Long companyId, int fiscalYear, int fiscalQuarter) {
        FsRawBundle rawBundle = financialService.loadQuarterRawBundle(companyId, fiscalYear, fiscalQuarter);
        return rawBundle.curr().get(METRIC_TOTAL_ASSETS);
    }
}
