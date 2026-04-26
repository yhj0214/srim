package org.yhj.srim.service.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.yhj.srim.client.DartReportType;
import org.yhj.srim.client.dto.DartShareStatusRow;
import org.yhj.srim.client.dto.KisSpreadRow;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CommonError;
import org.yhj.srim.common.exception.code.CrawlingError;
import org.yhj.srim.common.exception.code.StockError;
import org.yhj.srim.controller.dto.CrawlAllMarketsResult;
import org.yhj.srim.repository.entity.*;
import org.yhj.srim.service.crawl.DartCrawlingService;
import org.yhj.srim.service.crawl.dto.StockCodeDraft;
import org.yhj.srim.service.crawl.KisSpreadCrawlingService;
import org.yhj.srim.service.domain.BondYieldCurveService;
import org.yhj.srim.service.domain.DartCorpCodeSyncService;
import org.yhj.srim.service.domain.FailedJobService;
import org.yhj.srim.service.domain.FinancialMetricService;
import org.yhj.srim.service.domain.FinancialService;
import org.yhj.srim.service.crawl.KrxStockCrawlingService;
import org.yhj.srim.service.facade.dto.DailyBondYieldFetchResult;
import org.yhj.srim.service.facade.dto.CollectXbrlRawCommand;
import org.yhj.srim.service.domain.StockService;
import org.yhj.srim.service.dto.FinancialTableDto;
import org.yhj.srim.service.dto.PeriodType;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialFacadeService {
    private static final List<DartReportType> INITIAL_FINANCIAL_REPORT_TYPES = List.of(
            DartReportType.FIRST_QUARTER,
            DartReportType.HALF_YEAR,
            DartReportType.THIRD_QUARTER,
            DartReportType.ANNUAL
    );

    private final KrxStockCrawlingService krxStockCrawlingService;
    private final DartCrawlingService dartCrawlingService;
    private final KisSpreadCrawlingService kisSpreadCrawlingService;

    private final StockService stockService;
    private final DartCorpCodeSyncService dartCorpCodeSyncService;
    private final FinancialService financialService;
    private final FinancialMetricService financialMetricService;
    private final AnnualXbrlPipelineFacadeService annualXbrlPipelineFacadeService;
    private final BondYieldCurveService bondYieldCurveService;
    private final FailedJobService failedJobService;

    private final ThreadPoolTaskExecutor bondYieldTaskExecutor;

    /**
     * 1. company 조회, 없을 시 생성
     * 2. 재무제표, 주식 수 원천데이터 크롤링 및 저장
     */
    public Company crawlAnnualTable(Long stockId, int startYear) {

        return financialService.findCompanyByStockId(stockId)
                .orElseGet(() ->{

                    Company company = financialService.createCompany(stockId);
                    log.debug("신규 company 생성 : stockId = {}, companyId = {}", stockId, company.getCompanyId());

                    initializeCompanyData(company, startYear, LocalDate.now().getYear());
                    return company;
                });
    }

    public FinancialTableDto getFinancialTable(Long stockId, int limit, PeriodType periodType) {
        Company company = financialService.findCompanyByStockId(stockId)
                .orElseThrow(() -> new CustomException(StockError.COMPANY_NOT_FOUND));

        return financialService.getFinancialTable(company, limit, periodType);
    }

    public int rebuildCompanyMetrics(Long companyId, int startYear, int endYear) {
        return financialMetricService.rebuildCompanyMetrics(companyId, startYear, endYear);
    }

    public int processAnnualMetricsFromXbrl(Long stockId, int fiscalYear, String fsDiv) {
        return annualXbrlPipelineFacadeService.processAnnualMetricsFromXbrl(stockId, fiscalYear, fsDiv);
    }

    public int collectAnnualFilingMetadata(Long stockId, int startYear, int endYear, String fsDiv) {
        return annualXbrlPipelineFacadeService.collectAnnualFilingMetadata(stockId, startYear, endYear, fsDiv);
    }

    public int processAnnualMetricsFromXbrl(Long stockId, int startYear, int endYear, String fsDiv) {
        return annualXbrlPipelineFacadeService.processAnnualMetricsFromXbrl(stockId, startYear, endYear, fsDiv);
    }

    public Long collectAnnualFilingMetadata(Long stockId, int fiscalYear, String fsDiv) {
        return annualXbrlPipelineFacadeService.collectAnnualFilingMetadata(stockId, fiscalYear, fsDiv);
    }

    public Long runAnnualXbrlPipeline(Long stockId, String corpCode,
                                      String rceptNo, int bsnsYear, String fsDiv) {
        return annualXbrlPipelineFacadeService.runAnnualXbrlPipeline(stockId, corpCode, rceptNo, bsnsYear, fsDiv);
    }

    public Long runAnnualXbrlPipeline(Long stockId, int fiscalYear, String fsDiv) {
        return annualXbrlPipelineFacadeService.runAnnualXbrlPipeline(stockId, fiscalYear, fsDiv);
    }

    public int runAnnualXbrlPipeline(Long stockId, int startYear, int endYear, String fsDiv) {
        return annualXbrlPipelineFacadeService.runAnnualXbrlPipeline(stockId, startYear, endYear, fsDiv);
    }

    public Long collectXbrlRaw(CollectXbrlRawCommand command) {
        return annualXbrlPipelineFacadeService.collectXbrlRaw(command);
    }

    // 회사 초기화용 원천 데이터 적재
    private void initializeCompanyData(Company company, int startYear, int endYear) {
        String corpCode = company.getStockCode().getDartCorpCode();
        Long companyId = company.getCompanyId();


        log.info("재무정보 조회 전체 파이프라인 실행 - companyId={}, corpCode={}, year {}~{}",
                companyId, corpCode, startYear, endYear);

        for (int year = endYear - 1; year >= startYear; year--) {
            log.debug("{}년 크롤링 및 계산 진행", year);

            // 재무제표 크롤링
            List<DartCrawlingService.FinancialStatementBatch> batches =
                    dartCrawlingService.crawlFinancialStatements(corpCode, year, INITIAL_FINANCIAL_REPORT_TYPES);
            // 재무제표 정보 저장 Line, Filing
            for (DartCrawlingService.FinancialStatementBatch batch : batches) {
                financialService.replaceFinancialStatements(corpCode, companyId, batch.rows());
            }

            // 주식개수정보 크롤링
            List<DartShareStatusRow> shareStatusRows = dartCrawlingService.crawlShareStatus(corpCode, year);
            // 주식개수 정보 저장 StockShareStatus
            stockService.replaceShareStatus(company, year,shareStatusRows);

        }
        log.info("재무정보 크롤링 및 저장 완료");
//        financialService.updateCompanyShareInfo(companyId);
    }
    public CrawlAllMarketsResult marketCrawling() {
        // 크롤링 및 데이터 추출
        List<StockCodeDraft> stockCodeDrafts = krxStockCrawlingService.fetchStockList("KOSPI");

        // 추출 데이터 저장 StockCode로 변환 및 저장
        int saved = stockService.saveStockDrafts(stockCodeDrafts);

        // xml파일의 corp_code, corp_name, stock_code 별도 테이블 저장
        // 별도 테이블과 stockcode테이블을 조인하여 stockcode 데이블 갱신
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

    public void retryBondYieldForDate(LocalDate date) {
        List<KisSpreadRow> rows = kisSpreadCrawlingService.fetchSpreadRows(date);
        if (rows.isEmpty()) {
            throw new CustomException(CrawlingError.KIS_REQUEST_FAILED, "date=" + date + ", empty rows");
        }

        int upsertCount = bondYieldCurveService.upsertDailyRows(date, rows);
        log.info("BondYield retry success date={} upserts={}", date, upsertCount);
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
