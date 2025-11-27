package org.yhj.srim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.StockErrorCode;
import org.yhj.srim.controller.dto.CompanyMetaDto;
import org.yhj.srim.repository.*;
import org.yhj.srim.repository.entity.*;
import org.yhj.srim.service.dto.FinancialTableDto;
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

    /**
     * stockId로 연간 재무 테이블 조회
     */
    // to-do 리팩토링 완료 후 삭제
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

        log.info("=== [FS-DB] {}년 재무 데이터 ({}개 지표) ===", year, financialData.size());
        financialData.forEach((key, value) ->
                log.info(" key='{}', value={}", key, value)
        );

        if (financialData.isEmpty()) {
            log.warn("[FS-DB] {}년 재무 데이터 없음 (companyId={})", year, companyId);
            return 0;
        }

        //   - 연간정보는 월에 12, isEstimate=false
        FinPeriod period = saveOrUpdatePeriod(companyId, year, 12, false);

        int yearSaved = 0;
        for (Map.Entry<String, BigDecimal> entry : financialData.entrySet()) {
            String metricCode = entry.getKey();
            BigDecimal value  = entry.getValue();

            saveOrUpdateMetricValue(companyId, period.getPeriodId(), metricCode, value);
            yearSaved++;
        }

        log.info("[FS-DB] {}년 재무 데이터 저장 완료 - {}건 (companyId={})",
                year, yearSaved, companyId);

        return yearSaved;
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

    private void saveOrUpdateMetricValue(Long companyId, Long periodId, String metricCode, BigDecimal value) {
        log.debug("지표 값 저장 - company={}, period={}, metric={}, value={}",
                companyId, periodId, metricCode, value);

        Optional<FinMetricValue> existing = finMetricValueRepository
                .findByCompanyIdAndPeriodIdAndMetricCode(companyId, periodId, metricCode);

        if (existing.isPresent()) {
            FinMetricValue metricValue = existing.get();
            metricValue.setValueNum(value);
            metricValue.setSource("DART");
            finMetricValueRepository.save(metricValue);
            log.debug("지표 값 업데이트: {} = {}", metricCode, value);
        } else {
            FinMetricValue metricValue = FinMetricValue.builder()
                    .companyId(companyId)
                    .periodId(periodId)
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
            case ANNUAL -> finPeriodRepository.findRecentYearlyPeriods(companyId, limit);
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
                        v -> v.getPeriodId() + "_" + v.getMetricCode(),
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
            periods = finPeriodRepository.findRecentYearlyPeriods(companyId, nth);
        } else {
            periods = finPeriodRepository.findRecentQuarterlyPeriods(companyId, nth);
        }

        if (periods.isEmpty() || periods.size() < nth) {
            return null;
        }

        Long periodId = periods.get(nth - 1).getPeriodId();

        return finMetricValueRepository
                .findByCompanyIdAndPeriodIdAndMetricCode(companyId, periodId, metricCode)
                .map(FinMetricValue::getValueNum)
                .orElse(null);
    }

    /**
     * DB에 저장된 dart 재무제표(dart_fs_line) 기반으로
     * 한 해 주요 값들을 추출 및 계산
     */
    public Map<String, BigDecimal> buildFinancialMetrics(Long companyId, int currentYear) {

        Map<String, BigDecimal> raw = new LinkedHashMap<>();
        Map<String, BigDecimal> prevRaw = new LinkedHashMap<>();
        Map<String, BigDecimal> result = new LinkedHashMap<>();

        List<DartFsLine> lines = dartFsLineRepository.findByFiling_CompanyIdAndFiling_BsnsYear(companyId, currentYear);

        if (lines.isEmpty()) {
            log.warn("buildFinancialMetrics - 재무제표 라인 데이터가 없습니다. companyId={}, year={}", companyId, currentYear);
            return result;
        }

        for(DartFsLine line : lines) {
            String sjDiv = line.getSjDiv();                 // 재무제표 구분
            String accountId = line.getAccountId();         // 계정Id
            String accountNm = line.getAccountNm();         // 계정설명
            String accountDetail = line.getAccountDetail();// 구성요소 [member] 등

            BigDecimal currVal = line.getThstrmAmount();    // 당기금액
            BigDecimal prevVal = line.getFrmtrmAmount();    // 전기금액

            String metricCode = mapAccountToMetric(sjDiv, accountId, accountNm, accountDetail);

            if (metricCode == null) {
                log.debug("[FS-DB][UNMAPPED] year={}, sjDiv={}, accountId={}, accountNm={}",
                        currentYear, sjDiv, accountId, accountNm);
                continue;
            }

            // 당기
            if (currVal != null) {
                if ("NET_INC".equals(metricCode) && raw.containsKey("NET_INC")) {
                    log.debug("[FS-DB][DUP] NET_INC : old={}, new={}, accountId={}, accountNm={}",
                            raw.get("NET_INC"), currVal, accountId, accountNm);
                } else if ("NET_INC_OWNER".equals(metricCode) && raw.containsKey("NET_INC_OWNER")) {
                    log.debug("[FS-DB][DUP] NET_INC_OWNER : old={}, new={}, accountId={}, accountNm={}",
                            raw.get("NET_INC_OWNER"), currVal, accountId, accountNm);
                } else {
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

        // NET_INC(당기순이익) 없으면 CONT_NET_INC + DISC_NET_INC
        if (!raw.containsKey("NET_INC")) {
            BigDecimal cont = raw.getOrDefault("CONT_NET_INC", BigDecimal.ZERO);
            BigDecimal disc = raw.getOrDefault("DISC_NET_INC", BigDecimal.ZERO);

            if (cont.compareTo(BigDecimal.ZERO) != 0 ||
                    disc.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal netIncCalc = cont.add(disc);
                raw.put("NET_INC", netIncCalc);
                log.info(">>> FS-DB 계산된 NET_INC (당기순이익) = {}", netIncCalc);
            }
        }

        BigDecimal sales             = raw.get("SALES");
        BigDecimal opInc             = raw.get("OP_INC");
        BigDecimal netInc            = raw.get("NET_INC");          // 전체 당기순이익
        BigDecimal netIncOwner       = raw.get("NET_INC_OWNER");    // 지배주주 당기순이익
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
        if (accountId == null && accountNm == null) {
            return null;
        }

        String sj = sjDiv != null ? sjDiv.trim() : "";
        String id = accountId != null ? accountId.trim() : "";
        String nm = accountNm != null ? accountNm.trim() : "";
        String detail = accountDetail != null ? accountDetail.trim() : "";

        // 0) 자본변동표(SCE)는 일단 전체 스킵
        if ("SCE".equalsIgnoreCase(sj)) {
            return null;
        }

        // 1) 손익계산서 / 포괄손익계산서 (CIS, IS 등)
        if ("CIS".equalsIgnoreCase(sj) || "IS".equalsIgnoreCase(sj)) {

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

            // 전체 당기순이익
            if (id.equals("ifrs-full_ProfitLoss")
                    || id.equals("ifrs_ProfitLoss")
                    || nm.contains("당기순이익")) {
                return "NET_INC";
            }

            // 지배주주 당기순이익
            if (id.equals("ifrs-full_ProfitLossAttributableToOwnersOfParent")
                    || nm.contains("지배기업의 소유주에게 귀속되는 당기순이익")
                    || nm.contains("지배주주지분 순이익")) {
                return "NET_INC_OWNER";
            }

            // EPS (기본주당순이익 등)
            if (id.contains("EarningsPerShare") || nm.contains("주당순이익")) {
                return "EPS";
            }
        }

        // 2) 재무상태표 (BS 등)
        if ("BS".equalsIgnoreCase(sj) || "BIS".equalsIgnoreCase(sj)) {

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
            if (id.equals("ifrs-full_Equity")
                    || nm.contains("자본총계")) {
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
                finMetricValueRepository.findByCompanyIdAndPeriodId(companyId, period.getPeriodId());

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
                    .findByCompanyIdAndPeriodIdAndMetricCode(companyId, period.getPeriodId(), metricCode)
                    .orElseGet(FinMetricValue::new);

            fmv.setCompanyId(companyId);
            fmv.setPeriodId(period.getPeriodId());
            fmv.setMetricCode(metricCode);
            fmv.setValueNum(value);
            fmv.setSource("DART"); // CK_FMV_SOURCE 에 맞춤

            finMetricValueRepository.save(fmv);
        }

        log.info("[FIN_METRIC] 저장 완료 - companyId={}, year={}, metricCount={}",
                companyId, fiscalYear, metrics.size());
    }
}
