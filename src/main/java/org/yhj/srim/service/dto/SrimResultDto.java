package org.yhj.srim.service.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * S-RIM 계산 결과 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SrimResultDto {

    // 입력 파라미터
    private String rating; // BBB-
    private Integer tenorMonths; // 60
    private Integer year;

    // 중간 계산값
    private BigDecimal equity;           // 자기자본 (지배주주지분)
    private BigDecimal roe;              // ROE (가중평균)
    private BigDecimal roePercent;       // ROE (가중평균, %)
    private BigDecimal ke;               // 할인율 (요구수익률)
    private Long sharesOutstanding;      // 주식수
    private BigDecimal currentPrice;     // 현재(최근) 주가
    private String currentPriceDate;     // 현재가 기준일 (YYYY-MM-DD)

    private List<RoeDetail> roeDetails;  // 연도별 ROE (최신 3개)
    private List<ScenarioResult> scenarios; // 시나리오별 결과
    private QuarterlyResult quarterly;   // 최신 분기 기준 계산 결과

    /**
     * 시나리오 결과
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ScenarioResult {
        private BigDecimal reductionRate;     // 감소율 (0, -0.1, -0.2, -0.3, -0.5)
        private BigDecimal excessEarnings;    // 초과이익
        private BigDecimal enterpriseValue;   // 기업가치
        private BigDecimal fairValuePerShare; // 적정주가
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoeDetail {
        private Integer fiscalYear;
        private BigDecimal roePercent;
        private BigDecimal equityOwner;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuarterlyResult {
        private String periodLabel;
        private Integer fiscalYear;
        private Integer fiscalQuarter;
        private BigDecimal equity;
        private BigDecimal roe;
        private BigDecimal roePercent;
        private BigDecimal ke;
        private Long sharesOutstanding;
        private List<ScenarioResult> scenarios;
    }
}
