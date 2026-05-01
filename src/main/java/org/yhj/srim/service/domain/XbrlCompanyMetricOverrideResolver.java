package org.yhj.srim.service.domain;

import org.springframework.stereotype.Component;
import org.yhj.srim.service.dto.XbrlRawBundle;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class XbrlCompanyMetricOverrideResolver {

    private static final Map<String, XbrlCompanyMetricOverrideType> OVERRIDE_TYPES_BY_CORP_CODE = Map.of(
            "00125080", XbrlCompanyMetricOverrideType.AK_HOLDINGS
    );

    private final Map<XbrlCompanyMetricOverrideType, XbrlCompanyMetricOverrideRule> rulesByType;

    public XbrlCompanyMetricOverrideResolver(List<XbrlCompanyMetricOverrideRule> rules) {
        this.rulesByType = rules.stream()
                .collect(Collectors.toUnmodifiableMap(XbrlCompanyMetricOverrideRule::type, Function.identity()));
    }

    public Map<String, BigDecimal> resolveOverrides(XbrlRawBundle bundle, Map<String, BigDecimal> baseMetrics) {
        if (bundle == null || bundle.document() == null || bundle.document().corpCode() == null) {
            return Map.of();
        }

        XbrlCompanyMetricOverrideType ruleType = OVERRIDE_TYPES_BY_CORP_CODE.get(bundle.document().corpCode());
        if (ruleType == null) {
            return Map.of();
        }

        XbrlCompanyMetricOverrideRule rule = rulesByType.get(ruleType);
        if (rule == null) {
            return Map.of();
        }
        return rule.resolveOverrides(bundle, baseMetrics);
    }
}
