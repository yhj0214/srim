package org.yhj.srim.service.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.yhj.srim.client.dto.DaliyPrice;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.FailedJob;
import org.yhj.srim.service.crawl.NaverCrawlingService;
import org.yhj.srim.service.domain.FailedJobService;
import org.yhj.srim.service.domain.StockPriceService;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScheduledSyncOrchestrator {

    private final FailedJobService failedJobService;
    private final MarketInitializationApplicationService marketInitializationApplicationService;
    private final NaverCrawlingService naverCrawlingService;
    private final StockPriceService stockPriceService;

    @Value("${app.scheduler.zone:Asia/Seoul}")
    private String schedulerZone;

    @Value("${app.scheduler.price.backfill-days:3}")
    private int priceBackfillDays;

    public void syncDailyPrices() {
        LocalDate baseDate = LocalDate.now(ZoneId.of(schedulerZone)).minusDays(1);
        LocalDate startDate = baseDate.minusDays(Math.max(priceBackfillDays, 1) - 1L);
        List<Company> companies = stockPriceService.findAllCompaniesWithStockCode();

        log.info("[SCHED-PRICE] start companie-count={} range={}~{}", companies.size(), startDate, baseDate);

        int successCount = 0;
        int failureCount = 0;
        int totalSaved = 0;

        for (Company company : companies) {
            String tickerKrx = company.getStockCode() != null ? company.getStockCode().getTickerKrx() : null;
            if (tickerKrx == null || tickerKrx.isBlank()) {
                log.warn("[SCHED-PRICE] skip missing ticker companyId={}", company.getCompanyId());
                continue;
            }

            try {
                totalSaved += syncRecentDailyPrices(company.getCompanyId(), tickerKrx, startDate, baseDate);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                log.error("[SCHED-PRICE] failed companyId={} ticker={} range={}~{}",
                        company.getCompanyId(), tickerKrx, startDate, baseDate, e);
            }
        }

        log.info("[SCHED-PRICE] done companies={} success={} failed={} saved={}",
                companies.size(), successCount, failureCount, totalSaved);
    }

    int syncRecentDailyPrices(Long companyId, String tickerKrx, LocalDate startDate, LocalDate endDate) {
        List<DaliyPrice> dailyPrices =
                naverCrawlingService.fetchDailyPrices(tickerKrx, startDate, endDate);
        return stockPriceService.savePrices(companyId, dailyPrices);
    }

    public void syncDailyBondYields() {
        log.info("[SCHED-BOND] start");
        // TODO: 회사채 수익률 동기화 로직 연결
        log.info("[SCHED-BOND] done");
    }

    public void syncAnnualFinancialStatements() {
        log.info("[SCHED-FS] start");
        // TODO: 사업보고서 시즌 대상 재무/주식수 갱신 로직 연결
        log.info("[SCHED-FS] done");
    }

    public void retryFailedBondYields() {
        List<FailedJob> jobs = failedJobService.findDueBondYieldJobs();
        if (jobs.isEmpty()) {
            log.debug("[SCHED-BOND-RETRY] no due jobs");
            return;
        }

        log.info("[SCHED-BOND-RETRY] start size={}", jobs.size());
        for (FailedJob job : jobs) {
            if (job.getTargetDate() == null) {
                log.warn("[SCHED-BOND-RETRY] skip missing targetDate jobId={}", job.getFailedJobId());
                continue;
            }

            failedJobService.markRetrying(job);
            try {
                marketInitializationApplicationService.retryBondYieldForDate(job.getTargetDate());
                failedJobService.markDone(job);
            } catch (CustomException e) {
                failedJobService.rescheduleBondYieldFailure(job, e);
                log.warn("[SCHED-BOND-RETRY] failed date={} detail={}", job.getTargetDate(), e.getDetail());
            } catch (Exception e) {
                failedJobService.rescheduleBondYieldFailure(
                        job,
                        new CustomException(org.yhj.srim.common.exception.code.CrawlingError.KIS_REQUEST_FAILED, e.getMessage())
                );
                log.error("[SCHED-BOND-RETRY] unexpected failure date={}", job.getTargetDate(), e);
            }
        }
        log.info("[SCHED-BOND-RETRY] done size={}", jobs.size());
    }
}
