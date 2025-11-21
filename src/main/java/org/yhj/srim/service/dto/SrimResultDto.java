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
    private String basis;  // YEAR or QTR
    private String rating; // BBB-
    private Integer tenorMonths; // 60
    
    // 중간 계산값
    private BigDecimal equity;           // 자기자본 (BPS)
    private BigDecimal roe;              // ROE (가중평균)
    private BigDecimal ke;               // 할인율 (요구수익률)
    private Long sharesOutstanding;      // 주식수
    
    // 시나리오별 결과
    private List<ScenarioResult> scenarios;
    
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
}
