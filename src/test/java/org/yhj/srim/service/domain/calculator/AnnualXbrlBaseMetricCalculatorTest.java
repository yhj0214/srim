package org.yhj.srim.service.domain.calculator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnnualXbrlBaseMetricCalculatorTest {

    private final AnnualXbrlBaseMetricCalculator calculator = new AnnualXbrlBaseMetricCalculator();

    @Test
    @DisplayName("연간 XBRL base metric calculator는 핵심 base metric만 추출한다.")
    void calculate_extractsSupportedBaseMetrics() {
        Map<String, BigDecimal> raw = new LinkedHashMap<>();
        raw.put("SALES", new BigDecimal("2000"));
        raw.put("OP_INC", new BigDecimal("300"));
        raw.put("NET_INC", new BigDecimal("250"));
        raw.put("NET_INC_OWNER", new BigDecimal("200"));
        raw.put("NET_INC_NONCONT", new BigDecimal("50"));
        raw.put("TOTAL_LIABILITIES", new BigDecimal("400"));
        raw.put("TOTAL_EQUITY", new BigDecimal("600"));
        raw.put("TOTAL_EQUITY_OWNER", new BigDecimal("550"));
        raw.put("IGNORED", new BigDecimal("999"));

        Map<String, BigDecimal> metrics = calculator.calculate(raw);

        assertThat(metrics)
                .containsEntry("SALES", new BigDecimal("2000"))
                .containsEntry("OP_INC", new BigDecimal("300"))
                .containsEntry("NET_INC", new BigDecimal("250"))
                .containsEntry("NET_INC_OWNER", new BigDecimal("200"))
                .containsEntry("NET_INC_NONCONT", new BigDecimal("50"))
                .containsEntry("TOTAL_LIABILITIES", new BigDecimal("400"))
                .containsEntry("TOTAL_EQUITY", new BigDecimal("600"))
                .containsEntry("TOTAL_EQUITY_OWNER", new BigDecimal("550"))
                .doesNotContainKey("IGNORED");
    }
}
