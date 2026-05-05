package org.yhj.srim.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.yhj.srim.controller.dto.ApiResponse;
import org.yhj.srim.controller.dto.SrimRequestDto;
import org.yhj.srim.service.application.FinancialApplicationService;
import org.yhj.srim.service.application.PriceChartApplicationService;
import org.yhj.srim.service.domain.CompanyViewService;
import org.yhj.srim.service.domain.SrimService;
import org.yhj.srim.service.domain.StockService;
import org.yhj.srim.service.dto.FinancialTableDto;
import org.yhj.srim.service.dto.PeriodType;
import org.yhj.srim.service.dto.PopularStockDto;
import org.yhj.srim.service.dto.SrimCalculateCommand;
import org.yhj.srim.service.dto.SrimResultDto;
import org.yhj.srim.service.dto.StockDto;
import org.yhj.srim.service.dto.StockPriceDto;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
public class QueryApiController {

    private final CompanyViewService companyViewService;
    private final StockService stockService;
    private final PriceChartApplicationService priceChartApplicationService;
    private final FinancialApplicationService financialApplicationService;
    private final SrimService srimService;

    @GetMapping
    public ApiResponse<Page<StockDto>> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "companyName") String sort) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sort));
        Page<StockDto> stocks = stockService.search(q, pageable);

        return ApiResponse.success(stocks);
    }

    @GetMapping("/popular")
    public ApiResponse<List<PopularStockDto>> getPopularStocks(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(companyViewService.findPopularStocks(days, limit));
    }

    @GetMapping("/{companyId}/price-chart")
    public ApiResponse<StockPriceDto> getPriceChart(
            @PathVariable Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("=== 주가 그래프 데이터 조회 API 호출 ===");
        log.info("companyId: {}, startDate: {}, endDate: {}", companyId, startDate, endDate);

        StockPriceDto priceData = priceChartApplicationService.getPriceChart(companyId, startDate, endDate);
        log.info("=== 주가 그래프 데이터 조회 API 종료 ===");

        return ApiResponse.success(priceData);
    }

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
        FinancialTableDto result = financialApplicationService.getFinancialTable(stockId, limit, periodType);

        return ApiResponse.success(result);
    }

    @GetMapping("/{companyId}/srim")
    public ApiResponse<SrimResultDto> calculateSrim(
            @PathVariable Long companyId,
            @ModelAttribute SrimRequestDto request) {
        SrimCalculateCommand command = request.toCommand(companyId, LocalDate.now());
        SrimResultDto result = srimService.calculate(command);
        return ApiResponse.success(result);
    }
}
