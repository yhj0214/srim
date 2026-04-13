package org.yhj.srim.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.yhj.srim.controller.dto.ApiResponse;
import org.yhj.srim.controller.dto.SrimRequestDto;
import org.yhj.srim.service.facade.FinancialFacadeService;
import org.yhj.srim.service.dto.PeriodType;
import org.yhj.srim.service.domain.SrimService;
import org.yhj.srim.service.dto.FinancialTableDto;
import org.yhj.srim.service.dto.SrimCalculateCommand;
import org.yhj.srim.service.dto.SrimResultDto;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
public class FinancialApiController {
    private final FinancialFacadeService financialFacadeService;
    private final SrimService srimService;

    /**
     * 재무 테이블 API (stockId 기반)
     */
    @GetMapping("/{stockId}/financial")
    public ApiResponse<FinancialTableDto> getFinancialTableByStockId(
            @PathVariable Long stockId,
            @RequestParam(defaultValue = "annual") String period,
            @RequestParam(defaultValue = "15") int limit) {
        String normalizedPeriod = period == null ? "annual" : period.trim().toLowerCase();
        PeriodType periodType = switch (normalizedPeriod) {
            case "", "annual" -> PeriodType.ANNUAL;
            case "quarter" -> PeriodType.QUARTER;
            default -> throw new IllegalArgumentException("invalid period: " + period);
        };
        log.debug("재무 테이블 요청 - stockId: {}, period: {}, limit: {}", stockId, periodType, limit);
        FinancialTableDto result = financialFacadeService.getFinancialTable(stockId, limit, periodType);

        return ApiResponse.success(result);
    }

    /**
     * S-RIM 계산 API
     *
     * @param companyId 회사 ID
     * @return S-RIM 계산 결과
     */
    @GetMapping("/{companyId}/srim")
    public ApiResponse<SrimResultDto> calculateSrim(
            @PathVariable Long companyId,
            @ModelAttribute SrimRequestDto request) {

        SrimCalculateCommand command = request.toCommand(companyId, LocalDate.now());
        SrimResultDto result = srimService.calculate(command);
        return ApiResponse.success(result);
    }

}
