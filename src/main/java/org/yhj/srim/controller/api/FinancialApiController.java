package org.yhj.srim.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.yhj.srim.controller.dto.ApiResponse;
import org.yhj.srim.service.FinancialService;
import org.yhj.srim.service.dto.FinancialTableDto;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
public class FinancialApiController {

    private final FinancialService financialService;

    /**
     * 연간 재무 테이블 API (stockId 기반)
     */
    @GetMapping("/{stockId}/financial/annual")
    public ResponseEntity<ApiResponse<FinancialTableDto>> getAnnualTableByStockId(
            @PathVariable Long stockId,
            @RequestParam(defaultValue = "10") int limit) {
        
        log.info("=== 연간 재무 테이블 API 호출 (stockId) ===");
        log.info("stockId: {}, {}년 데이터(limit)", stockId, limit);
        
        try {
            FinancialTableDto table = financialService.getAnnualTableByStockId(stockId, limit);
            log.info("연간 재무 데이터 조회 성공: headers={}, rows={}", 
                    table.getHeaders().size(), table.getRows().size());
            return ResponseEntity.ok(ApiResponse.success(table));
        } catch (Exception e) {
            log.error("연간 재무 테이블 조회 실패: stockId={}", stockId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("재무 데이터 조회에 실패했습니다: " + e.getMessage()));
        }
    }

    /**
     * 분기 재무 테이블 API (stockId 기반)
     */
//    @GetMapping("/{stockId}/financial/quarter")
//    public ResponseEntity<ApiResponse<FinancialTableDto>> getQuarterTableByStockId(
//            @PathVariable Long stockId,
//            @RequestParam(defaultValue = "10") int limit) {
//
//        log.info("=== 분기 재무 테이블 API 호출 (stockId) ===");
//        log.info("stockId: {}, limit: {}", stockId, limit);
//
//        try {
//            FinancialTableDto table = financialService.getQuarterTableByStockId(stockId, limit);
//            log.info("분기 재무 데이터 조회 성공: headers={}, rows={}",
//                    table.getHeaders().size(), table.getRows().size());
//            return ResponseEntity.ok(ApiResponse.success(table));
//        } catch (Exception e) {
//            log.error("분기 재무 테이블 조회 실패: stockId={}", stockId, e);
//            return ResponseEntity.badRequest()
//                    .body(ApiResponse.error("재무 데이터 조회에 실패했습니다: " + e.getMessage()));
//        }
//    }

    /**
     * 연간 재무 테이블 API (market-ticker 기반)
     */
    @GetMapping("/{market}-{ticker}/financial/annual")
    public ResponseEntity<ApiResponse<FinancialTableDto>> getAnnualTableByTicker(
            @PathVariable String market,
            @PathVariable String ticker,
            @RequestParam(defaultValue = "5") int limit) {
        
        log.info("=== 연간 재무 테이블 API 호출 (ticker) ===");
        log.info("market: {}, ticker: {}, limit: {}", market, ticker, limit);
        
        try {
            FinancialTableDto table = financialService.getAnnualTableByTicker(market, ticker, limit);
            log.info("연간 재무 데이터 조회 성공: headers={}, rows={}", 
                    table.getHeaders().size(), table.getRows().size());
            return ResponseEntity.ok(ApiResponse.success(table));
        } catch (Exception e) {
            log.error("연간 재무 테이블 조회 실패: market={}, ticker={}", market, ticker, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("재무 데이터 조회에 실패했습니다: " + e.getMessage()));
        }
    }

//    /**
//     * 분기 재무 테이블 API (market-ticker 기반)
//     */
//    @GetMapping("/{market}-{ticker}/financial/quarter")
//    public ResponseEntity<ApiResponse<FinancialTableDto>> getQuarterTableByTicker(
//            @PathVariable String market,
//            @PathVariable String ticker,
//            @RequestParam(defaultValue = "8") int limit) {
//
//        log.info("=== 분기 재무 테이블 API 호출 (ticker) ===");
//        log.info("market: {}, ticker: {}, limit: {}", market, ticker, limit);
//
//        try {
//            FinancialTableDto table = financialService.getQuarterTableByTicker(market, ticker, limit);
//            log.info("분기 재무 데이터 조회 성공: headers={}, rows={}",
//                    table.getHeaders().size(), table.getRows().size());
//            return ResponseEntity.ok(ApiResponse.success(table));
//        } catch (Exception e) {
//            log.error("분기 재무 테이블 조회 실패: market={}, ticker={}", market, ticker, e);
//            return ResponseEntity.badRequest()
//                    .body(ApiResponse.error("재무 데이터 조회에 실패했습니다: " + e.getMessage()));
//        }
//    }
}
