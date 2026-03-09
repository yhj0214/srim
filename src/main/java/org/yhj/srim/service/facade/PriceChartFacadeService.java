package org.yhj.srim.service.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.service.facade.dto.YearBaseData;
import org.yhj.srim.repository.BondYieldCurveRepository;
import org.yhj.srim.repository.FinMetricValueRepository;
import org.yhj.srim.repository.FinPeriodRepository;
import org.yhj.srim.repository.entity.BondYieldCurve;
import org.yhj.srim.repository.entity.FinMetricValue;
import org.yhj.srim.repository.entity.FinPeriod;
import org.yhj.srim.repository.entity.StockPrice;
import org.yhj.srim.client.dto.DaliyPrice;
import org.yhj.srim.service.crawl.NaverCrawlingService;
import org.yhj.srim.service.domain.SrimService;
import org.yhj.srim.service.domain.StockPriceService;
import org.yhj.srim.service.dto.SrimResultDto;
import org.yhj.srim.service.dto.StockPriceDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class PriceChartFacadeService {

    private static final int INITIAL_BACKFILL_YEARS = 10;
    private static final LocalDate MIN_AVAILABLE_DATE = LocalDate.of(2015, 1, 1);
    private static final String DEFAULT_RATING = "BBB-";
    private static final Short DEFAULT_TENOR_MONTHS = 60;
    private static final String METRIC_EPS = "EPS";

    private final BondYieldCurveRepository bondYieldCurveRepository;
    private final FinPeriodRepository finPeriodRepository;
    private final FinMetricValueRepository finMetricValueRepository;
    private final NaverCrawlingService naverCrawlingService;
    private final SrimService srimService;
    private final StockPriceService stockPriceService;


    /**
     * 주가 차트 데이터 조회 (주가 + S-RIM 적정주가)
     *
     * @param companyId 회사 ID
     * @return 주가 차트 데이터
     */
    public StockPriceDto getPriceChart(Long companyId, LocalDate startDate, LocalDate endDate) {

        LocalDate end = (endDate != null) ? endDate : LocalDate.now();
        LocalDate start = (startDate != null) ? startDate : end.minusYears(1);

        if (start.isBefore(MIN_AVAILABLE_DATE)) {
            start = MIN_AVAILABLE_DATE;
        }
        if (end.isBefore(MIN_AVAILABLE_DATE)) {
            return StockPriceDto.builder().priceData(List.of()).build();
        }

        LocalDate hardMin = end.minusYears(INITIAL_BACKFILL_YEARS);
        if (start.isBefore(hardMin)) start = hardMin;

        LocalDate minTradeDate = stockPriceService.findMinTradeDateByCompany(companyId);
        LocalDate maxTradeDate = stockPriceService.findMaxTradeDateByCompany(companyId);

        if (minTradeDate == null || maxTradeDate == null) {
            return StockPriceDto.builder().priceData(List.of()).build();
        }

        if (start.isBefore(minTradeDate)) start = minTradeDate;
        if (end.isAfter(maxTradeDate)) end = maxTradeDate;

        if (start.isAfter(end)) {
            return StockPriceDto.builder().priceData(List.of()).build();
        }

        // 3. DB에서 주가 데이터 조회 (tradeDate 기준)
        List<StockPrice> stockPrices = stockPriceService
                .findPricesByCompanyIdAndTradeDateBetween(companyId, start, end);
        log.info("조회된 주가 데이터 개수: {}", stockPrices.size());

        if (stockPrices.isEmpty()) {
            log.warn("주가 데이터가 없습니다. 빈 결과 반환.");
            return StockPriceDto.builder()
                    .priceData(List.of())
                    .build();
        }

        // 4. S-RIM 적정주가 계산 (실제 존재하는 연도 기준)
        Map<LocalDate, SrimResultDto> srimByDate =
                calculateSrimForDates(companyId, stockPrices);

        // 4-1. 일별 PER 계산용 EPS 맵 구성 (주가 연도 → 전년도 EPS)
        Map<Integer, BigDecimal> epsByYear = buildEpsByYear(companyId, stockPrices);
        // 5. DTO 변환 (주가 + 적정주가 결합)
        List<StockPriceDto.PriceData> priceDataList =
                stockPrices.stream()
                        .map(sp -> convertToPriceData(sp, srimByDate, epsByYear))
                        .toList();

        log.info("PriceChart 조회 완료 - 응답 데이터 개수: {}", priceDataList.size());

        return StockPriceDto.builder()
                .priceData(priceDataList)
                .build();
    }

    /**
     * 주가 데이터 확보
     */
    public void ensurePriceData(Long companyId, LocalDate start, LocalDate end) {
        if (end.isBefore(MIN_AVAILABLE_DATE)) {
            log.info("요청 종료일이 최소 제공일 이전입니다. end={}", end);
            return;
        }
        if (start.isBefore(MIN_AVAILABLE_DATE)) {
            start = MIN_AVAILABLE_DATE;
        }

        boolean hasData = stockPriceService.existsByCompanyId(companyId);

        if (!hasData) {
            LocalDate backfillStart = end.minusYears(INITIAL_BACKFILL_YEARS);
            if (backfillStart.isBefore(MIN_AVAILABLE_DATE)) {
                backfillStart = MIN_AVAILABLE_DATE;
            }
            log.info("주가 데이터 최초 조회. companyId={} → {} ~ {} ({}년치) 백필 크롤링",
                    companyId, backfillStart, end, INITIAL_BACKFILL_YEARS);

            try {
                crawlAndSavePrices(companyId, backfillStart, end);
            } catch (Exception e) {
                log.error("주가 크롤링 실패(최초 백필): {}", e.getMessage(), e);
                // 실패해도 이후 DB 조회는 진행 (혹시 다른 경로로 적재된 데이터가 있을 수 있음)
            }
            return;
        }

        // 기존 데이터가 있는 경우 - 부족한 구간 체크
        LocalDate dbStart = stockPriceService.findMinTradeDateByCompany(companyId);
        LocalDate dbEnd = stockPriceService.findMaxTradeDateByCompany(companyId);

        if (dbStart == null || dbEnd == null) {
            log.warn("DB에 주가 데이터가 없습니다. companyId={}", companyId);
            return;
        }

        log.info("DB 주가 데이터 범위: {} ~ {}", dbStart, dbEnd);

        // 요청 시작일이 DB 범위보다 이전이면 앞부분 크롤링
        if (start.isBefore(dbStart)) {
            LocalDate crawlEnd = dbStart.minusDays(1);
            log.info("앞부분 주가 데이터 크롤링 필요: {} ~ {}", start, crawlEnd);
            try {
                crawlAndSavePrices(companyId, start, crawlEnd);
            } catch (Exception e) {
                log.error("앞부분 주가 크롤링 실패: {}", e.getMessage(), e);
            }
        }

        // 뒷부분 부족 시 크롤링
        if (end.isAfter(dbEnd)) {
            LocalDate crawlStart = dbEnd.plusDays(1);
            log.info("뒷부분 주가 데이터 크롤링 필요: {} ~ {}", crawlStart, end);
            try {
                crawlAndSavePrices(companyId, crawlStart, end);
            } catch (Exception e) {
                log.error("뒷부분 주가 크롤링 실패: {}", e.getMessage(), e);
            }
        }

        if (!start.isBefore(dbStart) && !end.isAfter(dbEnd)) {
            log.info("주가 데이터 크롤링 불필요 - DB에 충분한 데이터 존재.");
        }
    }

    private int crawlAndSavePrices(Long companyId, LocalDate start, LocalDate end) {
        String tickerKrx = stockPriceService.findTickerKrxByCompanyId(companyId);
        List<DaliyPrice> dailyPrices = naverCrawlingService.fetchDailyPrices(tickerKrx, start, end);
        return stockPriceService.savePrices(companyId, dailyPrices);
    }

    /**
     * 기간 내 연도별 S-RIM 계산
     *
     * 핵심 로직:
     * - 2025년 주가 → 2024년 재무데이터 사용 (전년도 기준)
     * - 2024년 S-RIM = 2024년 유통주식수, 2023년 자기자본, 2023/2022/2021 ROE 가중평균
     *
     * 따라서 Map의 키는 "주가 연도"이고, 값은 "전년도 재무데이터로 계산한 S-RIM"
     */
    private Map<LocalDate, SrimResultDto> calculateSrimForDates(
            Long companyId,
            List<StockPrice> stockPrices
    ) {
        List<LocalDate> dates = stockPrices.stream()
                .map(StockPrice::getTradeDate)
                .toList();


        LocalDate start = dates.get(0);
        LocalDate end = dates.get(dates.size() - 1);

        Map<LocalDate, BigDecimal> keByDate =
                buildKeByDate(DEFAULT_RATING, DEFAULT_TENOR_MONTHS, start, end, dates);

        Map<Integer, YearBaseData> baseDataByYear = new HashMap<>();
        Map<LocalDate, SrimResultDto> srimByDate = new LinkedHashMap<>();

        for(LocalDate date : dates) {
            int financialYear = date.getYear() - 1;

            BigDecimal ke = keByDate.get(date);
            if(ke == null) {
                srimByDate.put(date, null);
                continue;
            }

            YearBaseData base = baseDataByYear.get(financialYear);
            if (base == null && !baseDataByYear.containsKey(financialYear)) {
                base = loadYearBaseDataWithFallback(companyId, financialYear);
                baseDataByYear.put(financialYear, base);
            }
            if (base == null) {
                srimByDate.put(date, null);
                continue;
            }

            SrimResultDto srim = srimService.calculateFromBaseData(
                    base.getSharesOutstanding(),
                    base.getRoe(),
                    base.getEquityOwner(),
                    ke,
                    base.getYear(),
                    DEFAULT_RATING,
                    (int) DEFAULT_TENOR_MONTHS
            );
            srimByDate.put(date, srim);


        }

        return srimByDate;
    }
    private YearBaseData loadYearBaseDataWithFallback(Long companyId, int financialYear) {
        YearBaseData currentYearBaseData = tryLoadYearBaseData(companyId, financialYear);
        if (currentYearBaseData != null) {
            return currentYearBaseData;
        }

        int fallbackYear = financialYear - 1;
        if (fallbackYear <= 0) {
            return null;
        }

        YearBaseData fallbackBaseData = tryLoadYearBaseData(companyId, fallbackYear);
        if (fallbackBaseData != null) {
            log.info("가격 차트 S-RIM 기준연도 fallback 사용: requestYear={}, actualYear={}",
                    financialYear, fallbackYear);
        }
        return fallbackBaseData;
    }

    private YearBaseData tryLoadYearBaseData(Long companyId, int financialYear) {
        try {
            return loadYearBaseData(companyId, financialYear);
        } catch (Exception e) {
            log.warn("연도 기본데이터 로드 실패 (financialYear={}): {}", financialYear, e.getMessage());
            return null;
        }
    }

    private YearBaseData loadYearBaseData(Long companyId, int financialYear) {

        // 1) 유통주식수(연도 기준)
        Long sharesOutstanding = srimService.getShareOutStanding(companyId, financialYear, SrimService.SE);

        // 2) ROE 가중평균(연도 기준, 비율로 반환되도록 이미 변환해둠: 0.xx)
        BigDecimal roe = srimService.calculateWeightedAverageRoe(companyId, financialYear);

        // 3) 지배주주지분(연도 기준)
        BigDecimal equityOwner = srimService.getEquityOwner(companyId, financialYear);

        return new YearBaseData(financialYear, sharesOutstanding, roe, equityOwner);
    }

    private Map<LocalDate, BigDecimal> buildKeByDate(
            String rating,
            Short tenorMonths,
            LocalDate startDate,
            LocalDate endDate,
            List<LocalDate> tradeDates
    ) {
        BigDecimal lastYield = bondYieldCurveRepository
                .findFirstByRatingAndTenorMonthsAndAsOfLessThanEqualOrderByAsOfDesc(rating, tenorMonths, startDate)
                .map(BondYieldCurve::getYieldRate)
                .orElse(null);

        List<BondYieldCurve> curves = bondYieldCurveRepository
                .findByRatingAndTenorMonthsAndAsOfBetweenOrderByAsOfAsc(rating, tenorMonths, startDate, endDate);

        int idx = 0;
        Map<LocalDate, BigDecimal> keByDate = new HashMap<>(tradeDates.size());

        for(LocalDate d : tradeDates) {
            while(idx < curves.size() && !curves.get(idx).getAsOf().isAfter(d)) {
                lastYield = curves.get(idx++).getYieldRate();
            }
            keByDate.put(d, lastYield);
        }

        return keByDate;
    }
    /**
     * StockPrice 엔티티 → PriceData DTO 변환
     *
     * 2025년 1월 15일 주가 → 2025년 키로 조회 → 2024년 재무데이터로 계산된 S-RIM 사용
     */
    private StockPriceDto.PriceData convertToPriceData(StockPrice stockPrice,
                                                       Map<LocalDate, SrimResultDto> srimByDate,
                                                       Map<Integer, BigDecimal> epsByYear) {

        // tradeDate 사용
        LocalDate tradeDate = stockPrice.getTradeDate();

        SrimResultDto srim = srimByDate.get(tradeDate);

        // S-RIM 데이터 추출 (없으면 null)
        BigDecimal fv0 = null, fv10 = null, fv20 = null, fv30 = null, fv50 = null;

        if (srim != null && srim.getScenarios() != null && !srim.getScenarios().isEmpty()) {
            List<SrimResultDto.ScenarioResult> scenarios = srim.getScenarios();

            // 시나리오 순서: 0%, -10%, -20%, -30%, -50%
            if (scenarios.size() >= 1) fv0 = scenarios.get(0).getFairValuePerShare();
            if (scenarios.size() >= 2) fv10 = scenarios.get(1).getFairValuePerShare();
            if (scenarios.size() >= 3) fv20 = scenarios.get(2).getFairValuePerShare();
            if (scenarios.size() >= 4) fv30 = scenarios.get(3).getFairValuePerShare();
            if (scenarios.size() >= 5) fv50 = scenarios.get(4).getFairValuePerShare();
        } else {
            log.debug("주가에 대한 S-RIM 데이터 없음 (date: {})", tradeDate);
        }


        BigDecimal per = null;
        BigDecimal close = stockPrice.getPrice();
        if (close != null) {
            BigDecimal eps = epsByYear.get(tradeDate.getYear() - 1);
            if (eps != null && eps.compareTo(BigDecimal.ZERO) != 0) {
                per = close.divide(eps, 2, RoundingMode.HALF_UP);
            }
        }

        StockPriceDto.PriceData.PriceDataBuilder builder =
                StockPriceDto.PriceData.builder()
                        .date(tradeDate)
                        .open(stockPrice.getOpenPrice())
                        .high(stockPrice.getHighPrice())
                        .low(stockPrice.getLowPrice())
                        .close(close)        // 종가
                        .volume(stockPrice.getVolume())
                        .per(per);
        // S-RIM 계산 실패 또는 데이터 없음 -> fv 필드는 설정하지 않음(null)
        if (srim == null) {
            log.debug("S-RIM 데이터 없음 (date={})", tradeDate);
            return builder.build();
        }
        try {
            return builder
                    .fvScenario0(fv0)
                    .fvScenario10(fv10)
                    .fvScenario20(fv20)
                    .fvScenario30(fv30)
                    .fvScenario50(fv50)
                    .build();

        } catch (Exception e) {
            // NPE 등 예외 발생 시에도 OHLCV만 리턴
            log.warn("S-RIM 값 매핑 중 오류 발생 (date={}): {}", tradeDate, e.getMessage());
            return builder.build();
        }
    }

    private Map<Integer, BigDecimal> buildEpsByYear(Long companyId, List<StockPrice> stockPrices) {
        if (stockPrices.isEmpty()) return Collections.emptyMap();

        LocalDate firstDate = stockPrices.get(0).getTradeDate();
        LocalDate lastDate = stockPrices.get(stockPrices.size() - 1).getTradeDate();
        if (firstDate == null || lastDate == null) return Collections.emptyMap();
        int minYear = firstDate.getYear() - 1;
        int maxYear = lastDate.getYear() - 1;

        List<FinPeriod> yearlyPeriods = finPeriodRepository.findYearlyPeriods(companyId);
        Map<Integer, FinPeriod> periodByYear = new HashMap<>();
        for (FinPeriod period : yearlyPeriods) {
            Integer year = period.getFiscalYear();
            if (year == null) continue;
            periodByYear.putIfAbsent(year, period);
        }

        Integer minPeriodYear = periodByYear.keySet().stream().min(Integer::compareTo).orElse(null);
        if (minPeriodYear == null) return Collections.emptyMap();

        int startYear = Math.max(minYear, minPeriodYear);
        List<Long> periodIds = new ArrayList<>();
        for (int year = startYear; year <= maxYear; year++) {
            FinPeriod period = periodByYear.get(year);
            if (period == null || period.getPeriodId() == null) continue;
            periodIds.add(period.getPeriodId());
        }

        if (periodIds.isEmpty()) return Collections.emptyMap();

        List<FinMetricValue> epsValues = finMetricValueRepository
                .findByCompanyIdAndPeriodIdsAndMetricCode(companyId, periodIds, METRIC_EPS);
        Map<Long, BigDecimal> epsByPeriodId = new HashMap<>();
        for (FinMetricValue v : epsValues) {
            Long pid = v.getPeriod() != null ? v.getPeriod().getPeriodId() : null;
            if (pid == null) continue;
            epsByPeriodId.put(pid, v.getValueNum());
        }

        Map<Integer, BigDecimal> epsByYear = new HashMap<>();
        for (int year = minYear; year <= maxYear; year++) {
            BigDecimal eps = null;
            for (int y = year; y >= minPeriodYear; y--) {
                FinPeriod period = periodByYear.get(y);
                if (period == null || period.getPeriodId() == null) continue;
                BigDecimal value = epsByPeriodId.get(period.getPeriodId());
                if (value != null && value.compareTo(BigDecimal.ZERO) != 0) {
                    eps = value;
                    break;
                }
            }
            if (eps == null) continue;
            epsByYear.put(year, eps);
        }

        return epsByYear;
    }
}
