package org.yhj.srim.service.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 재무 테이블 DTO
 * 행: 지표, 열: 기간
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialTableDto {
    
    /**
     * 테이블 헤더 (기간 라벨)
     * 예: ["2024/12", "2023/12", "2022/12"]
     */
    private List<PeriodHeaderDto> headers;
    
    /**
     * 테이블 행 (지표별 데이터)
     */
    private List<MetricRowDto> rows;
    
    /**
     * 기간 헤더 DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PeriodHeaderDto {
        private Long periodId;
        private String label;  // 예: "2024/12", "2024.Q3"
        private Integer fiscalYear;
        private Integer fiscalQuarter;  // 연간이면 null
        private Boolean isEstimate;
    }
    
    /**
     * 지표 행 DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MetricRowDto {
        private String metricCode;
        private String metricName;  // 한글명
        private String unit;  // 단위
        
        /**
         * 기간별 값
         * Key: periodId, Value: 지표 값
         */
        private Map<Long, BigDecimal> values;
    }
}
