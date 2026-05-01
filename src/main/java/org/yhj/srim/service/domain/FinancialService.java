package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.client.DartReportType;
import org.yhj.srim.client.dto.DartFsRow;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CommonError;
import org.yhj.srim.common.exception.code.StockError;
import org.yhj.srim.repository.*;
import org.yhj.srim.repository.entity.*;
import org.yhj.srim.service.domain.calculator.AnnualXbrlBaseMetricCalculator;
import org.yhj.srim.service.domain.calculator.AnnualXbrlDerivedMetricCalculator;
import org.yhj.srim.service.domain.calculator.AnnualXbrlPerShareMetricCalculator;
import org.yhj.srim.service.dto.FinancialTableDto;
import org.yhj.srim.service.dto.FsRawBundle;
import org.yhj.srim.service.dto.PeriodType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FinancialService {
    private static final List<String> FLOW_METRIC_CODES = List.of(
            "SALES",
            "OP_INC",
            "NET_INC",
            "NET_INC_OWNER",
            "NET_INC_NONCONT"
    );

    private static final List<String> ATTRIBUTABLE_NET_INCOME_CODES = List.of(
            "NET_INC_OWNER",
            "NET_INC_NONCONT"
    );

    private static final List<String> BASE_METRIC_CODES = List.of(
            "SALES",
            "OP_INC",
            "NET_INC",
            "NET_INC_OWNER",
            "NET_INC_NONCONT",
            "TOTAL_LIABILITIES",
            "TOTAL_EQUITY",
            "TOTAL_EQUITY_OWNER"
    );

    private static final List<String> DERIVED_METRIC_CODES = List.of(
            "OPM",
            "NET_MARGIN",
            "DEBT_RATIO",
            "ROE",
            "ROA",
            "QUICK_RATIO"
    );

    private static final List<String> PER_SHARE_METRIC_CODES = List.of(
            "EPS"
    );

    private static final List<String> MARKET_METRIC_CODES = List.of(
            "PER",
            "PBR"
    );

    private final FinPeriodRepository finPeriodRepository;
    private final FinMetricDefRepository finMetricDefRepository;
    private final FinMetricValueRepository finMetricValueRepository;
    private final CompanyRepository companyRepository;
    private final StockCodeRepository stockCodeRepository;
    private final DartFsLineRepository dartFsLineRepository;
    private final StockShareStatusRepository stockShareStatusRepository;
    private final StockPriceRepository stockPriceRepository;
    private final DartFsFilingRepository filingRepository;
    private final XbrlFsRawBundleService xbrlFsRawBundleService;

    private final AnnualXbrlBaseMetricCalculator annualXbrlBaseMetricCalculator;
    private final AnnualXbrlDerivedMetricCalculator annualXbrlDerivedMetricCalculator;
    private final AnnualXbrlPerShareMetricCalculator annualXbrlPerShareMetricCalculator;

    /**
     * stockId로 연간 재무 테이블 조회
     */
    @Transactional
    public FinancialTableDto getAnnualTableByStockId(Long stockId, int limit) {
        log.info("=== getAnnualTableByStockId 호출 ===");
        log.info("stockId: {}, limit: {}", stockId, limit);

        // Company 가져오기 또는 생성
        Company company = getOrCreateCompany(stockId);
        log.info("Company 조회/생성 완료: companyId={}", company.getCompanyId());

        // 재무 데이터 조회 (크롤링 포함)
        return getFinancialTable(company, limit, PeriodType.ANNUAL);
    }

    private FinPeriod saveOrUpdatePeriod(Long companyId, DartReportType reportType, int fiscalYear) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(StockError.COMPANY_NOT_FOUND, "companyId=" + companyId));

        String periodType = reportType.periodType();
        Integer fiscalQuarter = reportType.fiscalQuarter();

        Optional<FinPeriod> existing = finPeriodRepository
                .findByCompany_CompanyIdAndPeriodTypeAndFiscalYearAndFiscalQuarterAndIsEstimate(
                        companyId, periodType, fiscalYear, fiscalQuarter, false);

        if (existing.isPresent()) {
            log.debug("기존 기간 사용: companyId={}, type={}, year={}, quarter={}",
                    companyId, periodType, fiscalYear, fiscalQuarter);
            return existing.get();
        }

        FinPeriod period = FinPeriod.builder()
                .company(company)
                .periodType(periodType)
                .fiscalYear(fiscalYear)
                .fiscalQuarter(fiscalQuarter)
                .periodStart(reportType.periodStart(fiscalYear))
                .periodEnd(reportType.periodEnd(fiscalYear))
                .label(reportType.periodLabel(fiscalYear))
                .isEstimate(false)
                .build();

        FinPeriod saved = finPeriodRepository.save(period);
        log.debug("새 기간 저장: companyId={}, type={}, year={}, quarter={}",
                companyId, periodType, fiscalYear, fiscalQuarter);
        return saved;
    }

    private FinPeriod saveOrUpdateQuarterPeriod(Long companyId, int fiscalYear, int fiscalQuarter) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(StockError.COMPANY_NOT_FOUND, "companyId=" + companyId));

        Optional<FinPeriod> existing = finPeriodRepository
                .findByCompany_CompanyIdAndPeriodTypeAndFiscalYearAndFiscalQuarterAndIsEstimate(
                        companyId, "QTR", fiscalYear, fiscalQuarter, false
                );

        if (existing.isPresent()) {
            log.debug("기존 분기 기간 사용: companyId={}, year={}, quarter={}", companyId, fiscalYear, fiscalQuarter);
            return existing.get();
        }

        LocalDate periodStart;
        LocalDate periodEnd;
        String label;
        switch (fiscalQuarter) {
            case 1 -> {
                periodStart = LocalDate.of(fiscalYear, 1, 1);
                periodEnd = LocalDate.of(fiscalYear, 3, 31);
                label = fiscalYear + "/03";
            }
            case 2 -> {
                periodStart = LocalDate.of(fiscalYear, 4, 1);
                periodEnd = LocalDate.of(fiscalYear, 6, 30);
                label = fiscalYear + "/06";
            }
            case 3 -> {
                periodStart = LocalDate.of(fiscalYear, 7, 1);
                periodEnd = LocalDate.of(fiscalYear, 9, 30);
                label = fiscalYear + "/09";
            }
            case 4 -> {
                periodStart = LocalDate.of(fiscalYear, 10, 1);
                periodEnd = LocalDate.of(fiscalYear, 12, 31);
                label = fiscalYear + "/12";
            }
            default -> throw new CustomException(CommonError.INVALID_INPUT, "지원하지 않는 분기 값입니다. fiscalQuarter=" + fiscalQuarter);
        }

        FinPeriod period = FinPeriod.builder()
                .company(company)
                .periodType("QTR")
                .fiscalYear(fiscalYear)
                .fiscalQuarter(fiscalQuarter)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .label(label)
                .isEstimate(false)
                .build();

        FinPeriod saved = finPeriodRepository.save(period);
        log.debug("새 분기 기간 저장: companyId={}, year={}, quarter={}", companyId, fiscalYear, fiscalQuarter);
        return saved;
    }

    private void saveOrUpdateMetricValue(Long companyId, FinPeriod period, String metricCode, BigDecimal value) {
        log.debug("지표 값 저장 - company={}, period={}, metric={}, value={}",
                companyId, period, metricCode, value);

        Optional<FinMetricValue> existing = finMetricValueRepository
                .findByCompanyIdAndPeriodAndMetricCode(companyId, period, metricCode);

        if (existing.isPresent()) {
            FinMetricValue metricValue = existing.get();
            metricValue.setValueNum(value);
            metricValue.setSource("DART");
            finMetricValueRepository.save(metricValue);
            log.debug("지표 값 업데이트: {} = {}", metricCode, value);
        } else {
            FinMetricValue metricValue = FinMetricValue.builder()
                    .companyId(companyId)
                    .period(period)
                    .metricCode(metricCode)
                    .valueNum(value)
                    .source("DART")
                    .build();
            finMetricValueRepository.save(metricValue);
            log.debug("지표 값 저장: {} = {}", metricCode, value);
        }
    }

    /**
     * market-ticker로 연간 재무 테이블 조회
     */
    @Transactional
    public FinancialTableDto getAnnualTableByTicker(String market, String ticker, int limit) {
        log.info("=== getAnnualTableByTicker 호출 ===");
        log.info("market: {}, ticker: {}, limit: {}", market, ticker, limit);

        StockCode stockCode = stockCodeRepository.findByMarketAndTickerKrx(market, ticker)
                .orElseThrow(() -> new CustomException(
                        StockError.STOCK_NOT_FOUND,
                        String.format("market=%s, ticker=%s", market, ticker)
                ));

        // Company 가져오기 또는 생성
        Company company = getOrCreateCompany(stockCode.getStockId());
        log.info("Company 조회/생성 완료: companyId={}", company.getCompanyId());

        // 재무 데이터 조회 (크롤링 포함)
        return getFinancialTable(company, limit, PeriodType.ANNUAL);
    }

    /**
     * market-ticker로 분기 재무 테이블 조회
     */
    @Transactional
    public FinancialTableDto getQuarterTableByTicker(String market, String ticker, int limit) {
        log.info("=== getQuarterTableByTicker 호출 ===");
        log.info("market: {}, ticker: {}, limit: {}", market, ticker, limit);

        StockCode stockCode = stockCodeRepository.findByMarketAndTickerKrx(market, ticker)
                .orElseThrow(() -> new CustomException(
                        StockError.STOCK_NOT_FOUND,
                        String.format("market=%s, ticker=%s", market, ticker)
                ));

        // Company 가져오기 또는 생성
        Company company = getOrCreateCompany(stockCode.getStockId());
        log.info("Company 조회/생성 완료: companyId={}", company.getCompanyId());

        // 재무 데이터 조회 (크롤링 포함)
        return getFinancialTable(company, limit, PeriodType.QUARTER);
    }

    public Optional<Company> findCompanyByStockId(Long stockId) {
        return companyRepository.findByStockCode_StockId(stockId);
    }

    @Transactional
    public Company getOrCreateCompanyWithStockCode(Long stockId) {
        Company company = getOrCreateCompany(stockId);
        return companyRepository.findWithStockCodeByCompanyId(company.getCompanyId())
                .orElseThrow(() -> new CustomException(StockError.COMPANY_NOT_FOUND, "companyId=" + company.getCompanyId()));
    }

    /**
     * Company 조회 또는 생성
     */
    @Transactional
    public Company getOrCreateCompany(Long stockId) {
        log.info("=== getOrCreateCompany 호출: stockId={} ===", stockId);

        return companyRepository.findByStockCode_StockId(stockId)
                .orElseGet(() -> {
                    StockCode stockCode = stockCodeRepository.findById(stockId)
                            .orElseThrow(() -> new CustomException(StockError.STOCK_NOT_FOUND, "stockId=" + stockId));

                    Company company = Company.builder()
                            .stockCode(stockCode)
                            .currency("KRW")
                            .build();

                    Company saved = companyRepository.save(company);
                    log.info("새 Company 생성: companyId={}, ticker={}", saved.getCompanyId(), stockCode.getTickerKrx());
                    return saved;
                });
    }

    @Transactional
    public Company createCompany(Long stockId) {
        log.info("=== createCompany : stockId = {} ===", stockId);
        StockCode stockCode = stockCodeRepository.findById(stockId)
                .orElseThrow(() -> new CustomException(StockError.STOCK_NOT_FOUND, "stockId="+stockId));

        String corpCode = stockCode.getDartCorpCode();
        if(corpCode == null || corpCode.length() != 8) {
            throw new CustomException(StockError.DART_CORP_CODE_INVALID);
        }

        Company company = Company.builder()
                .stockCode(stockCode)
                .currency("KRW")
                .build();

        Company saved = companyRepository.save(company);
        log.info("새 Company 생성: companyId={}, ticker={}", saved.getCompanyId(), stockCode.getTickerKrx());

        return saved;
    }

    /**
     * 연간, 분기 재무 테이블 조회 - DB 우선, 없으면 크롤링
     */
    @Transactional
    public FinancialTableDto getFinancialTable(Company company, int limit, PeriodType type) {
        log.info("=== getQuarterTable 호출 ===");
        log.info("companyId: {}, limit: {}", company.getCompanyId(), limit);

        Long companyId = company.getCompanyId();
        int currentYear = LocalDate.now().getYear();

        // 1) DB 조회
        List<FinPeriod> periods = switch (type) {
            case ANNUAL -> finPeriodRepository.findRecentYearlyPeriods(companyId, currentYear, limit);
            case QUARTER -> finPeriodRepository.findRecentQuarterlyPeriods(companyId, limit);
        };

        log.info("DB 조회 결과: {} 개 기간", periods.size());

        if (periods.isEmpty()) {
            return FinancialTableDto.builder()
                    .headers(Collections.emptyList())
                    .rows(Collections.emptyList())
                    .build();
        }
        return buildFinancialTable(companyId, periods);
    }

    /**
     * 재무 테이블 구축
     */
    private FinancialTableDto buildFinancialTable(Long companyId, List<FinPeriod> periods) {
        log.info("=== buildFinancialTable 호출 ===");
        log.info("companyId: {}, periods: {}", companyId, periods.size());

        // 1. 헤더 구성 (기간)
        List<FinancialTableDto.PeriodHeaderDto> headers = periods.stream()
                .map(period -> FinancialTableDto.PeriodHeaderDto.builder()
                        .periodId(period.getPeriodId())
                        .label(period.getLabel())
                        .fiscalYear(period.getFiscalYear())
                        .fiscalQuarter(period.getFiscalQuarter())
                        .isEstimate(period.getIsEstimate())
                        .build())
                .collect(Collectors.toList());

        // 2. 지표 정의 조회
        List<FinMetricDef> metricDefs = finMetricDefRepository.findAllByOrderByDisplayOrder();
        log.info("지표 정의 개수: {}", metricDefs.size());

        // 3. 기간 ID 목록
        List<Long> periodIds = periods.stream()
                .map(FinPeriod::getPeriodId)
                .collect(Collectors.toList());

        // 4. 모든 지표 값 조회
        List<FinMetricValue> allValues = finMetricValueRepository.findByCompanyIdAndPeriod_PeriodIdIn(companyId, periodIds);
        log.info("지표 값 개수: {}", allValues.size());

        // 5. periodId + metricCode로 빠른 조회를 위한 맵 생성
        Map<String, BigDecimal> valueMap = allValues.stream()
                .collect(Collectors.toMap(
                        v -> v.getPeriod().getPeriodId() + "_" + v.getMetricCode(),
                        FinMetricValue::getValueNum,
                        (v1, v2) -> v1  // 중복 시 첫 번째 값 사용
                ));

        // 6. 행 구성 (지표별)
        List<FinancialTableDto.MetricRowDto> rows = metricDefs.stream()
                .map(metricDef -> {
                    Map<Long, BigDecimal> rowValues = new HashMap<>();

                    for (FinPeriod period : periods) {
                        String key = period.getPeriodId() + "_" + metricDef.getMetricCode();
                        BigDecimal value = valueMap.get(key);
                        if (value != null) {
                            rowValues.put(period.getPeriodId(), value);
                        }
                    }

                    return FinancialTableDto.MetricRowDto.builder()
                            .metricCode(metricDef.getMetricCode())
                            .metricName(metricDef.getNameKor())
                            .unit(metricDef.getUnit())
                            .displayOrder(metricDef.getDisplayOrder())
                            .values(rowValues)
                            .build();
                })
                .filter(row -> !row.getValues().isEmpty())  // 값이 없는 행은 제외
                .collect(Collectors.toList());

        log.info("테이블 구성 완료: headers={}, rows={}", headers.size(), rows.size());

        return FinancialTableDto.builder()
                .headers(headers)
                .rows(rows)
                .build();
    }

    /**
     * 특정 지표의 최근 값 조회
     */
    public BigDecimal getRecentMetricValue(Long companyId, String metricCode, String periodType, int nth) {
        List<FinPeriod> periods;

        if ("YEAR".equals(periodType)) {
            periods = finPeriodRepository.findRecentYearlyPeriods(companyId,0, nth);
        } else {
            periods = finPeriodRepository.findRecentQuarterlyPeriods(companyId, nth);
        }

        if (periods.isEmpty() || periods.size() < nth) {
            return null;
        }

        Long periodId = periods.get(nth - 1).getPeriodId();

        return finMetricValueRepository
                .findByCompanyIdAndPeriodAndMetricCode(companyId, periods.get(nth-1), metricCode)
                .map(FinMetricValue::getValueNum)
                .orElse(null);
    }

    Map<String, BigDecimal> buildMetrics(Long companyId, int fiscalYear, FsRawBundle rawBundle, MetricStage stage) {
        FsRawBundle safeRawBundle = (rawBundle != null)
                ? rawBundle : new FsRawBundle(new LinkedHashMap<>(), new LinkedHashMap<>());

        return switch (stage) {
            case BASE -> annualXbrlBaseMetricCalculator.calculate(safeRawBundle.curr());
            case DERIVED -> annualXbrlDerivedMetricCalculator.calculate(safeRawBundle.curr(), safeRawBundle.prev(), fiscalYear);
            case PER_SHARE -> annualXbrlPerShareMetricCalculator.calculate(companyId, safeRawBundle.curr(), fiscalYear);
            case MARKET -> throw new CustomException(CommonError.INVALID_INPUT, "MARKET 단계는 시장 데이터 기반으로 별도 계산해야 합니다.");
        };
    }

    public int replaceMetrics(Long companyId, int fiscalYear, MetricStage stage, Map<String, BigDecimal> metrics) {
        return replaceAnnualMetricsByCodes(companyId, fiscalYear, metrics, metricCodesFor(stage));
    }

    @Transactional(readOnly = true)
    public List<FinPeriod> findAnnualPeriodsBetween(Long companyId, int startYear, int endYear) {
        return finPeriodRepository
                .findByCompany_CompanyIdAndPeriodTypeAndFiscalYearBetweenAndIsEstimateOrderByFiscalYearDesc(
                        companyId, "YEAR", startYear, endYear, false
                );
    }

    @Transactional(readOnly = true)
    public List<FinMetricValue> findMetricValuesByPeriodIds(Long companyId, List<Long> periodIds) {
        if (periodIds == null || periodIds.isEmpty()) {
            return List.of();
        }
        return finMetricValueRepository.findByCompanyIdAndPeriod_PeriodIdIn(companyId, periodIds);
    }

    @Transactional(readOnly = true)
    public List<FinMetricDef> findMetricDefinitions() {
        return finMetricDefRepository.findAllByOrderByDisplayOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<FinPeriod> findYearlyPeriods(Long companyId) {
        return finPeriodRepository.findYearlyPeriods(companyId);
    }

    @Transactional(readOnly = true)
    public Optional<FinPeriod> findAnnualPeriod(Long companyId, int fiscalYear) {
        return finPeriodRepository.findByCompany_CompanyIdAndPeriodTypeAndFiscalYearAndIsEstimate(
                companyId, "YEAR", fiscalYear, false
        );
    }

    @Transactional(readOnly = true)
    public Optional<DartFsFiling> findLatestAnnualFiling(Long companyId, int fiscalYear, String fsDiv) {
        return filingRepository.findTopByCompanyIdAndBsnsYearAndReprtCodeAndFsDivOrderByRceptDtDescRceptNoDesc(
                companyId,
                fiscalYear,
                DartReportType.ANNUAL.code(),
                fsDiv
        );
    }

    @Transactional(readOnly = true)
    public Optional<FinPeriod> findQuarterPeriod(Long companyId, int fiscalYear, int fiscalQuarter) {
        return finPeriodRepository.findByCompany_CompanyIdAndPeriodTypeAndFiscalYearAndFiscalQuarterAndIsEstimate(
                companyId, "QTR", fiscalYear, fiscalQuarter, false
        );
    }

    @Transactional(readOnly = true)
    public List<FinPeriod> findRecentActualQuarterlyPeriodsUpTo(Long companyId, int fiscalYear, int fiscalQuarter, int limit) {
        return finPeriodRepository.findRecentActualQuarterlyPeriodsUpTo(companyId, fiscalYear, fiscalQuarter, limit);
    }

    @Transactional(readOnly = true)
    public Optional<BigDecimal> findMetricValue(Long companyId, FinPeriod period, String metricCode) {
        return finMetricValueRepository.findByCompanyIdAndPeriodAndMetricCode(companyId, period, metricCode)
                .map(FinMetricValue::getValueNum);
    }

    @Transactional(readOnly = true)
    public Optional<BigDecimal> findYearEndPrice(Long companyId, int fiscalYear) {
        LocalDate yearEnd = LocalDate.of(fiscalYear, 12, 31);
        return stockPriceRepository
                .findTopByCompany_CompanyIdAndTradeDateLessThanEqualOrderByTradeDateDesc(companyId, yearEnd)
                .map(StockPrice::getPrice);
    }

    @Transactional(readOnly = true)
    public Optional<BigDecimal> findLatestShareCountForPeriod(Long companyId, FinPeriod period) {
        LocalDate baseDate = period.getPeriodEnd();
        if (baseDate == null && period.getFiscalYear() != null) {
            baseDate = LocalDate.of(period.getFiscalYear(), 12, 31);
        }

        return stockShareStatusRepository
                .findTopByCompany_CompanyIdAndSettlementDateLessThanEqualAndShareClassTypeOrderBySettlementDateDesc(
                        companyId, baseDate, ShareClassType.TOTAL
                )
                .map(status -> {
                    Long shares = status.getDistbStockCo();
                    if (shares == null || shares == 0L) {
                        shares = status.getIstcTotqy();
                    }
                    return shares;
                })
                .filter(shares -> shares != null && shares > 0L)
                .map(BigDecimal::valueOf);
    }

    @Transactional(readOnly = true)
    public Optional<BigDecimal> findCompanyShareCount(Long companyId) {
        return companyRepository.findById(companyId)
                .map(Company::getSharesOutstanding)
                .filter(shares -> shares != null && shares > 0L)
                .map(BigDecimal::valueOf);
    }

    public int replaceSingleMetric(Long companyId, int fiscalYear, String metricCode, BigDecimal value) {
        if (metricCode == null || metricCode.isBlank() || value == null) {
            return 0;
        }

        return replaceAnnualMetricsByCodes(companyId, fiscalYear,
                Map.of(metricCode, value), List.of(metricCode));
    }

    public int replaceSingleMetric(Long companyId, FinPeriod period, String metricCode, BigDecimal value) {
        if (period == null || metricCode == null || metricCode.isBlank() || value == null) {
            return 0;
        }

        return replaceMetricsByCodes(companyId, period, Map.of(metricCode, value), List.of(metricCode));
    }

    private List<String> metricCodesFor(MetricStage stage) {
        if (stage == null) {
            return List.of();
        }

        return switch (stage) {
            case BASE -> BASE_METRIC_CODES;
            case DERIVED -> DERIVED_METRIC_CODES;
            case PER_SHARE -> PER_SHARE_METRIC_CODES;
            case MARKET -> MARKET_METRIC_CODES;
        };
    }

    FsRawBundle loadRawBundle(Long companyId, int fiscalYear) {
        FinPeriod period = findAnnualPeriod(companyId, fiscalYear).orElse(null);
        if (period == null) {
            log.warn("연간 FinPeriod가 없습니다. companyId={}, year={}", companyId, fiscalYear);
            return new FsRawBundle(new LinkedHashMap<>(), new LinkedHashMap<>());
        }

        return loadRawBundle(companyId, period);
    }

    FsRawBundle loadRawBundle(Long companyId, FinPeriod period) {
        DartReportType reportType = resolveReportType(period);
        List<DartFsLine> lines = dartFsLineRepository.findByFiling_CompanyIdAndFiling_BsnsYearAndFiling_ReprtCode(
                companyId, period.getFiscalYear(), reportType.code());
        if (lines.isEmpty()) {
            log.warn("재무제표 라인 데이터가 없습니다. companyId={}, year={}, reprtCode={}",
                    companyId, period.getFiscalYear(), reportType.code());
            return new FsRawBundle(new LinkedHashMap<>(), new LinkedHashMap<>());
        }
        log.debug("==== companyId={}, year={}, reprtCode={} 조회된 재무제표 라인 수 : {}",
                companyId, period.getFiscalYear(), reportType.code(), lines.size());
        return collectRawBundle(lines);
    }

    FsRawBundle loadXbrlRawBundle(Long companyId, int fiscalYear, String fsDiv) {
        FsRawBundle rawBundle = xbrlFsRawBundleService.buildRawBundle(
                companyId,
                fiscalYear,
                DartReportType.ANNUAL,
                fsDiv
        );

        if (rawBundle.curr().isEmpty()) {
            log.warn("XBRL 원천 데이터가 없습니다. companyId={}, year={}, reprtCode={}, fsDiv={}",
                    companyId, fiscalYear, DartReportType.ANNUAL.code(), fsDiv);
        }

        return rawBundle;
    }

    FsRawBundle loadXbrlRawBundle(Long companyId, FinPeriod period, String fsDiv) {
        DartReportType reportType = resolveReportType(period);
        FsRawBundle rawBundle = xbrlFsRawBundleService.buildRawBundle(
                companyId,
                period.getFiscalYear(),
                reportType,
                fsDiv
        );

        if (rawBundle.curr().isEmpty()) {
            log.warn("XBRL 원천 데이터가 없습니다. companyId={}, year={}, reprtCode={}, fsDiv={}",
                    companyId, period.getFiscalYear(), reportType.code(), fsDiv);
        }

        return rawBundle;
    }

    FsRawBundle loadQuarterRawBundle(Long companyId, int fiscalYear, int fiscalQuarter) {
        FinPeriod period = findQuarterPeriod(companyId, fiscalYear, fiscalQuarter).orElse(null);
        if (period == null) {
            log.warn("분기 FinPeriod가 없습니다. companyId={}, year={}, quarter={}", companyId, fiscalYear, fiscalQuarter);
            return new FsRawBundle(new LinkedHashMap<>(), new LinkedHashMap<>());
        }

        FsRawBundle current = loadRawBundle(companyId, period);
        if (fiscalQuarter == 1) {
            return current;
        }

        FinPeriod previousQuarterPeriod = findQuarterPeriod(companyId, fiscalYear, fiscalQuarter - 1).orElse(null);
        if (previousQuarterPeriod == null) {
            log.warn("직전 분기 FinPeriod가 없습니다. companyId={}, year={}, quarter={}",
                    companyId, fiscalYear, fiscalQuarter - 1);
            return current;
        }

        FsRawBundle previous = loadRawBundle(companyId, previousQuarterPeriod);
        if (fiscalQuarter == 4) {
            return adjustFourthQuarterBundle(
                    current,
                    loadQuarterRawBundle(companyId, fiscalYear, 1),
                    loadQuarterRawBundle(companyId, fiscalYear, 2),
                    loadQuarterRawBundle(companyId, fiscalYear, 3),
                    previous
            );
        }
        return adjustQuarterlyAttributionBundle(current, previous);
    }

    private FsRawBundle adjustQuarterlyAttributionBundle(FsRawBundle currentBundle, FsRawBundle previousBundle) {
        Map<String, BigDecimal> curr = new LinkedHashMap<>(currentBundle.curr());
        Map<String, BigDecimal> previousCurr = previousBundle.curr();

        for (String metricCode : ATTRIBUTABLE_NET_INCOME_CODES) {
            BigDecimal currentValue = curr.get(metricCode);
            BigDecimal previousValue = previousCurr.get(metricCode);
            if (currentValue == null || previousValue == null) {
                continue;
            }
            curr.put(metricCode, currentValue.subtract(previousValue));
        }

        return new FsRawBundle(curr, previousCurr);
    }

    private FsRawBundle adjustFourthQuarterBundle(FsRawBundle annualBundle,
                                                  FsRawBundle firstQuarterBundle,
                                                  FsRawBundle secondQuarterBundle,
                                                  FsRawBundle thirdQuarterBundle,
                                                  FsRawBundle previousBundle) {
        Map<String, BigDecimal> curr = new LinkedHashMap<>();
        Map<String, BigDecimal> annualCurr = annualBundle.curr();
        Map<String, BigDecimal> firstQuarterCurr = firstQuarterBundle.curr();
        Map<String, BigDecimal> secondQuarterCurr = secondQuarterBundle.curr();
        Map<String, BigDecimal> thirdQuarterCurr = thirdQuarterBundle.curr();

        for (Map.Entry<String, BigDecimal> entry : annualCurr.entrySet()) {
            String metricCode = entry.getKey();
            BigDecimal annualValue = entry.getValue();
            if (annualValue == null) {
                continue;
            }

            if (FLOW_METRIC_CODES.contains(metricCode)) {
                BigDecimal firstQuarterValue = firstQuarterCurr.get(metricCode);
                BigDecimal secondQuarterValue = secondQuarterCurr.get(metricCode);
                BigDecimal thirdQuarterValue = thirdQuarterCurr.get(metricCode);
                BigDecimal priorQuarterSum = Stream.of(firstQuarterValue, secondQuarterValue, thirdQuarterValue)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                curr.put(metricCode, annualValue.subtract(priorQuarterSum));
            } else {
                curr.put(metricCode, annualValue);
            }
        }

        return new FsRawBundle(curr, previousBundle.curr());
    }

    private DartReportType resolveReportType(FinPeriod period) {
        if (period == null || period.getFiscalYear() == null) {
            throw new CustomException(CommonError.INVALID_INPUT, "period 정보가 올바르지 않습니다.");
        }

        String periodType = period.getPeriodType();
        Integer fiscalQuarter = period.getFiscalQuarter();

        if ("YEAR".equals(periodType)) {
            return DartReportType.ANNUAL;
        }

        if (!"QTR".equals(periodType) || fiscalQuarter == null) {
            throw new CustomException(CommonError.INVALID_INPUT,
                    "지원하지 않는 기간 유형입니다. periodType=" + periodType + ", fiscalQuarter=" + fiscalQuarter);
        }

        return switch (fiscalQuarter) {
            case 1 -> DartReportType.FIRST_QUARTER;
            case 2 -> DartReportType.HALF_YEAR;
            case 3 -> DartReportType.THIRD_QUARTER;
            case 4 -> DartReportType.ANNUAL;
            default -> throw new CustomException(
                    CommonError.INVALID_INPUT, "지원하지 않는 분기 값입니다. fiscalQuarter=" + fiscalQuarter
            );
        };
    }

    private FsRawBundle collectRawBundle(List<DartFsLine> lines) {

        Map<String, BigDecimal> curr = new LinkedHashMap<>();
        Map<String, BigDecimal> prev = new LinkedHashMap<>();
        Map<String, DartFsLine> currSources = new HashMap<>();
        Map<String, DartFsLine> prevSources = new HashMap<>();


        for(DartFsLine line : lines) {
            String sjDiv = line.getSjDiv();                 // 재무제표 구분
            String accountId = line.getAccountId();         // 계정Id
            String accountNm = line.getAccountNm();         // 계정설명
            String accountDetail = line.getAccountDetail(); // 구성요소 [member] 등

            String metricCode = mapAccountToMetric(sjDiv, accountId, accountNm, accountDetail);

            if (metricCode == null) {
                continue;
            }

            BigDecimal currVal = line.getThstrmAmount();
            BigDecimal prevVal = line.getFrmtrmAmount();    // 전기금액

            // 당기
            if (currVal != null) {
                BigDecimal old = curr.get(metricCode);
                if (old == null) {
                    curr.put(metricCode, currVal);
                    currSources.put(metricCode, line);
                } else if (shouldReplaceRawMetric(metricCode, currSources.get(metricCode), line)) {
                    curr.put(metricCode, currVal);
                    currSources.put(metricCode, line);
                } else if (old.compareTo(currVal) != 0) {
                    log.debug("[FS-DB][DUP-CURR] metric={} old={} new={} (accountId={}, accountNm={})",
                            metricCode, old, currVal, accountId, accountNm);
                }
            }

            // 전기
            if (prevVal != null) {
                BigDecimal old = prev.get(metricCode);
                if (old == null) {
                    prev.put(metricCode, prevVal);
                    prevSources.put(metricCode, line);
                } else if (shouldReplaceRawMetric(metricCode, prevSources.get(metricCode), line)) {
                    prev.put(metricCode, prevVal);
                    prevSources.put(metricCode, line);
                } else if (old.compareTo(prevVal) != 0) {
                    log.debug("[FS-DB][DUP-PREV] metric={} old={} new={} (accountId={}, accountNm={})",
                            metricCode, old, prevVal, accountId, accountNm);
                }
            }
        }

        applyNetIncomeFallbacks(curr);
        applyNetIncomeFallbacks(prev);
        applyOwnerEquityFallbacks(curr);
        applyOwnerEquityFallbacks(prev);

        return new FsRawBundle(curr, prev);
    }

    private boolean shouldReplaceRawMetric(String metricCode, DartFsLine currentLine, DartFsLine candidateLine) {
        if (currentLine == null || candidateLine == null) {
            return false;
        }

        return rawMetricPriority(metricCode, candidateLine) > rawMetricPriority(metricCode, currentLine);
    }

    private int rawMetricPriority(String metricCode, DartFsLine line) {
        return switch (metricCode) {
            case "NET_INC" -> netIncomeCandidatePriority(line);
            case "NET_INC_OWNER" -> ownerNetIncomeCandidatePriority(line);
            case "NET_INC_NONCONT" -> noncontNetIncomeCandidatePriority(line);
            default -> 0;
        };
    }

    private int netIncomeCandidatePriority(DartFsLine line) {
        String accountId = safeLower(line.getAccountId());
        String accountNm = safeLower(line.getAccountNm());
        String sjDiv = safeLower(line.getSjDiv());

        int priority = 0;

        if ("ifrs-full_profitloss".equals(accountId) || "ifrs_profitloss".equals(accountId)) {
            priority += 100;
        } else if (accountId.contains("미사용")) {
            priority += 10;
        }

        if ("cis".equals(sjDiv) || "is".equals(sjDiv)) {
            priority += 10;
        }

        if (accountNm.contains("계속영업")) {
            priority -= 50;
        }

        if (accountNm.contains("당기순이익") || accountNm.contains("당기순손실") || accountNm.contains("당기순손익")) {
            priority += 5;
        }

        return priority;
    }

    private int ownerNetIncomeCandidatePriority(DartFsLine line) {
        String accountId = safeLower(line.getAccountId());
        String accountNm = safeLower(line.getAccountNm());
        String accountDetail = safeLower(line.getAccountDetail());

        int priority = 0;

        if ("ifrs-full_profitloss".equals(accountId) || "ifrs_profitloss".equals(accountId)) {
            priority += 100;
        } else if (accountId.contains("미사용")) {
            priority += 10;
        }

        if (accountNm.contains("당기순이익") || accountNm.contains("당기순손실") || accountNm.contains("당기순손익")) {
            priority += 5;
        }

        if (containsAny(accountDetail,
                "지배기업의 소유주 귀속분",
                "지배기업의 소유주에게 귀속되는",
                "지배기업 소유주지분",
                "지배기업 소유주 귀속분")) {
            priority += 100;
        }

        if (isTopLevelDetail(accountDetail,
                "지배기업의 소유주 귀속분",
                "지배기업의 소유주에게 귀속되는",
                "지배기업 소유주지분",
                "지배기업 소유주 귀속분")) {
            priority += 200;
        }

        if (accountDetail.contains("연결재무제표") || accountDetail.contains("비지배")) {
            priority -= 100;
        }

        if (accountDetail.contains("자본금")
                || accountDetail.contains("이익잉여금")
                || accountDetail.contains("기타포괄손익누계액")
                || accountDetail.contains("기타자본")
                || accountDetail.contains("자본잉여금")) {
            priority -= 150;
        }

        return priority;
    }

    private int noncontNetIncomeCandidatePriority(DartFsLine line) {
        String accountId = safeLower(line.getAccountId());
        String accountNm = safeLower(line.getAccountNm());
        String accountDetail = safeLower(line.getAccountDetail());

        int priority = 0;

        if ("ifrs-full_profitloss".equals(accountId) || "ifrs_profitloss".equals(accountId)) {
            priority += 100;
        } else if (accountId.contains("미사용")) {
            priority += 10;
        }

        if (accountNm.contains("당기순이익") || accountNm.contains("당기순손실") || accountNm.contains("당기순손익")) {
            priority += 5;
        }

        if (accountDetail.contains("비지배")) {
            priority += 100;
        }

        if (isTopLevelDetail(accountDetail, "비지배지분")) {
            priority += 200;
        }

        if (accountDetail.contains("연결재무제표") || accountDetail.contains("지배기업")) {
            priority -= 100;
        }

        return priority;
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String value, String... markers) {
        for (String marker : markers) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTopLevelDetail(String detail, String... markers) {
        for (String marker : markers) {
            int markerIndex = detail.indexOf(marker);
            if (markerIndex < 0) {
                continue;
            }

            int nextPipeIndex = detail.indexOf('|', markerIndex + marker.length());
            if (nextPipeIndex < 0) {
                return true;
            }
        }
        return false;
    }

    private void applyNetIncomeFallbacks(Map<String, BigDecimal> metrics) {
        if (metrics == null || metrics.containsKey("NET_INC_OWNER")) {
            return;
        }

        BigDecimal netIncome = metrics.get("NET_INC");
        BigDecimal noncontNetIncome = metrics.get("NET_INC_NONCONT");
        if (netIncome == null || noncontNetIncome == null) {
            return;
        }

        metrics.put("NET_INC_OWNER", netIncome.subtract(noncontNetIncome));
    }

    private void applyOwnerEquityFallbacks(Map<String, BigDecimal> metrics) {
        if (metrics == null || metrics.containsKey("TOTAL_EQUITY_OWNER")) {
            return;
        }

        BigDecimal totalEquity = metrics.get("TOTAL_EQUITY");
        BigDecimal noncontrollingInterests = metrics.get("TOTAL_EQUITY_NONCONT");
        if (totalEquity == null || noncontrollingInterests == null) {
            return;
        }

        metrics.put("TOTAL_EQUITY_OWNER", totalEquity.subtract(noncontrollingInterests));
    }

    private String mapAccountToMetric(String sjDiv, String accountId, String accountNm, String accountDetail) {
        if(isBlank(accountId) && isBlank(accountNm)) return null;

        FsKey key = FsKey.of(trimOrEmptyStr(sjDiv), trimOrEmptyStr(accountId),
                trimOrEmptyStr(accountNm), trimOrEmptyStr(accountDetail));

        // SCE 자본변동표
        String m = mapSce(key);
        if(m != null) return m;

        // IS/CIS 손익게산서
        m = mapIncomeStatement(key);
        if(m != null) return m;

        // BS/BIS 재무상태표
        m = mapBlanceSheet(key);
        if(m != null) return m;


        return null;
    }

    private String mapBlanceSheet(FsKey key) {
        if(!"BS".equalsIgnoreCase(key.sj) && !"BIS".equalsIgnoreCase(key.sj)) return null;

        // "부채와자본총계"는 자본으로 보지 않고 무시
        // (자산총계와 같은 값)
        if (key.id.equals("ifrs-full_EquityAndLiabilities")
                || key.nm.contains("부채와자본총계")) {
            return null;
        }


        // 자산총계
        if (key.id.equals("ifrs-full_Assets")
                || key.nm.contains("자산총계")) {
            return "TOTAL_ASSETS";
        }

        // 부채총계
        if (key.id.equals("ifrs-full_Liabilities")
                || key.nm.contains("부채총계")) {
            return "TOTAL_LIABILITIES";
        }

        // 자본총계 (지배 + 비지배 포함)
        if (key.id.equals("ifrs-full_Equity") || key.id.equals("ifrs_Equity")) {
            return "TOTAL_EQUITY";
        }
        if ((key.id.isEmpty() || "-".equals(key.id)) && key.nm.equals("자본총계")) {
            return "TOTAL_EQUITY";
        }



        // 지배주주지분 / 지배기업 소유주 지분
        boolean isStandardOwnerEquityId = key.id.equals("ifrs-full_EquityAttributableToOwnersOfParent")
                || key.id.equals("ifrs_EquityAttributableToOwnersOfParent");
        boolean isOwnerEquityName = key.nm.contains("지배기업의 소유주에게 귀속되는 자본")
                || key.nm.contains("지배회사소유주지분")
                || key.nm.contains("지배주주지분");
        boolean isCustomOwnerEquityMatch = key.id.equals("dart_ContributedEquity") && isOwnerEquityName;

        if (isStandardOwnerEquityId || isCustomOwnerEquityMatch || isOwnerEquityName) {
            return "TOTAL_EQUITY_OWNER";
        }

        if (key.id.equals("ifrs-full_NoncontrollingInterests")
                || key.id.equals("ifrs_NoncontrollingInterests")
                || key.nm.contains("비지배지분")) {
            return "TOTAL_EQUITY_NONCONT";
        }

        // 유동자산
        if (key.id.equals("ifrs-full_CurrentAssets")
                || key.nm.contains("유동자산")) {
            return "CURRENT_ASSETS";
        }

        // 유동부채
        if (key.id.equals("ifrs-full_CurrentLiabilities")
                || key.nm.contains("유동부채")) {
            return "CURRENT_LIABILITIES";
        }

        // BPS (주당순자산) - BS나 기타 주당지표에서 나올 수 있음
        if (key.id.contains("EquityPerShare") || key.nm.contains("주당순자산")) {
            return "BPS";
        }

        return null;
    }

    private String mapIncomeStatement(FsKey key) {
        if(!"IS".equalsIgnoreCase(key.sj) && !"CIS".equalsIgnoreCase(key.sj)) return null;

        // 전체 당기순이익
        if (isProfitLossAccountId(key.id) && isNetIncomeLabel(key.nm)) {
            // 전체 당기순이익
            return "NET_INC";
        }

        // 매출액 / 영업수익
        if (key.id.equals("ifrs-full_Revenue")
                || key.id.equals("ifrs_Revenue")
                || key.nm.contains("매출액")
                || key.nm.contains("영업수익")) {
            return "SALES";
        }

        // 영업이익
        if (key.id.equals("ifrs-full_ProfitLossFromOperatingActivities")
                || key.id.equals("ifrs_ProfitLossFromOperatingActivities")
                || key.nm.contains("영업이익")) {
            return "OP_INC";
        }


        // EPS (기본주당순이익 등)
        if (key.id.contains("EarningsPerShare") || key.nm.contains("주당순이익")) {
            return "EPS";
        }
        return null;
    }

    private String mapSce(FsKey key) {
        if(!"SCE".equalsIgnoreCase(key.sj)) return null;

        // 지배주주 귀속 당기순이익
        if (isProfitLossAccountId(key.id)
                && isNetIncomeLabel(key.nm)
                && key.detail.contains("지배기업")
                && !key.detail.contains("기타자본") && !key.detail.contains("이익잉여") && !key.detail.contains("자본금") && !key.detail.contains("주식발행")) {

            return "NET_INC_OWNER";
        }

        // 비지배주주 귀속 당기순이익
        if (isProfitLossAccountId(key.id)
                && isNetIncomeLabel(key.nm)
                && key.detail.contains("비지배")) {
            return "NET_INC_NONCONT";
        }

        // 2018년 이전에는 ifrs_ProfitLoss id를 사용하였음. 이후 참고 데이터

        // 그 외 SCE 항목은 무시
        return null;

    }

    private boolean isProfitLossAccountId(String accountId) {
        return accountId.equals("ifrs-full_ProfitLoss")
                || accountId.equals("ifrs_ProfitLoss")
                || accountId.contains("미사용");
    }

    private boolean isNetIncomeLabel(String accountName) {
        boolean hasPeriodWord = accountName.contains("당기")
                || accountName.contains("분기")
                || accountName.contains("반기");
        boolean hasNetIncomeWord = accountName.contains("순이익")
                || accountName.contains("순손익")
                || accountName.contains("손실");

        return hasPeriodWord && hasNetIncomeWord;
    }

    private record FsKey(String sj, String id, String nm, String detail){

        public static FsKey of(String sjDiv, String accountId, String accountNm, String accountDetail){
            return new FsKey(sjDiv, accountId, accountNm, accountDetail);
        }
    }

    private static String trimOrEmptyStr(String s){
        return s == null ? "" : s.trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private void putIfNotNull(Map<String, BigDecimal> map, String key, BigDecimal value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    @Transactional
    public int replaceAnnualMetricsByCodes(Long companyId, int fiscalYear,
                                           Map<String, BigDecimal> metrics, List<String> stageMetricCodes) {
        return replaceAnnualMetricsByCodes(companyId, fiscalYear, metrics, stageMetricCodes, "DART");
    }

    @Transactional
    int replaceAnnualMetricsByCodes(Long companyId, int fiscalYear,
                                    Map<String, BigDecimal> metrics,
                                    List<String> stageMetricCodes,
                                    String source) {
        if (stageMetricCodes == null || stageMetricCodes.isEmpty()) {
            return 0;
        }

        List<String> targetMetricCodes = stageMetricCodes.stream().distinct().toList();

        FinPeriod period = finPeriodRepository
                .findByCompany_CompanyIdAndPeriodTypeAndFiscalYearAndIsEstimate(
                        companyId, "YEAR", fiscalYear, false
                )
                .orElseGet(() -> {
                    Company companyRef = companyRepository.getReferenceById(companyId);

                    FinPeriod p = new FinPeriod();
                    p.setCompany(companyRef);
                    p.setPeriodType("YEAR");
                    p.setFiscalYear(fiscalYear);
                    p.setFiscalQuarter(null);
                    p.setIsEstimate(false);
                    p.setLabel(fiscalYear + "/12");
                    p.setPeriodStart(null);
                    p.setPeriodEnd(LocalDate.of(fiscalYear, 12, 31));
                    return finPeriodRepository.save(p);
                });

        long deleted = finMetricValueRepository.deleteByCompanyIdAndPeriod_PeriodIdAndMetricCodeIn(
                companyId, period.getPeriodId(), targetMetricCodes
        );
        finMetricValueRepository.flush();
        log.info("[FIN-METRIC][ANNUAL] replace start companyId={}, year={}, periodId={}, deleteCount={}, targetMetricCodes={}",
                companyId, fiscalYear, period.getPeriodId(), deleted, targetMetricCodes);

        if (metrics == null || metrics.isEmpty()) {
            return 0;
        }

        List<FinMetricValue> entities = buildMetricEntities(companyId, period, metrics, targetMetricCodes, source);
        saveMetricEntitiesWithSourceFallback(
                entities,
                companyId,
                fiscalYear,
                period.getPeriodId(),
                "ANNUAL",
                source
        );
        return entities.size();
    }

    @Transactional
    public int replaceMetrics(Long companyId, FinPeriod period, MetricStage stage, Map<String, BigDecimal> metrics) {
        return replaceMetricsByCodes(companyId, period, metrics, metricCodesFor(stage));
    }

    @Transactional
    public int replaceMetricsByCodes(Long companyId, FinPeriod period,
                                     Map<String, BigDecimal> metrics, List<String> stageMetricCodes) {
        if (period == null || period.getPeriodId() == null || stageMetricCodes == null || stageMetricCodes.isEmpty()) {
            return 0;
        }

        List<String> targetMetricCodes = stageMetricCodes.stream().distinct().toList();

        long deleted = finMetricValueRepository.deleteByCompanyIdAndPeriod_PeriodIdAndMetricCodeIn(
                companyId, period.getPeriodId(), targetMetricCodes
        );
        finMetricValueRepository.flush();
        log.info("[FIN-METRIC][QTR] replace start companyId={}, periodId={}, period={}/{}, deleteCount={}, targetMetricCodes={}",
                companyId,
                period.getPeriodId(),
                period.getFiscalYear(),
                period.getFiscalQuarter(),
                deleted,
                targetMetricCodes);

        if (metrics == null || metrics.isEmpty()) {
            return 0;
        }

        List<FinMetricValue> entities = buildMetricEntities(companyId, period, metrics, targetMetricCodes, "DART");
        saveMetricEntitiesWithSourceFallback(
                entities,
                companyId,
                period.getFiscalYear(),
                period.getPeriodId(),
                "QTR",
                "DART"
        );
        return entities.size();
    }

    private List<FinMetricValue> buildMetricEntities(Long companyId,
                                                     FinPeriod period,
                                                     Map<String, BigDecimal> metrics,
                                                     List<String> targetMetricCodes,
                                                     String source) {
        return metrics.entrySet().stream()
                .filter(entry -> targetMetricCodes.contains(entry.getKey()))
                .filter(entry -> entry.getValue() != null)
                .map(entry -> FinMetricValue.builder()
                        .companyId(companyId)
                        .period(period)
                        .metricCode(entry.getKey())
                        .valueNum(entry.getValue())
                        .source(source)
                        .build())
                .toList();
    }

    private void saveMetricEntitiesWithSourceFallback(List<FinMetricValue> entities,
                                                      Long companyId,
                                                      Integer fiscalYear,
                                                      Long periodId,
                                                      String scope,
                                                      String source) {
        try {
            finMetricValueRepository.saveAll(entities);
        } catch (DataIntegrityViolationException ex) {
            if (shouldRetryWithDartSource(source, ex)) {
                log.warn("[FIN-METRIC][{}] source 제약 fallback 적용 companyId={}, fiscalYear={}, periodId={}, source={} -> DART",
                        scope, companyId, fiscalYear, periodId, source);
                List<FinMetricValue> fallbackEntities = entities.stream()
                        .map(entity -> FinMetricValue.builder()
                                .companyId(entity.getCompanyId())
                                .period(entity.getPeriod())
                                .metricCode(entity.getMetricCode())
                                .valueNum(entity.getValueNum())
                                .source("DART")
                                .build())
                        .toList();
                finMetricValueRepository.saveAll(fallbackEntities);
                return;
            }

            log.error("[FIN-METRIC][{}] save failure companyId={}, fiscalYear={}, periodId={}, entityMetricCodes={}",
                    scope,
                    companyId,
                    fiscalYear,
                    periodId,
                    entities.stream().map(FinMetricValue::getMetricCode).toList(),
                    ex);
            throw ex;
        }
    }

    private boolean shouldRetryWithDartSource(String source, DataIntegrityViolationException ex) {
        if ("DART".equals(source)) {
            return false;
        }

        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("CK_FMV_SOURCE")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public void updateCompanyShareInfo(Long companyId) {

        StockShareStatus latest = stockShareStatusRepository
                .findTopByCompany_CompanyIdOrderByUpdatedAtDesc(companyId)
                .orElse(null);

        if (latest == null) {
            log.info("최신 주식수 스냅샷이 없습니다. companyId={}", companyId);
            return;
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(StockError.COMPANY_NOT_FOUND, "companyId=" + companyId));

        company.setSharesOutstanding( latest.getDistbStockCo());

        log.info("Company 주식수 정보 갱신 완료 - companyId={}, shares={}",
                companyId, company.getSharesOutstanding());
    }

    @Transactional
    public void replaceFinancialStatements(String corpCode, Long companyId, List<DartFsRow> rows) {

        if(rows.isEmpty()) return;

        DartFsRow meta = rows.get(0);
        DartReportType reportType = DartReportType.fromCode(meta.getReprtCode());

        // FsFiling 생성 및 저장
        DartFsFiling filing = getOrCreateFiling(corpCode, companyId, meta);
        saveOrUpdatePeriod(companyId, reportType, meta.getBsnsYear());
        if (reportType == DartReportType.ANNUAL) {
            saveOrUpdateQuarterPeriod(companyId, meta.getBsnsYear(), 4);
        }

        // FsLine 생성 및 저장
        replaceFsLines(filing, companyId, rows);
    }

    private void replaceFsLines(DartFsFiling filing, Long companyId, List<DartFsRow> rows) {

        dartFsLineRepository.deleteByFiling_FsFilingId(filing.getFsFilingId());


        // Line 엔티티로 변환 + 저장
        List<DartFsLine> entities = rows.stream()
                .map(row -> DartFsLine.fromRow(filing, companyId, row))
                .toList();

        dartFsLineRepository.saveAll(entities);
    }

    private DartFsFiling getOrCreateFiling(String corpCode, Long companyId, DartFsRow firstRow) {
        String rceptNo = firstRow.getRceptNo();
        String reprtCode = firstRow.getReprtCode();
        String fsDiv = firstRow.getFsDiv();
        DartReportType reportType = DartReportType.fromCode(reprtCode);

        // to-do dart접수번호기준 조회 고려할것 , 현재 접수번호, 보고서번호, 재무제표종류
        Optional<DartFsFiling> existingOpt = filingRepository.findByRceptNoAndReprtCodeAndFsDiv(rceptNo, reprtCode, fsDiv);

        if(existingOpt.isPresent()) {
            DartFsFiling existing = existingOpt.get();
            return existing;
        }

        LocalDate rceptDt = null;
        String rceptDtStr = firstRow.getRceptDt(); // "20230320" 같은 형식이라고 가정
        if (rceptDtStr != null && rceptDtStr.length() == 8) {
            int yyyy = Integer.parseInt(rceptDtStr.substring(0, 4));
            int mm   = Integer.parseInt(rceptDtStr.substring(4, 6));
            int dd   = Integer.parseInt(rceptDtStr.substring(6, 8));
            rceptDt = LocalDate.of(yyyy, mm, dd);
        }

        DartFsFiling filing = DartFsFiling.builder()
                .corpCode(corpCode)
                .companyId(companyId)
                .rceptNo(rceptNo)
                .reprtCode(reprtCode)
                .bsnsYear(firstRow.getBsnsYear())
                .fsDiv(fsDiv)
                .reportTp(reportType.label())
                .currency(firstRow.getCurrency())
                .rceptDt(rceptDt)
                .build();

        return filingRepository.save(filing);
    }

}
