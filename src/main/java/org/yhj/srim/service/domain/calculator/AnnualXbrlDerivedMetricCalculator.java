package org.yhj.srim.service.domain.calculator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class AnnualXbrlDerivedMetricCalculator {

    public Map<String, BigDecimal> calculate(Map<String, BigDecimal> raw,
                                             Map<String, BigDecimal> prevRaw,
                                             int currentYear) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();

        if (raw == null || raw.isEmpty()) {
            return result;
        }

        BigDecimal sales = raw.get("SALES");
        BigDecimal opInc = raw.get("OP_INC");
        BigDecimal netInc = raw.get("NET_INC");
        BigDecimal netIncOwner = raw.get("NET_INC_OWNER");
        BigDecimal totalAssets = raw.get("TOTAL_ASSETS");
        BigDecimal totalLiab = raw.get("TOTAL_LIABILITIES");
        BigDecimal equityTotalCurr = raw.get("TOTAL_EQUITY");
        BigDecimal equityTotalPrev = prevRaw != null ? prevRaw.get("TOTAL_EQUITY") : null;
        BigDecimal equityOwnerCurr = raw.get("TOTAL_EQUITY_OWNER");
        BigDecimal equityOwnerPrev = prevRaw != null ? prevRaw.get("TOTAL_EQUITY_OWNER") : null;
        BigDecimal currentAssets = raw.get("CURRENT_ASSETS");
        BigDecimal currentLiab = raw.get("CURRENT_LIABILITIES");

        BigDecimal opm = raw.get("OPM");
        if (opm == null) {
            opm = toPercent(safeDivide(opInc, sales));
        }
        putIfNotNull(result, "OPM", opm);

        BigDecimal netMargin = raw.get("NET_MARGIN");
        if (netMargin == null) {
            netMargin = toPercent(safeDivide(netInc, sales));
        }
        putIfNotNull(result, "NET_MARGIN", netMargin);

        BigDecimal equityForDebt = (equityTotalCurr != null ? equityTotalCurr : equityOwnerCurr);
        BigDecimal debtRatio = toPercent(safeDivide(totalLiab, equityForDebt));
        putIfNotNull(result, "DEBT_RATIO", debtRatio);

        BigDecimal roeSourceNetInc = (netIncOwner != null ? netIncOwner : netInc);
        BigDecimal roeEquityCurr = (equityOwnerCurr != null ? equityOwnerCurr : equityTotalCurr);
        BigDecimal roeEquityPrev = (equityOwnerPrev != null ? equityOwnerPrev : equityTotalPrev);

        if (roeSourceNetInc != null && roeEquityCurr != null && roeEquityPrev != null) {
            BigDecimal avgEquity = roeEquityCurr.add(roeEquityPrev)
                    .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);

            if (avgEquity.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal roe = toPercent(
                        roeSourceNetInc.divide(avgEquity, 8, RoundingMode.HALF_UP)
                );
                putIfNotNull(result, "ROE", roe);

                log.debug("[ROE] year={} / netInc(used)={} / equity_curr={} / equity_prev={} / avgEquity={} / ROE={}",
                        currentYear, roeSourceNetInc, roeEquityCurr, roeEquityPrev, avgEquity, roe);
            } else {
                log.debug("[FS-DB][ROE] 평균 자기자본 0 - year={}", currentYear);
            }
        } else {
            log.debug("[FS-DB][ROE] netIncOwner/equityOwnerCurr/equityOwnerPrev 중 null 존재 - year={}", currentYear);
        }

        BigDecimal totalAssetsPrev = prevRaw != null ? prevRaw.get("TOTAL_ASSETS") : null;
        if (netInc != null && totalAssets != null && totalAssetsPrev != null) {
            BigDecimal avgAssets = totalAssets.add(totalAssetsPrev)
                    .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
            BigDecimal roa = toPercent(safeDivide(netInc, avgAssets));
            putIfNotNull(result, "ROA", roa);
        }

        BigDecimal quickRatio = toPercent(safeDivide(currentAssets, currentLiab));
        putIfNotNull(result, "QUICK_RATIO", quickRatio);

        return result;
    }

    private BigDecimal toPercent(BigDecimal ratio) {
        if (ratio == null) {
            return null;
        }
        return ratio.multiply(BigDecimal.valueOf(100));
    }

    private void putIfNotNull(Map<String, BigDecimal> map, String key, BigDecimal value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private BigDecimal safeDivide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || BigDecimal.ZERO.compareTo(denominator) == 0) {
            return null;
        }
        return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
    }
}
