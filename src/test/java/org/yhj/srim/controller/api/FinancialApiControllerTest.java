package org.yhj.srim.controller.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CommonError;
import org.yhj.srim.service.domain.SrimService;
import org.yhj.srim.service.dto.FinancialTableDto;
import org.yhj.srim.service.dto.SrimResultDto;
import org.yhj.srim.service.facade.FinancialFacadeService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FinancialApiController.class)
class FinancialApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    FinancialFacadeService financialFacadeService;

    @MockitoBean
    SrimService srimService;

    @Test
    @DisplayName("연간 재무 테이블 API가 성공한다.")
    void annual_financial_success() throws Exception {
        FinancialTableDto dto = FinancialTableDto.builder()
                .headers(List.of(FinancialTableDto.PeriodHeaderDto.builder()
                        .periodId(1L)
                        .label("2024/12")
                        .fiscalYear(2024)
                        .fiscalQuarter(null)
                        .isEstimate(false)
                        .build()))
                .rows(List.of(FinancialTableDto.MetricRowDto.builder()
                                .metricCode("REV")
                                .metricName("매출액")
                                .unit("백만원")
                                .displayOrder(1)
                                .values(Map.of(1L, new BigDecimal(10000)))
                                .build(),
                        FinancialTableDto.MetricRowDto.builder()
                                .metricCode("OPM")
                                .metricName("영업이익률")
                                .unit("%")
                                .displayOrder(101)
                                .values(Map.of(1L, new BigDecimal(10000)))
                                .build()))
                .build();

        BDDMockito.given(financialFacadeService.getAnnualTableDbOnly(1L, 10))
                .willReturn(dto);

        mockMvc.perform(get("/api/stocks/1/financial/annual")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.headers[0].label").value("2024/12"))
                .andExpect(jsonPath("$.data.headers[0].fiscalYear").value(2024))
                .andExpect(jsonPath("$.data.rows[0].metricCode").value("REV"))
                .andExpect(jsonPath("$.data.rows[0].unit").value("백만원"))
                .andExpect(jsonPath("$.data.rows[1].metricCode").value("OPM"))
                .andExpect(jsonPath("$.data.rows[1].displayOrder").value(101));
    }

    @Test
    @DisplayName("연간 재무 테이블 API 요청이 잘못되면 400을 반환한다.")
    void annual_financial_bad_request() throws Exception {
        BDDMockito.given(financialFacadeService.getAnnualTableDbOnly(1L, 10))
                .willThrow(new IllegalArgumentException("invalid request"));

        mockMvc.perform(get("/api/stocks/1/financial/annual")
                        .param("limit", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                .andExpect(jsonPath("$.error.message").value("잘못된 요청입니다."))
                .andExpect(jsonPath("$.error.detail").value("invalid limit"))
                .andExpect(jsonPath("$.error.path").value("/api/stocks/1/financial/annual"));
    }

    @Test
    @DisplayName("연간 재무 테이블 API에서 CustomException이 발생하면 ErrorCode를 반환한다.")
    void annual_financial_custom_error() throws Exception {
        BDDMockito.given(financialFacadeService.getAnnualTableDbOnly(1L, 10))
                .willThrow(new CustomException(CommonError.INVALID_INPUT, "custom detail"));

        mockMvc.perform(get("/api/stocks/1/financial/annual")
                        .param("limit", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                .andExpect(jsonPath("$.error.message").value("잘못된 요청입니다."))
                .andExpect(jsonPath("$.error.detail").value("custom detail"))
                .andExpect(jsonPath("$.error.path").value("/api/stocks/1/financial/annual"));
    }

    @Test
    @DisplayName("연간 재무 테이블 API 처리 중 오류가 발생하면 500을 반환한다.")
    void annual_financial_internal_error() throws Exception {
        BDDMockito.given(financialFacadeService.getAnnualTableDbOnly(1L, 10))
                .willThrow(new RuntimeException("unhandled 발생"));

        mockMvc.perform(get("/api/stocks/1/financial/annual")
                        .param("limit", "10"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-002"))
                .andExpect(jsonPath("$.error.message").value("서버 오류가 발생했습니다."))
                .andExpect(jsonPath("$.error.path").value("/api/stocks/1/financial/annual"));
    }

    @Test
    @DisplayName("S-RIM 계산 API가 성공한다.")
    void srim_success() throws Exception {
        SrimResultDto dto = SrimResultDto.builder()
                .basis("YEAR")
                .rating("BBB-")
                .tenorMonths(60)
                .year(2024)
                .equity(new BigDecimal("123456"))
                .roe(new BigDecimal("0.1234"))
                .roePercent(new BigDecimal("12.34"))
                .ke(new BigDecimal("0.045"))
                .sharesOutstanding(5969782550L)
                .currentPrice(new BigDecimal("71000"))
                .currentPriceDate("2026-03-04")
                .roeDetails(List.of(
                        SrimResultDto.RoeDetail.builder()
                                .fiscalYear(2023)
                                .roePercent(new BigDecimal("11.10"))
                                .equityOwner(new BigDecimal("110000"))
                                .build(),
                        SrimResultDto.RoeDetail.builder()
                                .fiscalYear(2022)
                                .roePercent(new BigDecimal("12.50"))
                                .equityOwner(new BigDecimal("100000"))
                                .build(),
                        SrimResultDto.RoeDetail.builder()
                                .fiscalYear(2021)
                                .roePercent(new BigDecimal("13.20"))
                                .equityOwner(new BigDecimal("90000"))
                                .build()
                ))
                .scenarios(List.of(
                        SrimResultDto.ScenarioResult.builder()
                                .reductionRate(BigDecimal.ZERO)
                                .excessEarnings(new BigDecimal("15000"))
                                .enterpriseValue(new BigDecimal("156000"))
                                .fairValuePerShare(new BigDecimal("26100"))
                                .build(),
                        SrimResultDto.ScenarioResult.builder()
                                .reductionRate(new BigDecimal("-0.10"))
                                .excessEarnings(new BigDecimal("13500"))
                                .enterpriseValue(new BigDecimal("153000"))
                                .fairValuePerShare(new BigDecimal("25600"))
                                .build()
                ))
                .build();

        BDDMockito.given(srimService.calculate(BDDMockito.any()))
                .willReturn(dto);

        mockMvc.perform(get("/api/stocks/1/srim")
                        .param("basis", "YEAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.basis").value("YEAR"))
                .andExpect(jsonPath("$.data.year").value(2024))
                .andExpect(jsonPath("$.data.scenarios[0].fairValuePerShare").value(26100));
    }

    @Test
    @DisplayName("S-RIM 계산 API 요청이 잘못되면 400을 반환한다.")
    void srim_bad_request() throws Exception {
        BDDMockito.given(srimService.calculate(BDDMockito.any()))
                .willThrow(new IllegalArgumentException("invalid srim request"));

        mockMvc.perform(get("/api/stocks/1/srim")
                        .param("basis", "YEAR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                .andExpect(jsonPath("$.error.message").value("잘못된 요청입니다."))
                .andExpect(jsonPath("$.error.detail").value("invalid srim request"))
                .andExpect(jsonPath("$.error.path").value("/api/stocks/1/srim"));
    }

    @Test
    @DisplayName("S-RIM 계산 API에서 CustomException이 발생하면 ErrorCode를 반환한다.")
    void srim_custom_error() throws Exception {
        BDDMockito.given(srimService.calculate(BDDMockito.any()))
                .willThrow(new CustomException(CommonError.INVALID_INPUT, "custom srim error"));

        mockMvc.perform(get("/api/stocks/1/srim")
                        .param("basis", "YEAR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                .andExpect(jsonPath("$.error.message").value("잘못된 요청입니다."))
                .andExpect(jsonPath("$.error.detail").value("custom srim error"))
                .andExpect(jsonPath("$.error.path").value("/api/stocks/1/srim"));
    }

    @Test
    @DisplayName("S-RIM 계산 API 처리 중 오류가 발생하면 500을 반환한다.")
    void srim_internal_error() throws Exception {
        BDDMockito.given(srimService.calculate(BDDMockito.any()))
                .willThrow(new RuntimeException("unexpected error"));

        mockMvc.perform(get("/api/stocks/1/srim")
                        .param("basis", "YEAR"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-002"))
                .andExpect(jsonPath("$.error.message").value("서버 오류가 발생했습니다."))
                .andExpect(jsonPath("$.error.path").value("/api/stocks/1/srim"));
    }
}
