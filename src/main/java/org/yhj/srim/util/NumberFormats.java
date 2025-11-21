package org.yhj.srim.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

/**
 * 숫자 포맷 유틸리티
 */
public class NumberFormats {

    private static final DecimalFormat COMMA_FORMAT = new DecimalFormat("#,##0");
    private static final DecimalFormat DECIMAL_2_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("#,##0.00");

    /**
     * 천단위 콤마 (정수)
     * 예: 1234567 → "1,234,567"
     */
    public static String formatWithComma(Long value) {
        if (value == null) return "-";
        return COMMA_FORMAT.format(value);
    }

    /**
     * 천단위 콤마 (소수 2자리)
     * 예: 1234.567 → "1,234.57"
     */
    public static String formatWithComma(BigDecimal value) {
        if (value == null) return "-";
        return DECIMAL_2_FORMAT.format(value);
    }

    /**
     * 천단위 콤마 (소수 지정)
     */
    public static String formatWithComma(BigDecimal value, int scale) {
        if (value == null) return "-";
        return formatWithComma(value.setScale(scale, RoundingMode.HALF_UP));
    }

    /**
     * 퍼센트 포맷 (소수 2자리)
     * 예: 0.1523 → "15.23%"
     */
    public static String formatPercent(BigDecimal value) {
        if (value == null) return "-";
        BigDecimal percent = value.multiply(BigDecimal.valueOf(100));
        return PERCENT_FORMAT.format(percent) + "%";
    }

    /**
     * 배수 포맷 (소수 2자리)
     * 예: 15.234 → "15.23배"
     */
    public static String formatMultiple(BigDecimal value) {
        if (value == null) return "-";
        return DECIMAL_2_FORMAT.format(value) + "배";
    }

    /**
     * 금액 단위 (원 → 억원)
     * 예: 123456789000 → "1,234.57억원"
     */
    public static String formatBillionWon(BigDecimal value) {
        if (value == null) return "-";
        BigDecimal billion = value.divide(BigDecimal.valueOf(100000000), 2, RoundingMode.HALF_UP);
        return DECIMAL_2_FORMAT.format(billion) + "억원";
    }

    /**
     * 금액 단위 (원 → 조원)
     * 예: 12345678900000 → "12.35조원"
     */
    public static String formatTrillionWon(BigDecimal value) {
        if (value == null) return "-";
        BigDecimal trillion = value.divide(BigDecimal.valueOf(1000000000000L), 2, RoundingMode.HALF_UP);
        return DECIMAL_2_FORMAT.format(trillion) + "조원";
    }

    /**
     * 자동 단위 선택 (조/억/원)
     */
    public static String formatWonAuto(BigDecimal value) {
        if (value == null) return "-";
        
        BigDecimal absValue = value.abs();
        
        if (absValue.compareTo(BigDecimal.valueOf(1000000000000L)) >= 0) {
            // 1조 이상
            return formatTrillionWon(value);
        } else if (absValue.compareTo(BigDecimal.valueOf(100000000)) >= 0) {
            // 1억 이상
            return formatBillionWon(value);
        } else {
            // 1억 미만
            return formatWithComma(value.setScale(0, RoundingMode.HALF_UP)) + "원";
        }
    }

    /**
     * 단위에 따른 포맷 적용
     */
    public static String format(BigDecimal value, String unit) {
        if (value == null) return "-";
        
        if (unit == null) {
            return formatWithComma(value);
        }
        
        switch (unit.toUpperCase()) {
            case "KRW":
            case "원":
                return formatWonAuto(value);
            case "%":
            case "PERCENT":
                return formatPercent(value);
            case "배":
            case "MULTIPLE":
                return formatMultiple(value);
            case "주":
                return formatWithComma(value.setScale(0, RoundingMode.HALF_UP)) + "주";
            default:
                return formatWithComma(value);
        }
    }
}
