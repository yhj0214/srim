package org.yhj.srim.service.domain;

import org.yhj.srim.service.dto.XbrlRawBundle;

import java.math.BigDecimal;
import java.util.Map;

public interface XbrlCompanyMetricOverrideRule {

    XbrlCompanyMetricOverrideType type();

    Map<String, BigDecimal> resolveOverrides(XbrlRawBundle bundle, Map<String, BigDecimal> baseMetrics);
}
