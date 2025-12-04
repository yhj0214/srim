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

    private List<PriceData> priceData;

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

        private BigDecimal fvScenario0;   // 적정주가 (초과이익 지속)
        private BigDecimal fvScenario10;  // 초과이익 10% 감소
        private BigDecimal fvScenario20;  // 20% 감소
        private BigDecimal fvScenario30;  // 30% 감소
        private BigDecimal fvScenario50;  // 50% 감소
    }
}
