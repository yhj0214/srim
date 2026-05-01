package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.yhj.srim.service.dto.XbrlRawBundle;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class XbrlDefaultMetricFallbackResolver {

    private static final String METRIC_NET_INC = "NET_INC";
    private static final String METRIC_NET_INC_OWNER = "NET_INC_OWNER";
    private static final String METRIC_NET_INC_NONCONT = "NET_INC_NONCONT";
    private static final String METRIC_TOTAL_EQUITY = "TOTAL_EQUITY";
    private static final String METRIC_TOTAL_EQUITY_OWNER = "TOTAL_EQUITY_OWNER";
    private static final String METRIC_TOTAL_EQUITY_NONCONT = "TOTAL_EQUITY_NONCONT";

    private final DefaultMetricFallbackRule defaultMetricFallbackRule;

    public Map<String, BigDecimal> resolveOverrides(XbrlRawBundle bundle, Map<String, BigDecimal> baseMetrics) {
        Map<String, BigDecimal> overrides = new LinkedHashMap<>();

        applyDefaultMetricOverride(
                overrides,
                defaultMetricFallbackRule.resolve(
                        bundle,
                        baseMetrics.get(METRIC_NET_INC),
                        baseMetrics.get(METRIC_NET_INC_OWNER),
                        baseMetrics.get(METRIC_NET_INC_NONCONT)
                ),
                METRIC_NET_INC_OWNER,
                METRIC_NET_INC_NONCONT
        );
        applyDefaultMetricOverride(
                overrides,
                defaultMetricFallbackRule.resolve(
                        bundle,
                        baseMetrics.get(METRIC_TOTAL_EQUITY),
                        baseMetrics.get(METRIC_TOTAL_EQUITY_OWNER),
                        baseMetrics.get(METRIC_TOTAL_EQUITY_NONCONT)
                ),
                METRIC_TOTAL_EQUITY_OWNER,
                METRIC_TOTAL_EQUITY_NONCONT
        );

        return overrides;
    }

    private void applyDefaultMetricOverride(Map<String, BigDecimal> overrides,
                                            Optional<DefaultMetricFallbackValues> fallback,
                                            String ownerMetricCode,
                                            String noncontMetricCode) {
        if (fallback.isEmpty()) {
            return;
        }
        overrides.put(ownerMetricCode, fallback.get().ownerValue());
        overrides.put(noncontMetricCode, fallback.get().noncontValue());
    }
}
