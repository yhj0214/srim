package org.yhj.srim.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.yhj.srim.controller.dto.ApiResponse;
import org.yhj.srim.service.StockService;
import org.yhj.srim.service.StockPriceService;
import org.yhj.srim.service.dto.StockDto;
import org.yhj.srim.service.dto.StockPriceDto;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
public class StockApiController {

    private final StockService stockService;
    private final StockPriceService stockPriceService;

    /**
     * 종목 검색 API
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<StockDto>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "companyName") String sort) {
        
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(sort));
            Page<StockDto> stocks = stockService.search(q, pageable);
            
            return ResponseEntity.ok(ApiResponse.success(stocks));
        } catch (Exception e) {
            log.error("종목 검색 실패", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("종목 검색에 실패했습니다: " + e.getMessage()));
        }
    }

    /**
     * 종목 상세 조회 API (ID)
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockDto>> getById(@PathVariable Long id) {
        try {
            StockDto stock = stockService.getById(id);
            return ResponseEntity.ok(ApiResponse.success(stock));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("종목 조회 실패", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("종목 조회에 실패했습니다: " + e.getMessage()));
        }
    }

    /**
     * 종목 상세 조회 API (market-ticker)
     */
    @GetMapping("/{market}-{ticker}")
    public ResponseEntity<ApiResponse<StockDto>> getByTicker(
            @PathVariable String market,
            @PathVariable String ticker) {
        
        try {
            StockDto stock = stockService.getByTicker(market, ticker);
            return ResponseEntity.ok(ApiResponse.success(stock));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("종목 조회 실패", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("종목 조회에 실패했습니다: " + e.getMessage()));
        }
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
    public ResponseEntity<ApiResponse<StockPriceDto>> getPriceChart(
            @PathVariable Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        try {
            log.info("=== 주가 그래프 데이터 조회 API 호출 ===");
            log.info("companyId: {}, startDate: {}, endDate: {}", companyId, startDate, endDate);
            
            StockPriceDto priceData = stockPriceService.getStockPriceData(companyId, startDate, endDate);
            
            return ResponseEntity.ok(ApiResponse.success(priceData));
        } catch (Exception e) {
            log.error("주가 그래프 데이터 조회 실패", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("주가 그래프 데이터 조회에 실패했습니다: " + e.getMessage()));
        }
    }
}
