package org.yhj.srim.service.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.service.facade.dto.YearBaseData;
import org.yhj.srim.repository.BondYieldCurveRepository;
import org.yhj.srim.repository.StockPriceRepository;
import org.yhj.srim.repository.entity.BondYieldCurve;
import org.yhj.srim.repository.entity.StockPrice;
import org.yhj.srim.service.crawl.CrawlingService;
import org.yhj.srim.service.domain.SrimService;
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
    private static final String DEFAULT_RATING = "BBB-";
    private static final Short DEFAULT_TENOR_MONTHS = 60;
    private static final String METRIC_TOTAL_EQUITY_OWNER = "TOTAL_EQUITY_OWNER";
    private static final int DEFAULT_SCALE = 2;
    
    private final StockPriceRepository stockPriceRepository;
    private final BondYieldCurveRepository bondYieldCurveRepository;
    private final CrawlingService crawlingService;
    private final SrimService srimService;

    private static final BigDecimal[] REDUCTION_RATES = {
            BigDecimal.ZERO,
            new BigDecimal("-0.10"),
            new BigDecimal("-0.20"),
            new BigDecimal("-0.30"),
            new BigDecimal("-0.50")
    };

    /**
     * 주가 차트 데이터 조회 (주가 + S-RIM 적정주가)
     * 
     * @param companyId 회사 ID
     * @return 주가 차트 데이터
     */
    public StockPriceDto getPriceChart(Long companyId, LocalDate startDate, LocalDate endDate) {

        LocalDate end = (endDate != null) ? endDate : LocalDate.now();
        LocalDate start = (startDate != null) ? startDate : end.minusYears(1);

        LocalDate hardMin = end.minusYears(INITIAL_BACKFILL_YEARS);
        if (start.isBefore(hardMin)) start = hardMin;

        LocalDate minTradeDate = stockPriceRepository.findMinTradeDateByCompany(companyId);
        LocalDate maxTradeDate = stockPriceRepository.findMaxTradeDateByCompany(companyId);

        if (start.isBefore(minTradeDate)) start = minTradeDate;
        if (end.isAfter(maxTradeDate)) end = maxTradeDate;

        if (start.isAfter(end)) {
            return StockPriceDto.builder().priceData(List.of()).build();
        }

        if (minTradeDate == null || maxTradeDate == null) {
            return StockPriceDto.builder().priceData(List.of()).build();
        }

        // 3. DB에서 주가 데이터 조회 (tradeDate 기준)
        List<StockPrice> stockPrices = stockPriceRepository.
                findByCompany_companyIdOrderByTradeDateAsc(companyId);
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

        // 5. DTO 변환 (주가 + 적정주가 결합)
        List<StockPriceDto.PriceData> priceDataList =
                stockPrices.stream()
                        .map(sp -> convertToPriceData(sp, srimByDate))
                        .toList();

        log.info("PriceChart 조회 완료 - 응답 데이터 개수: {}", priceDataList.size());

        return StockPriceDto.builder()
                .priceData(priceDataList)
                .build();
    }

    /**
     * 주가 데이터 확보
     * - 첫 조회 시: 최근 10년치 등록
     * - 이후: 부족한 구간만 크롤링
     */
    public void ensurePriceData(Long companyId, LocalDate start, LocalDate end) {
        boolean hasData = stockPriceRepository.existsByCompany_CompanyId(companyId);

        if (!hasData) {
            // 최초 조회인 경우 end 기준 과거 10년 백필
            LocalDate backfillStart = end.minusYears(INITIAL_BACKFILL_YEARS);
            log.info("주가 데이터 최초 조회. companyId={} → {} ~ {} ({}년치) 백필 크롤링",
                    companyId, backfillStart, end, INITIAL_BACKFILL_YEARS);

            try {
                crawlingService.crawlingStockPrice(companyId, backfillStart, end);
            } catch (Exception e) {
                log.error("주가 크롤링 실패(최초 백필): {}", e.getMessage(), e);
                // 실패해도 이후 DB 조회는 진행 (혹시 다른 경로로 적재된 데이터가 있을 수 있음)
            }
            return;
        }

        // 기존 데이터가 있는 경우 - 부족한 구간 체크
        LocalDate dbStart = stockPriceRepository.findMinTradeDateByCompany(companyId);
        LocalDate dbEnd = stockPriceRepository.findMaxTradeDateByCompany(companyId);

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
                crawlingService.crawlingStockPrice(companyId, start, crawlEnd);
            } catch (Exception e) {
                log.error("앞부분 주가 크롤링 실패: {}", e.getMessage(), e);
            }
        }

        // 뒷부분 부족 시 크롤링
        if (end.isAfter(dbEnd)) {
            LocalDate crawlStart = dbEnd.plusDays(1);
            log.info("뒷부분 주가 데이터 크롤링 필요: {} ~ {}", crawlStart, end);
            try {
                crawlingService.crawlingStockPrice(companyId, crawlStart, end);
            } catch (Exception e) {
                log.error("뒷부분 주가 크롤링 실패: {}", e.getMessage(), e);
            }
        }

        if (!start.isBefore(dbStart) && !end.isAfter(dbEnd)) {
            log.info("주가 데이터 크롤링 불필요 - DB에 충분한 데이터 존재.");
        }
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
                // 아직 시도 안 한 연도
                try {
                    base = loadYearBaseData(companyId, financialYear);
                    baseDataByYear.put(financialYear, base);
                } catch (Exception e) {
                    log.warn("연도 기본데이터 로드 실패 (financialYear={}): {}", financialYear, e.getMessage());
                    baseDataByYear.put(financialYear, null); // 실패 캐시
                    srimByDate.put(date, null);
                    continue;
                }
            }
            if (base == null) {
                srimByDate.put(date, null);
                continue;
            }

            SrimResultDto srim = calculateSrimFromBaseAndKe(base, ke, financialYear);
            srimByDate.put(date, srim);


        }

        return srimByDate;
    }
    private YearBaseData loadYearBaseData(Long companyId, int financialYear) {

        // 1) 유통주식수(연도 기준)
        Long sharesOutstanding = srimService.getShareOutStanding(companyId, financialYear, SrimService.SE);

        // 2) ROE 가중평균(연도 기준, 비율로 반환되도록 이미 변환해둠: 0.xx)
        BigDecimal roe = srimService.calculateWeightedAverageRoe(companyId, financialYear, "YEAR");

        // 3) 지배주주지분(연도 기준)
        BigDecimal equityOwner = srimService.getEquityOwner(companyId, financialYear);

        return new YearBaseData(financialYear, sharesOutstanding, roe, equityOwner);
    }

    private SrimResultDto calculateSrimFromBaseAndKe(YearBaseData base, BigDecimal ke, int financialYear) {

        // 안전장치 (ke=0이면 divide-by-zero)
        if (ke == null || ke.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Ke(할인율)가 유효하지 않습니다. ke=" + ke);
        }

        Long sharesOutstanding = base.getSharesOutstanding();
        BigDecimal roe = base.getRoe();
        BigDecimal equityOwner = base.getEquityOwner();

        // 기본 초과이익 = Equity * (ROE - Ke)
        BigDecimal baseExcessEarnings = equityOwner.multiply(roe.subtract(ke));

        List<SrimResultDto.ScenarioResult> scenarioResults = new ArrayList<>();

        for (BigDecimal reductionRate : REDUCTION_RATES) {
            // adjustedExcess = baseExcess * (1 + reductionRate)
            BigDecimal adjustedExcessEarnings =
                    baseExcessEarnings.multiply(BigDecimal.ONE.add(reductionRate));

            // 기업가치 = Equity + (adjustedExcess / Ke)
            BigDecimal enterpriseValue = equityOwner.add(
                    adjustedExcessEarnings.divide(ke, 10, RoundingMode.HALF_UP)
            );

            // 적정주가 = enterpriseValue / sharesOutstanding
            BigDecimal fairValuePerShare = enterpriseValue.divide(
                    BigDecimal.valueOf(sharesOutstanding),
                    DEFAULT_SCALE,
                    RoundingMode.HALF_UP
            );

            scenarioResults.add(SrimResultDto.ScenarioResult.builder()
                    .reductionRate(reductionRate)
                    .excessEarnings(adjustedExcessEarnings.setScale(0, RoundingMode.HALF_UP))
                    .enterpriseValue(enterpriseValue.setScale(0, RoundingMode.HALF_UP))
                    .fairValuePerShare(fairValuePerShare)
                    .build());
        }

        return SrimResultDto.builder()
                .basis("YEAR")
                .rating(DEFAULT_RATING)
                .tenorMonths((int) DEFAULT_TENOR_MONTHS)   // SrimResultDto 타입이 Integer라면 그대로
                .year(financialYear)
                .equity(equityOwner)
                .roe(roe)
                .ke(ke)
                .sharesOutstanding(sharesOutstanding)
                .scenarios(scenarioResults)
                .build();
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
                                                       Map<LocalDate, SrimResultDto> srimByDate) {

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


        StockPriceDto.PriceData.PriceDataBuilder builder =
                StockPriceDto.PriceData.builder()
                        .date(tradeDate)
                        .open(stockPrice.getOpenPrice())
                        .high(stockPrice.getHighPrice())
                        .low(stockPrice.getLowPrice())
                        .close(stockPrice.getPrice())        // 종가
                        .volume(stockPrice.getVolume());
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
}
