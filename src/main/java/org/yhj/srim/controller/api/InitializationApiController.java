package org.yhj.srim.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.yhj.srim.controller.dto.ApiResponse;
import org.yhj.srim.controller.dto.CrawlAllMarketsResult;
import org.yhj.srim.service.application.AnnualXbrlPipelineOrchestrator;
import org.yhj.srim.service.application.ManagementOrchestrator;
import org.yhj.srim.service.application.QuarterXbrlPipelineOrchestrator;

@RestController
@RequiredArgsConstructor
@Slf4j
public class InitializationApiController {

    private final ManagementOrchestrator managementOrchestrator;
    private final AnnualXbrlPipelineOrchestrator annualXbrlPipelineOrchestrator;
    private final QuarterXbrlPipelineOrchestrator quarterXbrlPipelineOrchestrator;

    @PostMapping("/api/crawling/krx/all")
    public ApiResponse<CrawlAllMarketsResult> crawlAllMarkets(
            @RequestParam(defaultValue = "2015") int startYear,
            @RequestParam(defaultValue = "CFS") String fsDiv) {
        log.info("전체 시장 크롤링 요청");
        return ApiResponse.success(managementOrchestrator.runInitialSync(startYear, fsDiv));
    }

    @PostMapping("/api/crawling/krx/all/step1")
    public ApiResponse<CrawlAllMarketsResult> crawlAllMarketsStep1() {
        log.info("전체 시장 크롤링 요청 (STEP1)");
        return ApiResponse.success(managementOrchestrator.collectMarketData());
    }

    @PostMapping("/api/crawling/krx/all/step2")
    public ApiResponse<Void> crawlAllMarketsStep2(
            @RequestParam(defaultValue = "2015") int startYear,
            @RequestParam(defaultValue = "CFS") String fsDiv) {
        log.info("전체 시장 크롤링 요청 (STEP2)");
        managementOrchestrator.syncAllCompanies(startYear, fsDiv);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/crawling/krx/stocks/{tickerKrx}/reset")
    public ApiResponse<Void> resetStockInfo(@PathVariable String tickerKrx) {
        log.info("단일 종목 초기화 데이터 삭제 tickerKrx : {}", tickerKrx);
        managementOrchestrator.resetSingleCompanyByTickerKrx(tickerKrx);
        log.info("단일 종목 초기화 데이터 삭제 종료 tickerKrx : {}", tickerKrx);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/stocks/{stockId}/xbrl/annual/run")
    public ApiResponse<Integer> runAnnualXbrlPipeline(
            @PathVariable Long stockId,
            @RequestParam(defaultValue = "2015") int startYear,
            @RequestParam(defaultValue = "CFS") String fsDiv) {
        int endYear = java.time.LocalDate.now().getYear() - 1;
        int completedAnnualYears = annualXbrlPipelineOrchestrator.runAnnualXbrlPipeline(
                stockId,
                startYear,
                endYear,
                fsDiv
        );
        quarterXbrlPipelineOrchestrator.runQuarterXbrlPipelineRange(
                stockId,
                startYear,
                endYear,
                fsDiv
        );
        return ApiResponse.success(
                completedAnnualYears
        );
    }

    @GetMapping("/api/stocks/{stockId}/xbrl/quarter/run")
    public ApiResponse<Integer> runQuarterXbrlPipeline(
            @PathVariable Long stockId,
            @RequestParam int fiscalYear,
            @RequestParam int fiscalQuarter,
            @RequestParam(defaultValue = "CFS") String fsDiv) {
        return ApiResponse.success(
                quarterXbrlPipelineOrchestrator.runQuarterXbrlPipeline(
                        stockId,
                        fiscalYear,
                        fiscalQuarter,
                        fsDiv
                )
        );
    }
}
