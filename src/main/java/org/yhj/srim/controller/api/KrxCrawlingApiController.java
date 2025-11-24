package org.yhj.srim.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.yhj.srim.controller.dto.ApiResponse;
import org.yhj.srim.controller.dto.CrawlAllMarketsResult;
import org.yhj.srim.service.DartCorpCodeSyncService;
import org.yhj.srim.service.KrxStockCrawlingService;

import java.util.HashMap;
import java.util.Map;

/**
 * KRX 종목 크롤링 API 컨트롤러
 */
@RestController
@RequestMapping("/api/crawling/krx")
@RequiredArgsConstructor
@Slf4j
public class KrxCrawlingApiController {

    private final KrxStockCrawlingService krxStockCrawlingService;
    private final DartCorpCodeSyncService dartCorpCodeSyncService;

    /**
     * 전체 시장 크롤링 (KOSPI + KOSDAQ)
     * GET /api/crawling/krx/all
     */
    @PostMapping("/all")
    public ApiResponse<CrawlAllMarketsResult> crawlAllMarkets() {
        log.info("전체 시장 크롤링 요청");

        int crawledCount = krxStockCrawlingService.crawlAllMarkets();
        int mappedCount = dartCorpCodeSyncService.syncFromXml();

        return ApiResponse.success(new CrawlAllMarketsResult(crawledCount, mappedCount));
    }

    /**
     * 특정 시장 크롤링
     * POST /api/crawling/krx?market=KOSPI
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> crawlMarket(
            @RequestParam(required = false) String market) {
        try {
            log.info("시장 크롤링 요청 - market: {}", market);
            
            int count = krxStockCrawlingService.crawlAndSaveStockList(market);
            
            Map<String, Object> result = new HashMap<>();
            result.put("market", market != null ? market : "전체");
            result.put("count", count);
            result.put("message", "크롤링이 완료되었습니다.");
            
            return ResponseEntity.ok(ApiResponse.success(result));
            
        } catch (Exception e) {
            log.error("크롤링 실패", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("크롤링 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 시장별 종목 수 조회
     * GET /api/crawling/krx/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStats() {
        try {
            Map<String, Long> stats = krxStockCrawlingService.getStockCountByMarket();
            return ResponseEntity.ok(ApiResponse.success(stats));
        } catch (Exception e) {
            log.error("통계 조회 실패", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("통계 조회 중 오류가 발생했습니다."));
        }
    }
}
