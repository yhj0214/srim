package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.client.dto.DartFsRow;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.StockErrorCode;
import org.yhj.srim.repository.*;
import org.yhj.srim.repository.entity.*;
import org.yhj.srim.service.dto.FinancialTableDto;
import org.yhj.srim.service.dto.FsRawBundle;
import org.yhj.srim.service.dto.PeriodType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FinancialService {

    private final FinPeriodRepository finPeriodRepository;
    private final FinMetricDefRepository finMetricDefRepository;
    private final FinMetricValueRepository finMetricValueRepository;
    private final CompanyRepository companyRepository;
    private final StockCodeRepository stockCodeRepository;
    private final DartFsLineRepository dartFsLineRepository;
    private final StockShareStatusRepository stockShareStatusRepository;
    private final DartFsFilingRepository filingRepository;

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

    /**
     * stockId로 분기 재무 테이블 조회
     */
//    @Transactional
//    public FinancialTableDto getQuarterTableByStockId(Long stockId, int limit) {
//        log.info("=== getQuarterTableByStockId 호출 ===");
//        log.info("stockId: {}, limit: {}", stockId, limit);
//
//        // Company 가져오기 또는 생성
//        Company company = getOrCreateCompany(stockId);
//        log.info("Company 조회/생성 완료: companyId={}", company.getCompanyId());
//
//        // 재무 데이터 조회 (크롤링 포함)
//        return getFinancialTable(company, limit, PeriodType.QUARTER);
//    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recalcAndSaveFinancialForYearFromDb(Company company, int year){

        Long companyId = company.getCompanyId();

        log.info("{}년 재무지표 재계산 및 저장 companyId={}", year, companyId);

        Map<String, BigDecimal> financialData = buildFinancialMetrics(companyId, year);

        if (financialData.isEmpty()) {
            log.warn("[FS-DB] {}년 재무 데이터 없음 (companyId={})", year, companyId);
            return 0;
        }

        //   - 연간정보는 월에 12, isEstimate=false
        FinPeriod period = saveOrUpdatePeriod(companyId, year, 12, false);



        int inserted = replaceMetricValues(companyId, period, financialData, "DART");

//        int yearSaved = 0;
//        for (Map.Entry<String, BigDecimal> entry : financialData.entrySet()) {
//            String metricCode = entry.getKey();
//            BigDecimal value  = entry.getValue();
//
//            saveOrUpdateMetricValue(companyId, period, metricCode, value);
//            yearSaved++;
//        }

        log.info("[FS-DB] {}년 재무 데이터 저장 완료 - {}건 (companyId={})",
                year, inserted, companyId);

        return inserted;
    }

    private int replaceMetricValues(Long companyId, FinPeriod period, Map<String, BigDecimal> metrics, String dart) {
        if(metrics == null || metrics.isEmpty()) return 0;

        long deleted = finMetricValueRepository.deleteByCompanyIdAndPeriod_PeriodId(companyId, period.getPeriodId());

        List<FinMetricValue> entities = metrics.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .filter(e -> e.getKey() != null && !e.getKey().isBlank())
                .map(entry -> FinMetricValue.builder()
                        .companyId(companyId)
                        .period(period)
                        .metricCode(entry.getKey())
                        .valueNum(entry.getValue())
                        .source(dart)
                        .build())
                .collect(Collectors.toList());

        finMetricValueRepository.saveAll(entities);

        return entities.size();
    }

    private FinPeriod saveOrUpdatePeriod(Long companyId, int fiscalYear, int fiscalMonth, boolean isQuarter) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyId));

        String periodType = "YEAR"; // 사업보고서는 연간 데이터
        Integer fiscalQuarter = null;

        Optional<FinPeriod> existing = finPeriodRepository
                .findByCompany_CompanyIdAndPeriodTypeAndFiscalYearAndFiscalQuarter(
                        companyId, periodType, fiscalYear, fiscalQuarter);

        if (existing.isPresent()) {
            log.debug("기존 기간 사용: {}년", fiscalYear);
            return existing.get();
        }

        FinPeriod period = FinPeriod.builder()
                .company(company)
                .periodType(periodType)
                .fiscalYear(fiscalYear)
                .fiscalQuarter(fiscalQuarter)
                .periodStart(LocalDate.of(fiscalYear, 1, 1))
                .periodEnd(LocalDate.of(fiscalYear, 12, 31))
                .label(fiscalYear + ".12") // YYYY.12 형식으로 통일
                .isEstimate(false)
                .build();

        FinPeriod saved = finPeriodRepository.save(period);
        log.debug("새 기간 저장: {}년", fiscalYear);
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
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("종목을 찾을 수 없습니다. (market=%s, ticker=%s)", market, ticker)));

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
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("종목을 찾을 수 없습니다. (market=%s, ticker=%s)", market, ticker)));

        // Company 가져오기 또는 생성
        Company company = getOrCreateCompany(stockCode.getStockId());
        log.info("Company 조회/생성 완료: companyId={}", company.getCompanyId());

        // 재무 데이터 조회 (크롤링 포함)
        return getFinancialTable(company, limit, PeriodType.QUARTER);
    }

    public Optional<Company> findCompanyByStockId(Long stockId) {
        return companyRepository.findByStockCode_StockId(stockId);
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
                            .orElseThrow(() -> new CustomException(StockErrorCode.STOCK_NOT_FOUND));

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
                .orElseThrow(() -> new CustomException(StockErrorCode.STOCK_NOT_FOUND));

        String corpCode = stockCode.getDartCorpCode();
        if(corpCode == null || corpCode.length() != 8) {
            throw new CustomException(StockErrorCode.DART_CORP_CODE_INVALID);
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

        // 1) DB 조회
        List<FinPeriod> periods = switch (type) {
            case ANNUAL -> finPeriodRepository.findRecentYearlyPeriods(companyId,0, limit);
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
        List<FinMetricValue> allValues = finMetricValueRepository.findByCompanyIdAndPeriodIds(companyId, periodIds);
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

    public Map<String, BigDecimal> buildFinancialMetrics(Long companyId, int currentYear) {

        Map<String, BigDecimal> result = new LinkedHashMap<>();

        List<DartFsLine> lines = dartFsLineRepository.findByFiling_CompanyIdAndFiling_BsnsYear(companyId, currentYear);

        if (lines.isEmpty()) {
            log.warn("buildFinancialMetrics - 재무제표 라인 데이터가 없습니다. companyId={}, year={}", companyId, currentYear);
            return result;
        }

        log.debug("==== {}년 조회된 재무제표 라인 수 : {}", currentYear, lines.size());

        FsRawBundle rawBundle = collectRawBundle(lines, currentYear);
        Map<String, BigDecimal> raw = rawBundle.curr();
        Map<String, BigDecimal> prevRaw = rawBundle.prev();

        // 원천 지표 + 계산 지표 결합
        result.putAll(extractBaseMetrics(raw));
        result.putAll(calculateDerivedMetrics(companyId, raw, prevRaw, currentYear));

        log.info("=== {}년 FS-DB 기반 FIN_METRIC 결과 ({}개 지표) ===", currentYear, result.size());
        result.forEach((k, v) -> log.info("   • metricCode='{}', value={}", k, v));

        return result;
    }

    private FsRawBundle collectRawBundle(List<DartFsLine> lines, int year) {

        Map<String, BigDecimal> curr = new LinkedHashMap<>();
        Map<String, BigDecimal> prev = new LinkedHashMap<>();


        for(DartFsLine line : lines) {
            String sjDiv = line.getSjDiv();                 // 재무제표 구분
            String accountId = line.getAccountId();         // 계정Id
            String accountNm = line.getAccountNm();         // 계정설명
            String accountDetail = line.getAccountDetail(); // 구성요소 [member] 등

            BigDecimal currVal = line.getThstrmAmount();    // 당기금액
            BigDecimal prevVal = line.getFrmtrmAmount();    // 전기금액

            String metricCode = mapAccountToMetric(sjDiv, accountId, accountNm, accountDetail);

            if (metricCode == null) {
                continue;
            }

            // 당기
            if (currVal != null) {
                BigDecimal old = curr.get(metricCode);
                if (old != null && old.compareTo(currVal) != 0) {
                    log.debug("[FS-DB][DUP] metric={} old={} new={} (accountId={}, accountNm={})",
                            metricCode, old, currVal, accountId, accountNm);
                } else if (old == null) {
                    curr.put(metricCode, currVal);
                }
            }
            // 전기
            if (prevVal != null) {
                prev.put(metricCode, prevVal);
            }
        }

        return new FsRawBundle(curr, prev);
    }

    private Map<String, BigDecimal> extractBaseMetrics(Map<String, BigDecimal> raw) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();

        if (raw == null || raw.isEmpty()) {
            return result;
        }

        putIfNotNull(result, "SALES",              raw.get("SALES"));
        putIfNotNull(result, "OP_INC",             raw.get("OP_INC"));
        putIfNotNull(result, "NET_INC",            raw.get("NET_INC"));
        putIfNotNull(result, "NET_INC_OWNER",      raw.get("NET_INC_OWNER"));
        putIfNotNull(result, "NET_INC_NONCONT",    raw.get("NET_INC_NONCONT"));
        putIfNotNull(result, "TOTAL_EQUITY",       raw.get("TOTAL_EQUITY"));
        putIfNotNull(result, "TOTAL_EQUITY_OWNER", raw.get("TOTAL_EQUITY_OWNER"));

        return result;
    }

    private Map<String, BigDecimal> calculateDerivedMetrics(
            Long companyId,
            Map<String, BigDecimal> raw,
            Map<String, BigDecimal> prevRaw,
            int currentYear
    ) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();

        if (raw == null || raw.isEmpty()) {
            return result;
        }

        BigDecimal sales             = raw.get("SALES");
        BigDecimal opInc             = raw.get("OP_INC");
        BigDecimal netInc            = raw.get("NET_INC");          // 전체 당기순이익
        BigDecimal netIncOwner       = raw.get("NET_INC_OWNER");    // 지배주주 당기순이익
        BigDecimal totalAssets       = raw.get("TOTAL_ASSETS");
        BigDecimal totalLiab         = raw.get("TOTAL_LIABILITIES");
        BigDecimal equityTotalCurr   = raw.get("TOTAL_EQUITY");         // 전체 자본
        BigDecimal equityTotalPrev   = prevRaw != null ? prevRaw.get("TOTAL_EQUITY") : null;
        BigDecimal equityOwnerCurr   = raw.get("TOTAL_EQUITY_OWNER");   // 지배 기준 자본
        BigDecimal equityOwnerPrev   = prevRaw != null ? prevRaw.get("TOTAL_EQUITY_OWNER") : null;
        BigDecimal currentAssets     = raw.get("CURRENT_ASSETS");
        BigDecimal currentLiab       = raw.get("CURRENT_LIABILITIES");

        // 영업이익률 OPM
        BigDecimal opm = raw.get("OPM");
        if (opm == null) {
            opm = toPercent(safeDivide(opInc, sales));
        }
        putIfNotNull(result, "OPM", opm);

        // 순이익률 NET_MARGIN
        BigDecimal netMargin = raw.get("NET_MARGIN");
        if (netMargin == null) {
            netMargin = toPercent(safeDivide(netInc, sales));
        }
        putIfNotNull(result, "NET_MARGIN", netMargin);

        // 부채비율 DEBT_RATIO = 부채총계 / 자본총계 * 100
        BigDecimal equityForDebt = (equityTotalCurr != null ? equityTotalCurr : equityOwnerCurr);
        BigDecimal debtRatio = toPercent(safeDivide(totalLiab, equityForDebt));
        putIfNotNull(result, "DEBT_RATIO", debtRatio);

        // ROE = (지배주주 당기순이익 or 전체) / 평균 지배주주자본(or 전체) * 100
        BigDecimal roeSourceNetInc  = (netIncOwner != null ? netIncOwner : netInc);
        BigDecimal roeEquityCurr    = (equityOwnerCurr != null ? equityOwnerCurr : equityTotalCurr);
        BigDecimal roeEquityPrev    = (equityOwnerPrev != null ? equityOwnerPrev : equityTotalPrev);

        if (roeSourceNetInc != null && roeEquityCurr != null && roeEquityPrev != null) {
            BigDecimal avgEquity = roeEquityCurr.add(roeEquityPrev)
                    .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);

            if (avgEquity.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal roe = toPercent(
                        roeSourceNetInc.divide(avgEquity, 8, RoundingMode.HALF_UP)
                );
                putIfNotNull(result, "ROE", roe);

                log.debug("[ROE] year={} / netInc(used)={} / equity_curr={} / equity_prev={} / avgEquity={} / ROE={}",
                        currentYear, roeSourceNetInc, roeEquityCurr, roeEquityPrev, avgEquity, roe);
            } else {
                log.debug("[FS-DB][ROE] 평균 자기자본 0 - year={}", currentYear);
            }
        } else {
            log.debug("[FS-DB][ROE] netIncOwner/equityOwnerCurr/equityOwnerPrev 중 null 존재 - year={}", currentYear);
        }

        // ROA = 당기순이익 / 자산총계 * 100
        BigDecimal roa = toPercent(safeDivide(netInc, totalAssets));
        putIfNotNull(result, "ROA", roa);

        // 유동비율(단순) = 유동자산 / 유동부채 * 100
        BigDecimal quickRatio = toPercent(safeDivide(currentAssets, currentLiab));
        putIfNotNull(result, "QUICK_RATIO", quickRatio);

        // EPS = 지배주주순이익 / 보통주 주식수
        Optional<BigDecimal> epsOpt = calcEps(companyId, currentYear, netIncOwner);
        epsOpt.ifPresent(eps -> putIfNotNull(result, "EPS", eps));

        return result;
    }

    private Optional<BigDecimal> calcEps(Long companyId, int fiscalYear, BigDecimal netIncOwner) {
        if (netIncOwner == null) {
            log.debug("[FS-DB][EPS] netIncOwner is null - companyId={}, year={}", companyId, fiscalYear);
            return Optional.empty();
        }

        Optional<BigDecimal> eps = findTotalIssuedShares(companyId, fiscalYear)
                .filter(shares -> shares.compareTo(BigDecimal.ZERO) > 0)
                .map(shares -> netIncOwner.divide(shares, 2, RoundingMode.HALF_UP));

        if (eps.isEmpty()) {
            log.debug("[FS-DB][EPS] common shares not found or zero - companyId={}, year={}", companyId, fiscalYear);
        } else {
            log.debug("[FS-DB][EPS] ok - companyId={}, year={}, netIncOwner={}, eps={}",
                    companyId, fiscalYear, netIncOwner, eps.get());
        }

        return eps;
    }


    private Optional<BigDecimal> findTotalIssuedShares(Long companyId, int fiscalYear) {
        List<StockShareStatus> statuses =
                stockShareStatusRepository.findByCompany_CompanyIdAndBsnsYearAndSeIn(
                        companyId, fiscalYear, List.of("보통주", "우선주")
                );

        if (statuses.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal total = BigDecimal.ZERO;
        for (StockShareStatus status : statuses) {
            BigDecimal shares = resolveIssuedShares(status);
            if (shares != null) {
                total = total.add(shares);
            }
        }

        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        return Optional.of(total);
    }

    private BigDecimal resolveIssuedShares(StockShareStatus status) {
        Long istc = status.getIstcTotqy();
        if (istc != null && istc > 0L) {
            return BigDecimal.valueOf(istc);
        }

        return null;
    }


        /**
         * DB에 저장된 dart 재무제표(dart_fs_line) 기반으로
         * 한 해 주요 값들을 추출 및 계산
         */
    public Map<String, BigDecimal> buildFinancialMetricsBackup(Long companyId, int currentYear) {

        Map<String, BigDecimal> raw = new LinkedHashMap<>();
        Map<String, BigDecimal> prevRaw = new LinkedHashMap<>();
        Map<String, BigDecimal> result = new LinkedHashMap<>();

        List<DartFsLine> lines = dartFsLineRepository.findByFiling_CompanyIdAndFiling_BsnsYear(companyId, currentYear);

        if (lines.isEmpty()) {
            log.warn("buildFinancialMetrics - 재무제표 라인 데이터가 없습니다. companyId={}, year={}", companyId, currentYear);
            return result;
        }

        log.debug("==== {}년 조회된 재무제표 라인 수 : {}",currentYear, lines.size());

        for(DartFsLine line : lines) {
            String sjDiv = line.getSjDiv();                 // 재무제표 구분
            String accountId = line.getAccountId();         // 계정Id
            String accountNm = line.getAccountNm();         // 계정설명
            String accountDetail = line.getAccountDetail(); // 구성요소 [member] 등

            BigDecimal currVal = line.getThstrmAmount();    // 당기금액
            BigDecimal prevVal = line.getFrmtrmAmount();    // 전기금액

            String metricCode = mapAccountToMetric(sjDiv, accountId, accountNm, accountDetail);

            if (metricCode == null) {
                log.debug(" X [FS-DB][UNMAPPED] year={}, sjDiv={}, accountId={}, accountNm={}, accountDetail={}",
                        currentYear, sjDiv, accountId, accountNm, accountDetail);
                continue;
            }

            log.debug(" O [FS-DB][UNMAPPED] year={}, sjDiv={}, accountId={}, accountNm={}, metricCode={}, accountDetail={}",
                    currentYear, sjDiv, accountId, accountNm, metricCode, accountDetail);
            // 당기
            if (currVal != null) {
                BigDecimal old = raw.get(metricCode);
                if (old != null && old.compareTo(currVal) != 0) {
                    log.debug("[FS-DB][DUP] metric={} old={} new={} (accountId={}, accountNm={})",
                            metricCode, old, currVal, accountId, accountNm);
                } else if (old == null) {
                    raw.put(metricCode, currVal);
                }
            }
            // 전기
            if (prevVal != null) {
                prevRaw.put(metricCode, prevVal);
            }


        }
        log.info("=== {}년 FS-DB RAW ({}개 지표) ===", currentYear, raw.size());
        raw.forEach((k, v) -> log.info("raw[{}] = {}", k, v));

        BigDecimal sales             = raw.get("SALES");
        BigDecimal opInc             = raw.get("OP_INC");
        BigDecimal netInc            = raw.get("NET_INC");          // 전체 당기순이익
        BigDecimal netIncOwner       = raw.get("NET_INC_OWNER");    // 지배주주 당기순이익
        BigDecimal netIncNonCont     = raw.get("NET_INC_NONCONT");  // 비지배 당기순이익
        BigDecimal totalAssets       = raw.get("TOTAL_ASSETS");
        BigDecimal totalLiab         = raw.get("TOTAL_LIABILITIES");
        BigDecimal equityTotalCurr   = raw.get("TOTAL_EQUITY");         // 전체 자본
        BigDecimal equityTotalPrev   = prevRaw.get("TOTAL_EQUITY");
        BigDecimal equityOwnerCurr   = raw.get("TOTAL_EQUITY_OWNER");   // 지배 기준 자본
        BigDecimal equityOwnerPrev   = prevRaw.get("TOTAL_EQUITY_OWNER");
        BigDecimal currentAssets     = raw.get("CURRENT_ASSETS");
        BigDecimal currentLiab       = raw.get("CURRENT_LIABILITIES");
        BigDecimal eps               = raw.get("EPS");
        BigDecimal bps               = raw.get("BPS");

        putIfNotNull(result, "SALES",               sales);
        putIfNotNull(result, "OP_INC",              opInc);
        putIfNotNull(result, "NET_INC",             netInc);
        putIfNotNull(result, "NET_INC_OWNER",       netIncOwner);
        putIfNotNull(result, "NET_INC_NONCONT",     netIncNonCont);
        putIfNotNull(result, "TOTAL_EQUITY",        equityTotalCurr);
        putIfNotNull(result, "TOTAL_EQUITY_OWNER",  equityOwnerCurr);
        putIfNotNull(result, "EPS",                 eps);
        putIfNotNull(result, "BPS",                 bps);


        // 영업이익률 OPM
        BigDecimal opm = raw.get("OPM");
        if (opm == null) {
            opm = toPercent(safeDivide(opInc, sales));
        }
        putIfNotNull(result, "OPM", opm);


        // 순이익률 NET_MARGIN
        BigDecimal netMargin = raw.get("NET_MARGIN");
        if (netMargin == null) {
            netMargin = toPercent(safeDivide(netInc, sales));
        }
        putIfNotNull(result, "NET_MARGIN", netMargin);

        // 부채비율 DEBT_RATIO = 부채총계 / 자본총계 * 100
        BigDecimal equityForDebt = (equityTotalCurr != null ? equityTotalCurr : equityOwnerCurr);
        BigDecimal debtRatio = toPercent(safeDivide(totalLiab, equityForDebt));
        putIfNotNull(result, "DEBT_RATIO", debtRatio);

        // ROE = (지배주주 당기순이익 or 전체) / 평균 지배주주자본(or 전체) * 100
        BigDecimal roeSourceNetInc  = (netIncOwner != null ? netIncOwner : netInc);
        BigDecimal roeEquityCurr    = (equityOwnerCurr != null ? equityOwnerCurr : equityTotalCurr);
        BigDecimal roeEquityPrev    = (equityOwnerPrev != null ? equityOwnerPrev : equityTotalPrev);

        if (roeSourceNetInc != null && roeEquityCurr != null && roeEquityPrev != null) {
            BigDecimal avgEquity = roeEquityCurr.add(roeEquityPrev)
                    .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);

            if (avgEquity.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal roe = toPercent(
                        roeSourceNetInc.divide(avgEquity, 8, RoundingMode.HALF_UP)
                );
                putIfNotNull(result, "ROE", roe);

                log.debug("[ROE] year={} / netInc(used)={} / equity_curr={} / equity_prev={} / avgEquity={} / ROE={}",
                        currentYear, roeSourceNetInc, roeEquityCurr, roeEquityPrev, avgEquity, roe);
            } else {
                log.debug("[FS-DB][ROE] 평균 자기자본 0 - year={}", currentYear);
            }
        } else {
            log.debug("[FS-DB][ROE] netIncOwner/equityOwnerCurr/equityOwnerPrev 중 null 존재 - year={}", currentYear);
        }

        // ROA = 당기순이익 / 자산총계 * 100
        BigDecimal roa = toPercent(safeDivide(netInc, totalAssets));
        putIfNotNull(result, "ROA", roa);

        // 유동비율(단순) = 유동자산 / 유동부채 * 100
        BigDecimal quickRatio = toPercent(safeDivide(currentAssets, currentLiab));
        putIfNotNull(result, "QUICK_RATIO", quickRatio);

        log.info("=== {}년 FS-DB 기반 FIN_METRIC 결과 ({}개 지표) ===", currentYear, result.size());
        result.forEach((k, v) -> log.info("   • metricCode='{}', value={}", k, v));

        return result;
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
        if (key.id.equals("ifrs-full_EquityAttributableToOwnersOfParent")
                || key.id.equals("ifrs_EquityAttributableToOwnersOfParent")
                || key.nm.contains("지배기업의 소유주에게 귀속되는 자본")
                || key.nm.contains("지배주주지분")) {
            return "TOTAL_EQUITY_OWNER";
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
        if ((key.id.equals("ifrs-full_ProfitLoss") || key.id.equals("ifrs_ProfitLoss") || key.id.contains("미사용"))
                && (key.nm.contains("당기순이익") || key.nm.contains("당기순손실") || key.nm.contains("당기순손익"))) {
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
        if ((key.id.equals("ifrs-full_ProfitLoss") || key.id.contains("미사용"))
                && (key.nm.contains("당기순이익") || key.nm.contains("당기순손실") || key.nm.contains("당기순손익"))
                && key.detail.contains("지배기업")) {

            return "NET_INC_OWNER";
        }

        // 비지배주주 귀속 당기순이익
        if ((key.id.equals("ifrs-full_ProfitLoss") || key.id.contains("미사용"))
                && (key.nm.contains("당기순이익") || key.nm.contains("당기순손실") || key.nm.contains("당기순손익"))
                && key.detail.contains("비지배")) {
            return "NET_INC_NONCONT";
        }

        // 2018년 이전에는 ifrs_ProfitLoss id를 사용하였음. 이후 참고 데이터

        // 그 외 SCE 항목은 무시
        return null;

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

    private String mapAccountToMetricBackup(String sjDiv, String accountId, String accountNm, String accountDetail) {
        if (accountId == null && accountNm == null) {
            return null;
        }

        String sj = sjDiv != null ? sjDiv.trim() : "";
        String id = accountId != null ? accountId.trim() : "";
        String nm = accountNm != null ? accountNm.trim() : "";
        String detail = accountDetail != null ? accountDetail.trim() : "";

        if ("SCE".equalsIgnoreCase(sj)) {


            // 지배주주 귀속 당기순이익
            if (id.equals("ifrs-full_ProfitLoss") && (nm.equals("당기순이익") || nm.equals("당기순손실") || nm.equals("당기순손익")) && detail.contains("지배기업")) {

                return "NET_INC_OWNER";
            }

            // 비지배주주 귀속 당기순이익
            if (id.equals("ifrs-full_ProfitLoss") && (nm.equals("당기순이익") || nm.equals("당기순손실") || nm.equals("당기순손익")) && detail.contains("비지배")) {
                return "NET_INC_NONCONT";
            }

            // 2018년 이전에는 ifrs_ProfitLoss id를 사용하였음. 이후 참고 데이터

            // 그 외 SCE 항목은 무시
            return null;
        }

        // 1) 손익계산서 / 포괄손익계산서 (CIS, IS 등)
        if ("CIS".equalsIgnoreCase(sj) || "IS".equalsIgnoreCase(sj)) {

            // 전체 당기순이익
            if (id.equals("ifrs-full_ProfitLoss") && (nm.equals("당기순이익") || nm.equals("당기순손실") || nm.equals("당기순손익"))) {
                // 전체 당기순이익
                return "NET_INC";
            }

            // 매출액 / 영업수익
            if (id.equals("ifrs-full_Revenue")
                    || id.equals("ifrs_Revenue")
                    || nm.contains("매출액")
                    || nm.contains("영업수익")) {
                return "SALES";
            }

            // 영업이익
            if (id.equals("ifrs-full_ProfitLossFromOperatingActivities")
                    || id.equals("ifrs_ProfitLossFromOperatingActivities")
                    || nm.contains("영업이익")) {
                return "OP_INC";
            }


            // EPS (기본주당순이익 등)
            if (id.contains("EarningsPerShare") || nm.contains("주당순이익")) {
                return "EPS";
            }
        }

        // 2) 재무상태표 (BS 등)
        if ("BS".equalsIgnoreCase(sj) || "BIS".equalsIgnoreCase(sj)) {

            // "부채와자본총계"는 자본으로 보지 않고 무시
            // (자산총계와 같은 값)
            if (id.equals("ifrs-full_EquityAndLiabilities")
                    || nm.contains("부채와자본총계")) {
                return null;
            }


            // 자산총계
            if (id.equals("ifrs-full_Assets")
                    || nm.contains("자산총계")) {
                return "TOTAL_ASSETS";
            }

            // 부채총계
            if (id.equals("ifrs-full_Liabilities")
                    || nm.contains("부채총계")) {
                return "TOTAL_LIABILITIES";
            }

            // 자본총계 (지배 + 비지배 포함)
            if (id.equals("ifrs-full_Equity")) {
                return "TOTAL_EQUITY";
            }
            if ((id.isEmpty() || "-".equals(id)) && nm.equals("자본총계")) {
                return "TOTAL_EQUITY";
            }



            // 지배주주지분 / 지배기업 소유주 지분
            if (id.equals("ifrs-full_EquityAttributableToOwnersOfParent")
                    || nm.contains("지배기업의 소유주에게 귀속되는 자본")
                    || nm.contains("지배주주지분")) {
                return "TOTAL_EQUITY_OWNER";
            }

            // 유동자산
            if (id.equals("ifrs-full_CurrentAssets")
                    || nm.contains("유동자산")) {
                return "CURRENT_ASSETS";
            }

            // 유동부채
            if (id.equals("ifrs-full_CurrentLiabilities")
                    || nm.contains("유동부채")) {
                return "CURRENT_LIABILITIES";
            }

            // BPS (주당순자산) - BS나 기타 주당지표에서 나올 수 있음
            if (id.contains("EquityPerShare") || nm.contains("주당순자산")) {
                return "BPS";
            }
        }

        // 기타 필요한 매핑 (나중에 케이스 생길 때마다 추가)
        // 예: 계속/중단영업 당기순이익(분리) -> CONT_NET_INC / DISC_NET_INC 등

        return null; // 사용하지 않는 계정
    }

    private BigDecimal toPercent(BigDecimal ratio) {
        if (ratio == null) return null;
        return ratio.multiply(BigDecimal.valueOf(100));
    }
    private void putIfNotNull(Map<String, BigDecimal> map, String key, BigDecimal value) {
        if (value != null) {
            map.put(key, value);
        }
    }
    private BigDecimal safeDivide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || BigDecimal.ZERO.compareTo(denominator) == 0) {
            return null;
        }
        // scale과 RoundingMode 조정가능
        return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
    }

    @Transactional
    public Map<String, BigDecimal> getOrBuildAnnualMetrics(Long companyId, int fiscalYear) {

        // DB 조회
        Map<String, BigDecimal> result = loadAnnualMetricsFromDb(companyId, fiscalYear);
        if (!result.isEmpty()) {
            log.debug("[FIN_METRIC] cached 사용 - companyId={}, year={}", companyId, fiscalYear);
            return result;
        }

        // 없으면 계산
        Map<String, BigDecimal> calculated = buildFinancialMetrics(companyId, fiscalYear);

        if (calculated.isEmpty()) {
            log.warn("[FIN_METRIC] 계산 결과 없음 - companyId={}, year={}", companyId, fiscalYear);
            return calculated;
        }

        // 계산 결과 DB 저장
        saveAnnualMetricsToDb(companyId, fiscalYear, calculated);

        return calculated;
    }

    // ------------------ DB에서 연간 지표 로드 ------------------
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> loadAnnualMetricsFromDb(Long companyId, int fiscalYear) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();

        Optional<FinPeriod> optPeriod =
                finPeriodRepository.findByCompany_CompanyIdAndPeriodTypeAndFiscalYearAndIsEstimate(
                        companyId, "YEAR", fiscalYear, false
                );

        if (optPeriod.isEmpty()) {
            return result;
        }

        FinPeriod period = optPeriod.get();

        List<FinMetricValue> values =
                finMetricValueRepository.findByCompanyIdAndPeriod_PeriodId(companyId, period.getPeriodId());

        for (FinMetricValue v : values) {
            BigDecimal value = v.getValueNum();
            if (value != null) {
                result.put(v.getMetricCode(), value);
            }
        }

        return result;
    }

    // ------------------ 연간 지표를 fin테이블에 저장 ------------------
    @Transactional
    public void saveAnnualMetricsToDb(Long companyId, int fiscalYear,
                                      Map<String, BigDecimal> metrics) {

        // fin_period 조회/생성
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

        // metricCode → value 저장 (fin_metric_def에 정의된 것만)
        for (Map.Entry<String, BigDecimal> entry : metrics.entrySet()) {
            String metricCode = entry.getKey();
            BigDecimal value = entry.getValue();

            Optional<FinMetricDef> optDef = finMetricDefRepository.findById(metricCode);
            if (optDef.isEmpty()) {
                log.debug("[FIN_METRIC] fin_metric_def에 정의 안된 코드 스킵: {}", metricCode);
                continue;
            }

            FinMetricValue fmv = finMetricValueRepository
                    .findByCompanyIdAndPeriodAndMetricCode(companyId, period, metricCode)
                    .orElseGet(FinMetricValue::new);

            fmv.setCompanyId(companyId);
            fmv.setPeriod(period);
            fmv.setMetricCode(metricCode);
            fmv.setValueNum(value);
            fmv.setSource("DART"); // CK_FMV_SOURCE 에 맞춤

            finMetricValueRepository.save(fmv);
        }

        log.info("[FIN_METRIC] 저장 완료 - companyId={}, year={}, metricCount={}",
                companyId, fiscalYear, metrics.size());
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
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        company.setSharesOutstanding( latest.getDistbStockCo());

        log.info("Company 주식수 정보 갱신 완료 - companyId={}, shares={}",
                companyId, company.getSharesOutstanding());
    }

    @Transactional
    public void replaceAnnualFinancial(String corpCode, Long companyId, List<DartFsRow> rows) {

        if(rows.isEmpty()) return;

        DartFsRow meta = rows.get(0);
        DartFsFiling filing = getOrCreateFiling(corpCode, companyId, meta);

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

        // to-do dart접수번호기준 조회 고려할것
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
                .reportTp("연간")
                .currency(firstRow.getCurrency())
                .rceptDt(rceptDt)
                .build();

        return filingRepository.save(filing);
    }

}
