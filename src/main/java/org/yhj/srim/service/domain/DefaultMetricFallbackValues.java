package org.yhj.srim.service.domain;

import java.math.BigDecimal;

public record DefaultMetricFallbackValues(
        BigDecimal ownerValue,
        BigDecimal noncontValue
) {
}
