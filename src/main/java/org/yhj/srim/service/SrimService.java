package org.yhj.srim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CommonErrorCode;
import org.yhj.srim.common.exception.code.FinancialErrorCode;
import org.yhj.srim.common.exception.code.StockErrorCode;
import org.yhj.srim.repository.*;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.FinMetricValue;
import org.yhj.srim.repository.entity.FinPeriod;
import org.yhj.srim.repository.entity.StockShareStatus;
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

    // 기본 설정값
    private static final String METRIC_TOTAL_EQUITY_OWNER = "TOTAL_EQUITY_OWNER";
    private static final String DEFAULT_RATING = "BBB-";
    private static final short DEFAULT_TENOR_MONTHS = 60;
    private static final int DEFAULT_SCALE = 2;
    private static final String SE = "보통주";
    
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
     * 
     * @param companyId 회사 ID
     * @param basis 기준 (YEAR/QTR)
     * @param rating 신용등급 (기본 BBB-)
     * @param tenorMonths 만기 (기본 60개월)
     * @return S-RIM 계산 결과
     */
    public SrimResultDto calculate(Long companyId, String basis,Integer year, String rating, Integer tenorMonths) {
        log.debug("S-RIM 계산 시작: companyId={}, basis={}, year={}, rating={}, tenor={}",
                companyId, basis, year, rating, tenorMonths);

        // 기본값 설정
        if (rating == null) rating = DEFAULT_RATING;
        if (tenorMonths == null) tenorMonths = (int) DEFAULT_TENOR_MONTHS;
        if (basis == null) basis = "YEAR";

        // 1. 연도별 주식 수 조회,
        int baseYear = year == null ? LocalDate.now().getYear() -1 : year;
        log.debug("기준연도 : year = {}", baseYear);
        Long sharesOutStanding = getShareOutStanding(companyId, baseYear, SE);
        log.debug("{}연도 유통주식수 : {}",baseYear, sharesOutStanding);


        // 2. ROE 가중평균 계산 (최근 3개, 가중치 3:2:1)
        BigDecimal roe = calculateWeightedAverageRoe(companyId, baseYear , basis);
        log.debug("ROE: {}", roe);

        // 3. 연도 기준 지배주주지분 조회
        BigDecimal equityOwner = getEquityOwner(companyId, baseYear);
        log.debug("자기자본(지배주주지분) : {}", equityOwner);

        // 4. Ke (할인율) 조회 - 회사채 수익률
        BigDecimal ke = getDiscountRate(rating, tenorMonths.shortValue());
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
                    .append("\n basis            : ").append(basis)
                    .append("\n year             : ").append(baseYear)
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
                .basis(basis)
                .rating(rating)
                .tenorMonths(tenorMonths)
                .year(baseYear)
                .equity(equityOwner)
                .roe(roe)
                .ke(ke)
                .sharesOutstanding(sharesOutStanding)
                .scenarios(scenarioResults)
                .build();
    }

    // to-do ErrorCode 수정
    private BigDecimal getEquityOwner(Long companyId, int baseYear) {
        FinMetricValue value = finMetricValueRepository
                .findYearlyMetricWithFetch(companyId, baseYear, METRIC_TOTAL_EQUITY_OWNER)
                .orElseThrow(() -> new CustomException(CommonErrorCode.INVALID_INPUT));

        return value.getValueNum();
    }

    private Long getShareOutStanding(Long companyId, int baseYear, String se) {
        log.debug("companyId : {}, baseYear : {}, basis : {}", companyId, baseYear, se);
        Optional<StockShareStatus> status = stockShareStatusRepository
                .findByCompany_CompanyIdAndBsnsYearAndSe(companyId, baseYear, se);
        log.debug(" {}년도 주식 현황 : {}",baseYear, status);
        return status.get().getDistbStockCo();
    }

    /**
     * ROE 가중평균 계산 (최근 3개, 가중치 3:2:1)
     */
    private BigDecimal calculateWeightedAverageRoe(Long companyId, int baseYear, String basis) {
        List<FinPeriod> periods;
        
        if ("YEAR".equals(basis)) {
            periods = finPeriodRepository.findRecentYearlyPeriods(companyId, baseYear, 3);
        } else {
            periods = finPeriodRepository.findRecentQuarterlyPeriods(companyId, 3);
        }
        
        if (periods.size() < 3) {
            throw new IllegalArgumentException("ROE 계산에 필요한 데이터가 부족합니다. (최소 3개 필요)");
        }
        
        // 최근 3개의 ROE 값 조회
        List<BigDecimal> roeValues = new ArrayList<>();
        int idx = 0;
        for (FinPeriod period : periods) {
            BigDecimal roe = finMetricValueRepository
                    .findByCompanyIdAndPeriodAndMetricCode(companyId, period, "ROE")
                    .map(FinMetricValue::getValueNum)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "ROE 데이터가 없습니다. period: " + period.getLabel()));

            log.debug("YEAR : {}, ROE: {} (idx={})", period.getFiscalYear(), roe, idx++);
            roeValues.add(roe);
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

        return avgRoeRatio;
    }

    /**
     * 최근 BPS 조회
     */
    private BigDecimal getRecentBps(Long companyId, String basis) {
        List<FinPeriod> periods;
        
        if ("YEAR".equals(basis)) {
            periods = finPeriodRepository.findRecentYearlyPeriods(companyId,0, 1);
        } else {
            periods = finPeriodRepository.findRecentQuarterlyPeriods(companyId, 1);
        }
        
        if (periods.isEmpty()) {
            throw new IllegalArgumentException("BPS 데이터가 없습니다.");
        }
        
        return finMetricValueRepository
                .findByCompanyIdAndPeriodAndMetricCode(companyId, periods.get(0), "BPS")
                .map(FinMetricValue::getValueNum)
                .orElseThrow(() -> new IllegalArgumentException("BPS 데이터가 없습니다."));
    }

    /**
     * 할인율(Ke) 조회 - 회사채 수익률
     */
    private BigDecimal getDiscountRate(String rating, Short tenorMonths) {
        return bondYieldCurveRepository.findFirstByRatingAndTenorMonthsOrderByAsOfDesc(rating, tenorMonths)
                .map(bond -> bond.getYieldRate())
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("회사채 수익률 데이터가 없습니다. (rating=%s, tenor=%d)", rating, tenorMonths)
                ));
    }
}
