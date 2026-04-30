package org.yhj.srim.service.domain;

import java.math.BigDecimal;

public record OwnershipMetricValues(
        BigDecimal ownerValue,
        BigDecimal noncontValue
) {
}
