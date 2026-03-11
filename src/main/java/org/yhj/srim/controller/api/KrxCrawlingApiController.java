package org.yhj.srim.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.yhj.srim.controller.dto.ApiResponse;
import org.yhj.srim.controller.dto.CrawlAllMarketsResult;
import org.yhj.srim.service.facade.ManagementFacade;

/**
 * KRX 종목 크롤링 API 컨트롤러
 */
@RestController
@RequestMapping("/api/crawling/krx")
@RequiredArgsConstructor
@Slf4j
public class KrxCrawlingApiController {

    private final ManagementFacade managementFacade;

    /**
     * 전체 시장 크롤링 (KOSPI) 및 데이터 초기화 전체 로직 진행
     * GET /api/crawling/krx/all
     * - 기업 조회 로직
     * - 재무제표 및 전체 데이터 크롤링
     */
    @PostMapping("/all")
    public ApiResponse<CrawlAllMarketsResult> crawlAllMarkets() {
        log.info("전체 시장 크롤링 요청");
        return ApiResponse.success(managementFacade.runInitialSync());
    }

    /**
     * 기업 조회로직
     * @return
     */
    @PostMapping("/all/step1")
    public ApiResponse<CrawlAllMarketsResult> crawlAllMarketsStep1() {
        log.info("전체 시장 크롤링 요청 (STEP1)");
        return ApiResponse.success(managementFacade.collectMarketData());
    }

    /**
     * 데이터 조회 로직
     * 재무제표 및 전체 데이터 크롤링
     */
    @PostMapping("/all/step2")
    public ApiResponse<Void> crawlAllMarketsStep2() {
        log.info("전체 시장 크롤링 요청 (STEP2)");
        managementFacade.syncAllCompanies();
        return ApiResponse.success(null);
    }


    /**
     * 단일종목 조회 API
     * GET /api/crawling/krx/stocks/{tickerKrx}
     * - tickerKrx : KRX 고유 종목코드 (예: 005930)
     * - 해당 종목의 상세 정보 및 재무제표 크롤링 및 DB 저장
     * - 이미 존재하는 경우, 최신 데이터로 업데이트
     */
    @GetMapping("/stocks/{tickerKrx}")
    public ApiResponse<Void> crawlingStockInfo(@PathVariable String tickerKrx){
        log.info("단일 종목 조회 tickerKrx : {}", tickerKrx);
        managementFacade.syncSingleCompanyByTickerKrx(tickerKrx);
        log.info("단일 종목 조회 종료 tickerKrx : {}", tickerKrx);
        return ApiResponse.success(null);
    }

    @GetMapping("/stocks/{tickerKrx}/reset")
    public ApiResponse<Void> resetStockInfo(@PathVariable String tickerKrx) {
        log.info("단일 종목 초기화 데이터 삭제 tickerKrx : {}", tickerKrx);
        managementFacade.resetSingleCompanyByTickerKrx(tickerKrx);
        log.info("단일 종목 초기화 데이터 삭제 종료 tickerKrx : {}", tickerKrx);
        return ApiResponse.success(null);
    }


}
