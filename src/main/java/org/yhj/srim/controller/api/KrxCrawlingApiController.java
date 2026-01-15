package org.yhj.srim.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.yhj.srim.controller.dto.ApiResponse;
import org.yhj.srim.controller.dto.CrawlAllMarketsResult;
import org.yhj.srim.service.facade.ManagementFacade;
import org.yhj.srim.service.crawl.KrxStockCrawlingService;

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
    private final ManagementFacade managementFacade;

    /**
     * 전체 시장 크롤링 (KOSPI + KOSDAQ)
     * GET /api/crawling/krx/all
     */
    @PostMapping("/all")
    public ApiResponse<CrawlAllMarketsResult> crawlAllMarkets() {
        log.info("전체 시장 크롤링 요청");
        return ApiResponse.success(managementFacade.initializeAll());
    }

    @PostMapping("/all/step1")
    public ApiResponse<CrawlAllMarketsResult> crawlAllMarketsStep1() {
        log.info("전체 시장 크롤링 요청 (STEP1)");
        return ApiResponse.success(managementFacade.step1MarketSync());
    }

    @GetMapping("/stocks/{tickerKrx}")
    public ApiResponse<Void> crawlingStockInfo(@PathVariable String tickerKrx){
        log.info("stickerKrx : {}", tickerKrx);
        managementFacade.findStockInfo(tickerKrx);
        return ApiResponse.success("요청에 성공하였습니다.", null);
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
