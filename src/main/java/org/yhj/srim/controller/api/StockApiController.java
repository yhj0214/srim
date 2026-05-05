package org.yhj.srim.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.yhj.srim.controller.dto.ApiResponse;
import org.yhj.srim.service.domain.CompanyViewService;
import org.yhj.srim.service.application.PriceChartApplicationService;
import org.yhj.srim.service.domain.StockService;
import org.yhj.srim.service.dto.PopularStockDto;
import org.yhj.srim.service.dto.StockDto;
import org.yhj.srim.service.dto.StockPriceDto;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
public class StockApiController {

    private final CompanyViewService companyViewService;
    private final StockService stockService;
    private final PriceChartApplicationService priceChartFacadeService;

    /**
     * 종목 검색 API
     */
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

    /**
     * 종목 상세 조회 API (ID)
     */
    @GetMapping("/{id}")
    public ApiResponse<StockDto> getById(@PathVariable Long id) {
        StockDto stock = stockService.getById(id);
        return ApiResponse.success(stock);
    }

    /**
     * 종목 상세 조회 API (market-ticker)
     */
    @GetMapping("/{market}-{ticker}")
    public ApiResponse<StockDto> getByTicker(
            @PathVariable String market,
            @PathVariable String ticker) {

        StockDto stock = stockService.getByTicker(market, ticker);
        return ApiResponse.success(stock);
    }

    @GetMapping("/popular")
    public ApiResponse<List<PopularStockDto>> getPopularStocks(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(companyViewService.findPopularStocks(days, limit));
    }
    
    /**
     * 주가 그래프 데이터 조회 API
     * 
     * @param companyId 회사 ID
     * @param startDate 시작일 (optional, 기본값: 1년 전)
     * @param endDate 종료일 (optional, 기본값: 오늘)
     * @return 주가 데이터 및 시나리오별 적정주가
     */
    @GetMapping("/{companyId}/price-chart")
    public ApiResponse<StockPriceDto> getPriceChart(
            @PathVariable Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("=== 주가 그래프 데이터 조회 API 호출 ===");
        log.info("companyId: {}, startDate: {}, endDate: {}", companyId, startDate, endDate);

        StockPriceDto priceData = priceChartFacadeService.getPriceChart(companyId, startDate, endDate);
        log.info("=== 주가 그래프 데이터 조회 API 종료 ===");

        return ApiResponse.success(priceData);
    }
}
