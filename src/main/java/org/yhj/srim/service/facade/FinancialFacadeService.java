package org.yhj.srim.service.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.client.KisSpreadClient;
import org.yhj.srim.client.dto.DartFsRow;
import org.yhj.srim.client.dto.DartShareStatusRow;
import org.yhj.srim.client.dto.KisSpreadRow;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.StockErrorCode;
import org.yhj.srim.controller.dto.CrawlAllMarketsResult;
import org.yhj.srim.repository.*;
import org.yhj.srim.repository.entity.*;
import org.yhj.srim.service.crawl.CrawlingService;
import org.yhj.srim.service.crawl.dto.StockCodeDraft;
import org.yhj.srim.service.domain.DartCorpCodeSyncService;
import org.yhj.srim.service.domain.FinancialService;
import org.yhj.srim.service.crawl.KrxStockCrawlingService;
import org.yhj.srim.service.domain.StockService;
import org.yhj.srim.service.dto.FinancialTableDto;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialFacadeService {

    private final KrxStockCrawlingService krxStockCrawlingService;
    private final CrawlingService crawlingService;
    private final KisSpreadClient kisSpreadClient;
    private final StockService stockService;

    private final DartCorpCodeSyncService dartCorpCodeSyncService;
    private final FinancialService financialService;
    private final FinPeriodRepository finPeriodRepository;
    private final FinMetricDefRepository finMetricDefRepository;
    private final FinMetricValueRepository finMetricValueRepository;
    private final BondYieldCurveRepository bondYieldCurveRepository;

    private static final String SOURCE = "KIS";

    /**
     * 1. company 조회, 없을 시 생성
     * 2. 재무제표, 주식 수 크롤링 및 저장
     * 3. 저장된 값들로 지표 계산 및 financialTableDto생성
     */
    public Company crawlAnnualTable(Long stockId, int limit) {

        return financialService.findCompanyByStockId(stockId)
                .orElseGet(() ->{

                    Company company = financialService.createCompany(stockId);
                    log.info("신규 company 생성 : stockId = {}, companyId = {}", stockId, company.getCompanyId());

                    initializeCompanyData(company, limit);
                    return company;
                });
    }

    public FinancialTableDto getAnnualTableDbOnly(Long stockId, int limit) {
        Company company = financialService.findCompanyByStockId(stockId)
                .orElseThrow(() -> new CustomException(StockErrorCode.COMPANY_NOT_FOUND));

        FinancialTableDto dto = buildAnnualTableDto(company, limit);

        return dto;
    }


    private FinancialTableDto buildAnnualTableDto(Company company, int limit) {
        Long companyId = company.getCompanyId();
        int currentYear = LocalDate.now().getYear();
        int startYear = currentYear - limit + 1;

        for(int year = currentYear; year >= startYear; year--){
            financialService.getOrBuildAnnualMetrics(companyId, year);
        }

        List<FinPeriod> periods = finPeriodRepository
                .findByCompany_CompanyIdAndPeriodTypeAndFiscalYearBetweenAndIsEstimateOrderByFiscalYearDesc(
                        companyId, "YEAR", startYear, currentYear, false
                );

        if (periods.isEmpty()) {
            log.warn("FinPeriod 없음 - companyId={}, years={}~{}", companyId, startYear, currentYear);
            return new FinancialTableDto(List.of(), List.of());
        }

        List<FinancialTableDto.PeriodHeaderDto> headers = buildPeriodHeaders(periods);
        List<Long> periodIds = extractPeriodIds(periods);
        List<FinMetricValue> metricValues =
                finMetricValueRepository.findByCompanyIdAndPeriod_PeriodIdIn(companyId, periodIds);
        Map<String, Map<Long, BigDecimal>> metricCodeToPeriodValueMap = indexMetricValuesByCode(metricValues);

        List<FinMetricDef> metricDefs = finMetricDefRepository.findAllByOrderByDisplayOrderAsc();
        List<FinancialTableDto.MetricRowDto> rows =
                buildMetricRows(metricDefs, periodIds, metricCodeToPeriodValueMap);

        return FinancialTableDto.builder()
                .headers(headers)
                .rows(rows)
                .build();
    }

    private List<FinancialTableDto.PeriodHeaderDto> buildPeriodHeaders(List<FinPeriod> periods) {
        return periods.stream()
                .map(p -> FinancialTableDto.PeriodHeaderDto.builder()
                        .periodId(p.getPeriodId())
                        .label(p.getLabel())              // ex) "2024/12"
                        .fiscalYear(p.getFiscalYear())
                        .fiscalQuarter(p.getFiscalQuarter())
                        .isEstimate(p.getIsEstimate())
                        .build())
                .collect(Collectors.toList());
    }

    private List<Long> extractPeriodIds(List<FinPeriod> periods) {
        return periods.stream()
                .map(FinPeriod::getPeriodId)
                .collect(Collectors.toList());
    }

    private Map<String, Map<Long, BigDecimal>> indexMetricValuesByCode(List<FinMetricValue> metricValues) {
        Map<String, Map<Long, BigDecimal>> metricCodeToPeriodValueMap = new HashMap<>();

        for (FinMetricValue v : metricValues) {
            String metricCode = v.getMetricCode();
            Long periodId = v.getPeriod().getPeriodId();
            BigDecimal value = v.getValueNum();

            metricCodeToPeriodValueMap
                    .computeIfAbsent(metricCode, k -> new HashMap<>())
                    .put(periodId, value);
        }

        return metricCodeToPeriodValueMap;
    }

    private List<FinancialTableDto.MetricRowDto> buildMetricRows(
            List<FinMetricDef> metricDefs,
            List<Long> periodIds,
            Map<String, Map<Long, BigDecimal>> metricCodeToPeriodValueMap
    ) {
        List<FinancialTableDto.MetricRowDto> rows = new ArrayList<>();

        for (FinMetricDef def : metricDefs) {
            String metricCode = def.getMetricCode();
            String nameKor = def.getNameKor();
            String unit = def.getUnit();

            Map<Long, BigDecimal> periodValueMap =
                    metricCodeToPeriodValueMap.getOrDefault(metricCode, Collections.emptyMap());

            Map<Long, BigDecimal> valueCopy = new LinkedHashMap<>();
            for (Long periodId : periodIds) {
                if (periodValueMap.containsKey(periodId)) {
                    valueCopy.put(periodId, periodValueMap.get(periodId));
                }
            }

            FinancialTableDto.MetricRowDto row = FinancialTableDto.MetricRowDto.builder()
                    .metricCode(metricCode)
                    .metricName(nameKor)
                    .unit(unit)
                    .displayOrder(def.getDisplayOrder())
                    .values(valueCopy)
                    .build();

            rows.add(row);
        }

        return rows;
    }


    // 데이터가 있는 경우 Skip, Delete&Insert, Upsert
    private void initializeCompanyData(Company company, int limit) {
        String corpCode = company.getStockCode().getDartCorpCode();
        Long companyId = company.getCompanyId();

        int currentYear = LocalDate.now().getYear();
        int startYear   = currentYear - limit + 1;

        log.info("전체 파이프라인 실행 - companyId={}, corpCode={}, year {}~{}",
                companyId, corpCode, startYear, currentYear);

        for (int year = currentYear-1; year >= startYear; year--) {
            log.debug("{}년 크롤링 및 계산 진행", year);

            // 재무제표 크롤링 , dart_fs_filing + dart_fs_line DB저장
//            crawlingService.crawlAndSaveAnnualFinancial(corpCode, companyId, year);
            // 재무제표 크롤링
            List<DartFsRow> fsRows = crawlingService.crawlAnnualFinancial(corpCode, year);
            // 재무제표 정보 저장 Line, Filing
            if(!fsRows.isEmpty()) financialService.replaceAnnualFinancial(corpCode, companyId, fsRows);

            // 주식수 크롤링 + dart_share_status 저장
//            crawlingService.crawlAndSaveShareStatus(corpCode, companyId, year);
            List<DartShareStatusRow> shareStatusRows = crawlingService.crawlShareStatus(company, year);
            stockService.replaceShareStatus(company, year,shareStatusRows);


            // dart_fs_line 기반 -> fin_metric_value 저장 (필요 데이터 가공)
            financialService.recalcAndSaveFinancialForYearFromDb(company, year);
        }

        financialService.updateCompanyShareInfo(companyId);
    }
    private FinancialTableDto buildFinancialTableDtoFromMetrics(
            Map<Integer, Map<String, BigDecimal>> metricsByYear) {

        // 기간 헤더 생성 (연간만 다루니까 fiscalQuarter=null, isEstimate=false)
        List<FinancialTableDto.PeriodHeaderDto> headers = new ArrayList<>();

        // metricCode 별로 row 모으기
        Map<String, FinancialTableDto.MetricRowDto> rowMap = new LinkedHashMap<>();

        for (Map.Entry<Integer, Map<String, BigDecimal>> entry : metricsByYear.entrySet()) {
            Integer year = entry.getKey();
            Map<String, BigDecimal> metrics = entry.getValue();

            // 여기서는 편의상 periodId = year 로 사용 (나중에 FinPeriod 쓰면 교체)
            Long periodId = year.longValue();

            // label 은 "YYYY/12"
            String label = year + "/12";

            headers.add(FinancialTableDto.PeriodHeaderDto.builder()
                    .periodId(periodId)
                    .label(label)
                    .fiscalYear(year)
                    .fiscalQuarter(null)     // 연간
                    .isEstimate(false)
                    .build());

            // metric 들을 MetricRowDto 에 채워넣기
            for (Map.Entry<String, BigDecimal> mEntry : metrics.entrySet()) {
                String metricCode = mEntry.getKey();
                BigDecimal value  = mEntry.getValue();

                FinancialTableDto.MetricRowDto row =
                        rowMap.computeIfAbsent(metricCode, code -> FinancialTableDto.MetricRowDto.builder()
                                .metricCode(code)
                                .metricName(resolveMetricName(code))  // 한글명
                                .unit(resolveMetricUnit(code))        // 단위
                                .values(new LinkedHashMap<>())
                                .build()
                        );

                row.getValues().put(periodId, value);
            }
        }

        return FinancialTableDto.builder()
                .headers(headers)
                .rows(new ArrayList<>(rowMap.values()))
                .build();
    }

    private String resolveMetricName(String code) {
        return switch (code) {
            case "SALES"              -> "매출액";
            case "OP_INC"             -> "영업이익";
            case "NET_INC"            -> "당기순이익";
            case "NET_INC_OWNER"      -> "지배주주 순이익";
            case "TOTAL_EQUITY"       -> "자본총계";
            case "TOTAL_EQUITY_OWNER" -> "지배주주지분";
            case "ROE"                -> "ROE";
            case "ROA"                -> "ROA";
            case "OPM"                -> "영업이익률";
            case "NET_MARGIN"         -> "순이익률";
            case "DEBT_RATIO"         -> "부채비율";
            case "QUICK_RATIO"        -> "유동비율";
            case "EPS"                -> "EPS";
            case "BPS"                -> "BPS";
            default                   -> code;
        };
    }

    private String resolveMetricUnit(String code) {

        return switch (code) {
            case "ROE", "ROA", "OPM", "NET_MARGIN", "DEBT_RATIO", "QUICK_RATIO" -> "%";
            case "EPS", "BPS" -> "원/주";
            default -> "백만원";
        };
    }

    @Transactional
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

    @Transactional
    public void CrawlAndSaveBondYield(LocalDate startDate, LocalDate endDate) {

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate/endDate는 null일 수 없습니다.");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate는 endDate보다 이후일 수 없습니다.");
        }

        int processedDays = 0;
        int upsertCount = 0;
        int skippedDays = 0;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {

            // 주말 스킵
            DayOfWeek dow = date.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                skippedDays++;
                continue;
            }

            List<KisSpreadRow> rows;
            try {
                rows = kisSpreadClient.fetchSpreadRows(date);
            } catch (Exception e) {
                log.warn("KIS 수익률 조회 실패 date={}", date, e);
                continue;
            }

            if (rows == null || rows.isEmpty()) {
                skippedDays++;
                continue;
            }

            for (KisSpreadRow row : rows) {
                String rating = normalizeRating(row.category());

                // 만기별 upsert
                upsertCount += upsertTenor(date, rating, (short) 3,  row.m3());
                upsertCount += upsertTenor(date, rating, (short) 6,  row.m6());
                upsertCount += upsertTenor(date, rating, (short) 9,  row.m9());
                upsertCount += upsertTenor(date, rating, (short) 12, row.y1());
                upsertCount += upsertTenor(date, rating, (short) 18, row.y1_6());
                upsertCount += upsertTenor(date, rating, (short) 24, row.y2());
                upsertCount += upsertTenor(date, rating, (short) 36, row.y3());
                upsertCount += upsertTenor(date, rating, (short) 60, row.y5());
            }

            processedDays++;

            if (processedDays % 50 == 0) {
                log.info("BondYield progress processedDays={}, upserts={}, skippedDays={}",
                        processedDays, upsertCount, skippedDays);
            }
        }

        log.info("BondYield done processedDays={}, upserts={}, skippedDays={}, range={}~{}",
                processedDays, upsertCount, skippedDays, startDate, endDate);
    }

    private int upsertTenor(LocalDate asOf, String rating, short tenorMonths, BigDecimal ratePercent) {
        if (asOf == null || rating == null || rating.isBlank()) return 0;
        if (ratePercent == null) return 0;

        // 엔티티 주석: 0.0286 = 2.86%  → 퍼센트(2.86)를 소수로 저장
        BigDecimal yieldRate = ratePercent.movePointLeft(2);

        return bondYieldCurveRepository.upsert(asOf, rating, tenorMonths, yieldRate, SOURCE);
    }

    private String normalizeRating(String category) {
        if (category == null) return "UNKNOWN";
        return category.trim();
    }

    private void addIfPresent(
            List<BondYieldCurve> target,
            LocalDate asOf,
            String rating,
            short tenorMonths,
            BigDecimal yieldRate
    ) {
        if (yieldRate == null) return;

        target.add(BondYieldCurve.builder()
                .asOf(asOf)
                .rating(rating)
                .tenorMonths(tenorMonths)
                .yieldRate(yieldRate)
                .source(SOURCE)
                .build());
    }

}
