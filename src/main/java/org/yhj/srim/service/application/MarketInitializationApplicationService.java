package org.yhj.srim.service.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.yhj.srim.client.dto.KisSpreadRow;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CommonError;
import org.yhj.srim.common.exception.code.CrawlingError;
import org.yhj.srim.controller.dto.CrawlAllMarketsResult;
import org.yhj.srim.service.application.dto.DailyBondYieldFetchResult;
import org.yhj.srim.service.crawl.KisSpreadCrawlingService;
import org.yhj.srim.service.crawl.KrxStockCrawlingService;
import org.yhj.srim.service.crawl.dto.StockCodeDraft;
import org.yhj.srim.service.domain.BondYieldCurveService;
import org.yhj.srim.service.domain.DartCorpCodeSyncService;
import org.yhj.srim.service.domain.FailedJobService;
import org.yhj.srim.service.domain.StockService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketInitializationApplicationService {
    private final KrxStockCrawlingService krxStockCrawlingService;
    private final KisSpreadCrawlingService kisSpreadCrawlingService;
    private final StockService stockService;
    private final DartCorpCodeSyncService dartCorpCodeSyncService;
    private final BondYieldCurveService bondYieldCurveService;
    private final FailedJobService failedJobService;
    private final ThreadPoolTaskExecutor bondYieldTaskExecutor;

    public CrawlAllMarketsResult marketCrawling() {
        List<StockCodeDraft> stockCodeDrafts = krxStockCrawlingService.fetchStockList("KOSPI");
        int saved = stockService.saveStockDrafts(stockCodeDrafts);
        int mappedCount = dartCorpCodeSyncService.syncFromXml();
        return new CrawlAllMarketsResult(saved, mappedCount);
    }

    public void crawlAndSaveBondYield(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new CustomException(CommonError.INVALID_INPUT, "startDate/endDate는 null일 수 없습니다.");
        }
        if (startDate.isAfter(endDate)) {
            throw new CustomException(CommonError.INVALID_INPUT, "startDate는 endDate보다 이후일 수 없습니다.");
        }

        int processedDays = 0;
        int upsertCount = 0;
        int skippedDays = 0;

        for (YearMonth month : splitMonths(startDate, endDate)) {
            MonthBondYieldResult monthResult = processBondYieldMonth(month, startDate, endDate);
            processedDays += monthResult.processedDays();
            skippedDays += monthResult.skippedDays();
            upsertCount += monthResult.upsertCount();

            log.info("BondYield monthly batch done month={} processedDays={}, upserts={}, skippedDays={}",
                    month, monthResult.processedDays(), monthResult.upsertCount(), monthResult.skippedDays());
        }

        log.info("BondYield done processedDays={}, upserts={}, skippedDays={}, range={}~{}",
                processedDays, upsertCount, skippedDays, startDate, endDate);
    }

    public void retryBondYieldForDate(LocalDate date) {
        List<KisSpreadRow> rows = kisSpreadCrawlingService.fetchSpreadRows(date);
        if (rows.isEmpty()) {
            throw new CustomException(CrawlingError.KIS_REQUEST_FAILED, "date=" + date + ", empty rows");
        }

        int upsertCount = bondYieldCurveService.upsertDailyRows(date, rows);
        log.info("BondYield retry success date={} upserts={}", date, upsertCount);
    }

    private MonthBondYieldResult processBondYieldMonth(YearMonth month, LocalDate startDate, LocalDate endDate) {
        LocalDate monthStart = month.atDay(1).isBefore(startDate) ? startDate : month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth().isAfter(endDate) ? endDate : month.atEndOfMonth();
        List<LocalDate> businessDays = getBusinessDays(monthStart, monthEnd);
        int skippedDays = (int) monthStart.datesUntil(monthEnd.plusDays(1))
                .filter(date -> !businessDays.contains(date))
                .count();

        List<DailyBondYieldFetchResult> fetchResults = fetchMonthResults(businessDays);
        int processedDays = 0;
        int upsertCount = 0;

        for (DailyBondYieldFetchResult result : fetchResults) {
            if (!result.isSuccess() || result.getRows().isEmpty()) {
                skippedDays++;
                continue;
            }

            upsertCount += bondYieldCurveService.upsertDailyRows(result.getDate(), result.getRows());
            processedDays++;
        }

        return new MonthBondYieldResult(processedDays, upsertCount, skippedDays);
    }

    private List<YearMonth> splitMonths(LocalDate startDate, LocalDate endDate) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth current = YearMonth.from(startDate);
        YearMonth last = YearMonth.from(endDate);

        while (!current.isAfter(last)) {
            months.add(current);
            current = current.plusMonths(1);
        }

        return months;
    }

    private List<LocalDate> getBusinessDays(LocalDate startDate, LocalDate endDate) {
        List<LocalDate> businessDays = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            DayOfWeek dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                businessDays.add(date);
            }
        }
        return businessDays;
    }

    private List<DailyBondYieldFetchResult> fetchMonthResults(List<LocalDate> businessDays) {
        if (businessDays.isEmpty()) {
            return List.of();
        }

        ExecutorService executor = bondYieldTaskExecutor.getThreadPoolExecutor();
        try {
            List<Callable<DailyBondYieldFetchResult>> tasks = businessDays.stream()
                    .<Callable<DailyBondYieldFetchResult>>map(date -> () -> fetchDailyBondYield(date))
                    .toList();

            List<Future<DailyBondYieldFetchResult>> futures = executor.invokeAll(tasks);
            List<DailyBondYieldFetchResult> results = new ArrayList<>(futures.size());
            for (Future<DailyBondYieldFetchResult> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof CustomException customException) {
                        log.warn("{} [{}] detail={}",
                                customException.getErrorCode().getMessage(),
                                customException.getErrorCode().getCode(),
                                customException.getDetail());
                    } else {
                        log.error("KIS 수익률 월 배치 결과 수집 실패", cause);
                        throw new CustomException(CrawlingError.KIS_REQUEST_FAILED, cause.getMessage());
                    }
                }
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException(CrawlingError.KIS_REQUEST_FAILED, "batch interrupted");
        }
    }

    private DailyBondYieldFetchResult fetchDailyBondYield(LocalDate date) {
        try {
            List<KisSpreadRow> rows = kisSpreadCrawlingService.fetchSpreadRows(date);
            if (rows.isEmpty()) {
                log.warn("KIS 수익률 데이터 없음 date={}", date);
                return DailyBondYieldFetchResult.failure(date);
            }
            return DailyBondYieldFetchResult.success(date, rows);
        } catch (CustomException e) {
            log.warn("{} [{}] date={} detail={}",
                    e.getErrorCode().getMessage(),
                    e.getErrorCode().getCode(),
                    date,
                    e.getDetail());
            failedJobService.enqueueBondYieldFailure(date, buildBondYieldFailureDetail(e));
            return DailyBondYieldFetchResult.failure(date);
        }
    }

    private String buildBondYieldFailureDetail(CustomException e) {
        if (e.getDetail() == null || e.getDetail().isBlank()) {
            return e.getMessage();
        }
        return e.getMessage() + " (" + e.getDetail() + ")";
    }

    private record MonthBondYieldResult(int processedDays, int upsertCount, int skippedDays) {
    }
}
