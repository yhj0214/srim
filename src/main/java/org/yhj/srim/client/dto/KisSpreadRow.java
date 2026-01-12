package org.yhj.srim.client.dto;

import java.math.BigDecimal;

public record KisSpreadRow(
        String category, // 국고채, AAA, AA+, ...
        BigDecimal m3,
        BigDecimal m6,
        BigDecimal m9,
        BigDecimal y1,
        BigDecimal y1_6,
        BigDecimal y2,
        BigDecimal y3,
        BigDecimal y5
) {}
