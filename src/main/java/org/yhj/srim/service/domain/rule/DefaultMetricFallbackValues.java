package org.yhj.srim.service.domain.rule;

import java.math.BigDecimal;

public record DefaultMetricFallbackValues(
        BigDecimal ownerValue,
        BigDecimal noncontValue
) {
}
