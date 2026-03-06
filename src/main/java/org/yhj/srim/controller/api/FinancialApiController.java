package org.yhj.srim.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.yhj.srim.controller.dto.ApiResponse;
import org.yhj.srim.controller.dto.SrimRequestDto;
import org.yhj.srim.service.facade.FinancialFacadeService;
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
     * 연간 재무 테이블 API (stockId 기반)
     */
    @GetMapping("/{stockId}/financial/annual")
    public ApiResponse<FinancialTableDto> getAnnualTableByStockId(
            @PathVariable Long stockId,
            @RequestParam(defaultValue = "15") int limit) {
        log.debug("연간 재무 테이블 요청 - stockId: {}, limit: {}", stockId, limit);
        FinancialTableDto result = financialFacadeService.getAnnualTable(stockId, limit);

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
