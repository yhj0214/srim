package org.yhj.srim.service.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnnualXbrlDerivedMetricCalculatorTest {

    private final AnnualXbrlDerivedMetricCalculator calculator = new AnnualXbrlDerivedMetricCalculator();

    @Test
    @DisplayName("연간 XBRL derived metric calculator는 수익성/안정성 지표를 계산한다.")
    void calculate_derivesMetricsFromCurrentAndPreviousRaw() {
        Map<String, BigDecimal> raw = new LinkedHashMap<>();
        raw.put("SALES", new BigDecimal("2000"));
        raw.put("NET_INC", new BigDecimal("300"));
        raw.put("NET_INC_OWNER", new BigDecimal("200"));
        raw.put("TOTAL_ASSETS", new BigDecimal("750"));
        raw.put("TOTAL_LIABILITIES", new BigDecimal("250"));
        raw.put("TOTAL_EQUITY", new BigDecimal("500"));
        raw.put("TOTAL_EQUITY_OWNER", new BigDecimal("450"));
        raw.put("CURRENT_ASSETS", new BigDecimal("300"));
        raw.put("CURRENT_LIABILITIES", new BigDecimal("150"));

        Map<String, BigDecimal> prevRaw = new LinkedHashMap<>();
        prevRaw.put("TOTAL_ASSETS", new BigDecimal("620"));
        prevRaw.put("TOTAL_EQUITY", new BigDecimal("400"));
        prevRaw.put("TOTAL_EQUITY_OWNER", new BigDecimal("350"));

        Map<String, BigDecimal> metrics = calculator.calculate(raw, prevRaw, 2024);

        assertThat(metrics)
                .containsEntry("NET_MARGIN", new BigDecimal("15.00000000"))
                .containsEntry("DEBT_RATIO", new BigDecimal("50.00000000"))
                .containsEntry("ROE", new BigDecimal("50.00000000"))
                .containsEntry("ROA", new BigDecimal("43.79562000"))
                .containsEntry("QUICK_RATIO", new BigDecimal("200.00000000"));
    }
}
