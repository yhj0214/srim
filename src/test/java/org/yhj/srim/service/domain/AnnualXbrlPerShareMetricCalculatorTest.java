package org.yhj.srim.service.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yhj.srim.repository.StockShareStatusRepository;
import org.yhj.srim.repository.entity.ShareClassType;
import org.yhj.srim.repository.entity.StockShareStatus;
import org.yhj.srim.service.domain.calculator.AnnualXbrlPerShareMetricCalculator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnualXbrlPerShareMetricCalculatorTest {

    @InjectMocks
    AnnualXbrlPerShareMetricCalculator calculator;

    @Mock
    StockShareStatusRepository stockShareStatusRepository;

    @Test
    @DisplayName("연간 XBRL per-share calculator는 지배순이익과 주식수로 EPS를 계산한다.")
    void calculate_buildsEpsFromOwnerIncomeAndShareCount() {
        Map<String, BigDecimal> raw = Map.of("NET_INC_OWNER", new BigDecimal("200"));
        StockShareStatus common = StockShareStatus.builder()
                .shareClassType(ShareClassType.COMMON)
                .istcTotqy(100L)
                .build();

        when(stockShareStatusRepository.findByCompany_CompanyIdAndBsnsYearAndShareClassTypeIn(
                7L, 2024, List.of(ShareClassType.COMMON, ShareClassType.PREFERRED)
        )).thenReturn(List.of(common));

        Map<String, BigDecimal> metrics = calculator.calculate(7L, raw, 2024);

        assertThat(metrics).containsEntry("EPS", new BigDecimal("2.00"));
    }
}
