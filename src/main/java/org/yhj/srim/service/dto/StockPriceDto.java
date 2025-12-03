package org.yhj.srim.service.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 주가 그래프 데이터 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockPriceDto {
    
    /**
     * 주가 데이터 목록
     */
    private List<PriceData> priceData;
    
    /**
     * 시나리오별 적정주가 데이터
     */
    private ScenarioData scenarioData;
    
    /**
     * 일별 주가 데이터
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PriceData {
        private LocalDate date;        // 날짜
        private BigDecimal open;       // 시가
        private BigDecimal high;       // 고가
        private BigDecimal low;        // 저가
        private BigDecimal close;      // 종가
        private Long volume;           // 거래량
        private FairValues fairValues; // 날짜별 적정주가
    }
    
    /**
     * 날짜별 적정주가 데이터
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FairValues {
        private BigDecimal scenario0;  // 초과이익 지속
        private BigDecimal scenario10; // 10% 감소
        private BigDecimal scenario20; // 20% 감소
        private BigDecimal scenario30; // 30% 감소
        private BigDecimal scenario50; // 50% 감소
    }
    
    /**
     * 시나리오별 적정주가 데이터
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ScenarioData {
        private BigDecimal scenario0;  // 초과이익 지속
        private BigDecimal scenario10; // 10% 감소
        private BigDecimal scenario20; // 20% 감소
        private BigDecimal scenario30; // 30% 감소
        private BigDecimal scenario50; // 50% 감소
    }
}
