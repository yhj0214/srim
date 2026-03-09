package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.SrimError;
import org.yhj.srim.repository.*;
import org.yhj.srim.repository.entity.*;
import org.yhj.srim.service.dto.SrimCalculateCommand;
import org.yhj.srim.service.dto.SrimResultDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class SrimService {

    private final CompanyRepository companyRepository;
    private final FinPeriodRepository finPeriodRepository;
    private final FinMetricValueRepository finMetricValueRepository;
    private final BondYieldCurveRepository bondYieldCurveRepository;
    private final StockPriceRepository stockPriceRepository;

    // 기본 설정값
    private static final String METRIC_TOTAL_EQUITY_OWNER = "TOTAL_EQUITY_OWNER";
    private static final String DEFAULT_RATING = "BBB-";
    private static final short DEFAULT_TENOR_MONTHS = 60;
    private static final int DEFAULT_SCALE = 2;
    public static final String SE = "보통주";

    // 감소율 시나리오
    private static final BigDecimal[] REDUCTION_RATES = {
            BigDecimal.ZERO,
            new BigDecimal("-0.10"),
            new BigDecimal("-0.20"),
            new BigDecimal("-0.30"),
            new BigDecimal("-0.50")
    };
    private final StockShareStatusRepository stockShareStatusRepository;

    /**
     * S-RIM 계산
     */
    public SrimResultDto calculate(SrimCalculateCommand command) {
        if (command == null) {
            throw new CustomException(SrimError.INVALID_REQUEST, "S-RIM 계산 요청이 비어있습니다.");
        }

        Long companyId = command.getCompanyId();
        Integer requestedYear = command.getYear();
        String rating = command.getRating();
        Integer tenorMonths = command.getTenor();
        LocalDate asOf = command.getAsOf();

        log.debug("S-RIM 계산 시작: companyId={}, year={}, rating={}, tenor={}",
                companyId, requestedYear, rating, tenorMonths);

        // 기본값 설정
        if (rating == null) rating = DEFAULT_RATING;
        if (tenorMonths == null) tenorMonths = (int) DEFAULT_TENOR_MONTHS;
        if (asOf == null) asOf = LocalDate.now();

        int effectiveYear = requestedYear != null
                ? requestedYear : resolveEffectiveFiscalYear(companyId, asOf);
        log.debug("계산 기준연도 : requestedYear={}, effectiveYear={}, asOf={}",
                requestedYear, effectiveYear, asOf);

        // 1. 연도별 주식 수 조회
        Long sharesOutStanding = getShareOutStanding(companyId, effectiveYear, SE);
        log.debug("{}연도 유통주식수 : {}", effectiveYear, sharesOutStanding);


        // 2. ROE 가중평균 계산 (최근 3개, 가중치 3:2:1)
        RoeSummary roeSummary = calculateWeightedAverageRoeSummary(companyId, effectiveYear);
        BigDecimal roe = roeSummary.avgRoeRatio();
        log.debug("ROE: {}", roe);

        // 3. 연도 기준 지배주주지분 조회
        BigDecimal equityOwner = getEquityOwner(companyId, effectiveYear);
        log.debug("자기자본(지배주주지분) : {}", equityOwner);

        // 4. Ke (할인율) 조회 - 회사채 수익률
        BigDecimal ke = getDiscountRate(rating, tenorMonths.shortValue(), asOf);
        log.debug("Ke: {}", ke);

        // 5. 기본 초과이익 계산 (Equity * (ROE-ke))
        BigDecimal baseExcessEarnings = equityOwner.multiply(roe.subtract(ke));
        log.debug("자기자본값 : {}", equityOwner);
        log.debug("평균ROE : {}", roe);
        log.debug("할인율 : {}", ke);
        log.debug("기본 초과이익, 감소율 0 : {}", baseExcessEarnings);

        // 6. 초과이익 감소 시나리오별 계산
        List<SrimResultDto.ScenarioResult> scenarioResults = new ArrayList<>();

        for(BigDecimal reductionRate : REDUCTION_RATES) {

            BigDecimal adjustedExcessEarnings = baseExcessEarnings
                    .multiply(BigDecimal.ONE.add(reductionRate));

            // 기업 가치 = Equity + (초과이익 / Ke)
            BigDecimal enterpriseValue = equityOwner.add(
                    adjustedExcessEarnings.divide(ke, 10, RoundingMode.HALF_UP)
            );

            // 적정주가 = 기업주가 / 유통주식수
            BigDecimal fairValuePerShare = enterpriseValue.divide(
                    new BigDecimal(sharesOutStanding),
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

        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n=== S-RIM 계산 결과 ===")
                    .append("\n companyId        : ").append(companyId)
                    .append("\n year             : ").append(effectiveYear)
                    .append("\n rating           : ").append(rating)
                    .append("\n tenorMonths      : ").append(tenorMonths)
                    .append("\n sharesOutstanding: ").append(sharesOutStanding)
                    .append("\n equityOwner      : ").append(equityOwner)
                    .append("\n roe              : ").append(roe)
                    .append("\n ke               : ").append(ke)
                    .append("\n baseExcessEarn   : ").append(baseExcessEarnings)
                    .append("\n --- 시나리오별 결과 ---");

            for (SrimResultDto.ScenarioResult s : scenarioResults) {
                sb.append("\n  · reductionRate=").append(s.getReductionRate())
                        .append(", excessEarnings=").append(s.getExcessEarnings())
                        .append(", enterpriseValue=").append(s.getEnterpriseValue())
                        .append(", fairValuePerShare=").append(s.getFairValuePerShare());
            }

            sb.append("\n=========================\n");
            log.debug(sb.toString());
        }

        return SrimResultDto.builder()
                .rating(rating)
                .tenorMonths(tenorMonths)
                .year(effectiveYear)
                .equity(equityOwner)
                .roe(roe)
                .roePercent(roeSummary.avgRoePercent())
                .ke(ke)
                .sharesOutstanding(sharesOutStanding)
                .currentPrice(resolveCurrentPrice(companyId))
                .currentPriceDate(resolveCurrentPriceDate(companyId))
                .roeDetails(roeSummary.details())
                .scenarios(scenarioResults)
                .build();
    }

    public SrimResultDto calculateFromBaseData(
            Long sharesOutstanding,
            BigDecimal roe,
            BigDecimal equityOwner,
            BigDecimal ke,
            int financialYear,
            String rating,
            Integer tenorMonths
    ) {
        if (ke == null || ke.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(SrimError.INVALID_DISCOUNT_RATE, "ke=" + ke);
        }
        if (rating == null) rating = DEFAULT_RATING;
        if (tenorMonths == null) tenorMonths = (int) DEFAULT_TENOR_MONTHS;

        BigDecimal baseExcessEarnings = equityOwner.multiply(roe.subtract(ke));

        List<SrimResultDto.ScenarioResult> scenarioResults = new ArrayList<>();

        for (BigDecimal reductionRate : REDUCTION_RATES) {
            BigDecimal adjustedExcessEarnings =
                    baseExcessEarnings.multiply(BigDecimal.ONE.add(reductionRate));

            BigDecimal enterpriseValue = equityOwner.add(
                    adjustedExcessEarnings.divide(ke, 10, RoundingMode.HALF_UP)
            );

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

        BigDecimal roePercent = roe != null
                ? roe.multiply(BigDecimal.valueOf(100))
                : null;

        return SrimResultDto.builder()
                .rating(rating)
                .tenorMonths(tenorMonths)
                .year(financialYear)
                .equity(equityOwner)
                .roe(roe)
                .roePercent(roePercent)
                .ke(ke)
                .sharesOutstanding(sharesOutstanding)
                .scenarios(scenarioResults)
                .build();
    }

    private int resolveEffectiveFiscalYear(Long companyId, LocalDate asOf) {

        int previousYear = asOf.getYear() - 1;
        if (hasSufficientYearlyRoeData(companyId, previousYear)) {
            return previousYear;
        }

        int yearBeforePrevious = previousYear - 1;
        if (hasSufficientYearlyRoeData(companyId, yearBeforePrevious)) {
            return yearBeforePrevious;
        }

        throw new CustomException(
                SrimError.INSUFFICIENT_ROE_DATA,
                "companyId=" + companyId + ", asOf=" + asOf
        );
    }

    private boolean hasSufficientYearlyRoeData(Long companyId, int baseYear) {
        if (baseYear <= 0) {
            return false;
        }

        List<FinPeriod> periods = finPeriodRepository.findRecentYearlyPeriods(companyId, baseYear, 3);
        if (periods.size() < 3) {
            return false;
        }

        for (FinPeriod period : periods) {
            boolean hasRoe = finMetricValueRepository
                    .findByCompanyIdAndPeriodAndMetricCode(companyId, period, "ROE")
                    .isPresent();
            if (!hasRoe) {
                return false;
            }
        }

        return true;
    }

    // to-do ErrorCode 수정
    public BigDecimal getEquityOwner(Long companyId, int baseYear) {
        FinMetricValue v = finMetricValueRepository
                .findTopByCompanyIdAndMetricCodeAndPeriod_PeriodTypeAndPeriod_IsEstimateAndPeriod_FiscalYearLessThanEqualOrderByPeriod_FiscalYearDesc(
                        companyId, METRIC_TOTAL_EQUITY_OWNER, "YEAR", false, baseYear
                )
                .orElseThrow(() -> new CustomException(SrimError.EQUITY_OWNER_NOT_FOUND));

        Integer actualYear = v.getPeriod() == null ? null : v.getPeriod().getFiscalYear();
        if (actualYear != null && actualYear != baseYear) {
            log.info("지배주주지분 fallback 사용: companyId={}, requestYear={}, actualYear={}",
                    companyId, baseYear, actualYear);
        }

        return v.getValueNum();
    }

    public Long getShareOutStanding(Long companyId, int baseYear, String se) {
        log.debug("companyId : {}, baseYear : {}, se : {}", companyId, baseYear, se);

        // 해당연도 이하 가장 최신데이터
        StockShareStatus s =
                stockShareStatusRepository
                        .findTopByCompany_CompanyIdAndSeAndBsnsYearLessThanEqualOrderByBsnsYearDesc(companyId, se, baseYear)
                        .orElseThrow(() -> new CustomException(SrimError.SHARES_OUTSTANDING_NOT_FOUND));


        log.info("유통주식수 fallback 사용: companyId={}, requestYear={}, actualYear={}",
                companyId, baseYear, s.getBsnsYear());

        return s.getDistbStockCo();
    }

    /**
     * ROE 가중평균 계산 (최근 3개, 가중치 3:2:1)
     */
    public BigDecimal calculateWeightedAverageRoe(Long companyId, int baseYear) {
        return calculateWeightedAverageRoeSummary(companyId, baseYear).avgRoeRatio();
    }

    private RoeSummary calculateWeightedAverageRoeSummary(Long companyId, int baseYear) {
        List<FinPeriod> periods;

        periods = finPeriodRepository.findRecentYearlyPeriods(companyId, baseYear, 3);

        if (periods.size() < 3) {
            throw new CustomException(SrimError.INSUFFICIENT_ROE_DATA);
        }

        // 최근 3개의 ROE 값 조회
        List<BigDecimal> roeValues = new ArrayList<>();
        List<SrimResultDto.RoeDetail> details = new ArrayList<>();
        int idx = 0;
        for (FinPeriod period : periods) {
            BigDecimal roe = finMetricValueRepository
                    .findByCompanyIdAndPeriodAndMetricCode(companyId, period, "ROE")
                    .map(FinMetricValue::getValueNum)
                    .orElseThrow(() -> new CustomException(
                            SrimError.ROE_NOT_FOUND,
                            "period=" + period.getLabel()
                    ));
            BigDecimal equityOwner = finMetricValueRepository
                    .findByCompanyIdAndPeriodAndMetricCode(companyId, period, METRIC_TOTAL_EQUITY_OWNER)
                    .map(FinMetricValue::getValueNum)
                    .orElse(null);

            log.debug("YEAR : {}, ROE: {} (idx={})", period.getFiscalYear(), roe, idx++);
            roeValues.add(roe);
            details.add(SrimResultDto.RoeDetail.builder()
                    .fiscalYear(period.getFiscalYear())
                    .roePercent(roe)
                    .equityOwner(equityOwner)
                    .build());
        }

        // 가중평균 계산 (최신 = 3, 두번째 = 2, 세번째 = 1)
        BigDecimal weightedSum = roeValues.get(0).multiply(new BigDecimal("3"))
                .add(roeValues.get(1).multiply(new BigDecimal("2")))
                .add(roeValues.get(2).multiply(BigDecimal.ONE));

        BigDecimal totalWeight = new BigDecimal("6"); // 3 + 2 + 1

        // ROE퍼센트
        BigDecimal avgRoePercent = weightedSum.divide(totalWeight, 10, RoundingMode.HALF_UP);

        // 비율 변환 후 리턴
        BigDecimal avgRoeRatio = avgRoePercent
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

        return new RoeSummary(avgRoeRatio, avgRoePercent, details);
    }

    private record RoeSummary(
            BigDecimal avgRoeRatio,
            BigDecimal avgRoePercent,
            List<SrimResultDto.RoeDetail> details
    ) {}

    private BigDecimal resolveCurrentPrice(Long companyId) {
        return stockPriceRepository.findTopByCompany_CompanyIdOrderByTradeDateDesc(companyId)
                .map(StockPrice::getPrice)
                .orElse(null);
    }

    private String resolveCurrentPriceDate(Long companyId) {
        return stockPriceRepository.findTopByCompany_CompanyIdOrderByTradeDateDesc(companyId)
                .map(p -> p.getTradeDate() != null ? p.getTradeDate().toString() : null)
                .orElse(null);
    }

    /**
     * 할인율(Ke) 조회 - 회사채 수익률
     */
    private BigDecimal getDiscountRate(String rating, Short tenorMonths, LocalDate date) {
        return bondYieldCurveRepository.findFirstByRatingAndTenorMonthsAndAsOfLessThanEqualOrderByAsOfDesc(
                        rating, tenorMonths, date
                ).map(BondYieldCurve::getYieldRate)
                .orElseThrow(() -> new CustomException(SrimError.BOND_YIELD_NOT_FOUND));
    }
}
