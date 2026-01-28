package org.yhj.srim.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.yhj.srim.controller.dto.ApiResponse;
import org.yhj.srim.service.facade.FinancialFacadeService;
import org.yhj.srim.service.dto.FinancialTableDto;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
public class FinancialApiController {

    private final FinancialFacadeService financialFacadeService;

    /**
     * 연간 재무 테이블 API (stockId 기반)
     */
    @GetMapping("/{stockId}/financial/annual")
    public ApiResponse<FinancialTableDto> getAnnualTableByStockId(
            @PathVariable Long stockId,
            @RequestParam(defaultValue = "10") int limit) {
        log.debug("연간 재무 테이블 요청 - stockId: {}, limit: {}", stockId, limit);
        FinancialTableDto result = financialFacadeService.getAnnualTableDbOnly(stockId, limit);

        return ApiResponse.success(result);
    }


}
