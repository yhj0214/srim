package org.yhj.srim.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.yhj.srim.controller.dto.ApiResponse;
import org.yhj.srim.service.StockService;
import org.yhj.srim.service.dto.StockDto;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
public class StockApiController {

    private final StockService stockService;

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
}
