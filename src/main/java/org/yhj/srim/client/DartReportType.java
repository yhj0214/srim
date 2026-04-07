package org.yhj.srim.client;

import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CommonError;

import java.time.LocalDate;
import java.util.Arrays;

public enum DartReportType {
    ANNUAL("11011", "연간"),
    HALF_YEAR("11012", "반기"),
    FIRST_QUARTER("11013", "1분기"),
    THIRD_QUARTER("11014", "3분기");

    private final String code;
    private final String label;

    DartReportType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public String periodType() {
        return this == ANNUAL ? "YEAR" : "QTR";
    }

    public Integer fiscalQuarter() {
        return switch (this) {
            case ANNUAL -> null;
            case FIRST_QUARTER -> 1;
            case HALF_YEAR -> 2;
            case THIRD_QUARTER -> 3;
        };
    }

    public LocalDate periodStart(int fiscalYear) {
        return switch (this) {
            case FIRST_QUARTER -> LocalDate.of(fiscalYear, 1, 1);
            case HALF_YEAR -> LocalDate.of(fiscalYear, 4, 1);
            case THIRD_QUARTER -> LocalDate.of(fiscalYear, 7, 1);
            case ANNUAL -> LocalDate.of(fiscalYear, 1, 1);
        };
    }

    public LocalDate periodEnd(int fiscalYear) {
        return switch (this) {
            case FIRST_QUARTER -> LocalDate.of(fiscalYear, 3, 31);
            case HALF_YEAR -> LocalDate.of(fiscalYear, 6, 30);
            case THIRD_QUARTER -> LocalDate.of(fiscalYear, 9, 30);
            case ANNUAL -> LocalDate.of(fiscalYear, 12, 31);
        };
    }

    public String periodLabel(int fiscalYear) {
        return switch (this) {
            case FIRST_QUARTER -> fiscalYear + ".03";
            case HALF_YEAR -> fiscalYear + ".06";
            case THIRD_QUARTER -> fiscalYear + ".09";
            case ANNUAL -> fiscalYear + ".12";
        };
    }

    public static DartReportType fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new CustomException(
                        CommonError.INVALID_INPUT, "지원하지 않는 DART 보고서 코드입니다. code=" + code
                ));
    }
}
