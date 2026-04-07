package org.yhj.srim.client;

import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CommonError;

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

    public static DartReportType fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new CustomException(
                        CommonError.INVALID_INPUT, "지원하지 않는 DART 보고서 코드입니다. code=" + code
                ));
    }
}
