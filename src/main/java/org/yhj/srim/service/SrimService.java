package org.yhj.srim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.repository.*;
import org.yhj.srim.repository.entity.FinMetricValue;
import org.yhj.srim.repository.entity.FinPeriod;
import org.yhj.srim.service.dto.SrimResultDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

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
    private static final String DEFAULT_RATING = "BBB-";
    private static final short DEFAULT_TENOR_MONTHS = 60;
    private static final int DEFAULT_SCALE = 2;
    
    // 감소율 시나리오
    private static final BigDecimal[] REDUCTION_RATES = {
        BigDecimal.ZERO,
        new BigDecimal("-0.10"),
        new BigDecimal("-0.20"),
        new BigDecimal("-0.30"),
        new BigDecimal("-0.50")
    };

    /**
     * S-RIM 계산
     * 
     * @param companyId 회사 ID
     * @param basis 기준 (YEAR/QTR)
     * @param rating 신용등급 (기본 BBB-)
     * @param tenorMonths 만기 (기본 60개월)
     * @return S-RIM 계산 결과
     */
    public SrimResultDto calculate(Long companyId, String basis, String rating, Integer tenorMonths) {
        log.debug("S-RIM 계산 시작: companyId={}, basis={}, rating={}, tenor={}", 
                  companyId, basis, rating, tenorMonths);

        // 기본값 설정
        if (rating == null) rating = DEFAULT_RATING;
        if (tenorMonths == null) tenorMonths = (int) DEFAULT_TENOR_MONTHS;
        if (basis == null) basis = "YEAR";

        // 1. 회사 정보 조회 (주식수)
        Long sharesOutstanding = companyRepository.findById(companyId)
                .map(company -> company.getSharesOutstanding())
                .orElseThrow(() -> new IllegalArgumentException("회사 정보를 찾을 수 없습니다."));

        if (sharesOutstanding == null || sharesOutstanding <= 0) {
            throw new IllegalArgumentException("상장 주식수 정보가 없습니다.");
        }

        // 2. ROE 가중평균 계산 (최근 3개, 가중치 3:2:1)
        BigDecimal roe = calculateWeightedAverageRoe(companyId, basis);
        log.debug("ROE: {}", roe);
        
        // 3. BPS (자기자본) 조회
        BigDecimal bps = getRecentBps(companyId, basis);
        log.debug("BPS: {}", bps);

        // 4. Ke (할인율) 조회 - 회사채 수익률
        BigDecimal ke = getDiscountRate(rating, tenorMonths.shortValue());
        log.debug("Ke: {}", ke);

        // 5. 자기자본 계산 (BPS * 주식수)
        BigDecimal equity = bps.multiply(new BigDecimal(sharesOutstanding));
        log.debug("Equity: {}", equity);

        // 6. 기본 초과이익 계산
        // ExcessEarnings = Equity * (ROE - Ke)
        BigDecimal baseExcessEarnings = equity.multiply(roe.subtract(ke));

        // 7. 시나리오별 계산
        List<SrimResultDto.ScenarioResult> scenarios = new ArrayList<>();
        
        for (BigDecimal reductionRate : REDUCTION_RATES) {
            // 감소율 적용
            BigDecimal adjustedExcessEarnings = baseExcessEarnings.multiply(
                BigDecimal.ONE.add(reductionRate)
            );
            
            // 기업가치 = Equity + (초과이익 / Ke)
            BigDecimal enterpriseValue = equity.add(
                adjustedExcessEarnings.divide(ke, 10, RoundingMode.HALF_UP)
            );
            
            // 적정주가 = 기업가치 / 주식수
            BigDecimal fairValuePerShare = enterpriseValue.divide(
                new BigDecimal(sharesOutstanding), 
                DEFAULT_SCALE, 
                RoundingMode.HALF_UP
            );
            
            scenarios.add(SrimResultDto.ScenarioResult.builder()
                    .reductionRate(reductionRate)
                    .excessEarnings(adjustedExcessEarnings.setScale(0, RoundingMode.HALF_UP))
                    .enterpriseValue(enterpriseValue.setScale(0, RoundingMode.HALF_UP))
                    .fairValuePerShare(fairValuePerShare)
                    .build());
        }

        return SrimResultDto.builder()
                .basis(basis)
                .rating(rating)
                .tenorMonths(tenorMonths)
                .equity(bps)  // BPS로 표시
                .roe(roe)
                .ke(ke)
                .sharesOutstanding(sharesOutstanding)
                .scenarios(scenarios)
                .build();
    }

    /**
     * ROE 가중평균 계산 (최근 3개, 가중치 3:2:1)
     */
    private BigDecimal calculateWeightedAverageRoe(Long companyId, String basis) {
        List<FinPeriod> periods;
        
        if ("YEAR".equals(basis)) {
            periods = finPeriodRepository.findRecentYearlyPeriods(companyId, 3);
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
                    .findByCompanyIdAndPeriodIdAndMetricCode(companyId, period.getPeriodId(), "ROE")
                    .map(FinMetricValue::getValueNum)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "ROE 데이터가 없습니다. period: " + period.getLabel()));

            log.debug("ROE: {} (idx={})", roe, idx++);
            roeValues.add(roe);
        }
        
        // 가중평균 계산 (최신 = 3, 두번째 = 2, 세번째 = 1)
        BigDecimal weightedSum = roeValues.get(0).multiply(new BigDecimal("3"))
                .add(roeValues.get(1).multiply(new BigDecimal("2")))
                .add(roeValues.get(2).multiply(BigDecimal.ONE));
        
        BigDecimal totalWeight = new BigDecimal("6"); // 3 + 2 + 1
        
        return weightedSum.divide(totalWeight, 10, RoundingMode.HALF_UP);
    }

    /**
     * 최근 BPS 조회
     */
    private BigDecimal getRecentBps(Long companyId, String basis) {
        List<FinPeriod> periods;
        
        if ("YEAR".equals(basis)) {
            periods = finPeriodRepository.findRecentYearlyPeriods(companyId, 1);
        } else {
            periods = finPeriodRepository.findRecentQuarterlyPeriods(companyId, 1);
        }
        
        if (periods.isEmpty()) {
            throw new IllegalArgumentException("BPS 데이터가 없습니다.");
        }
        
        return finMetricValueRepository
                .findByCompanyIdAndPeriodIdAndMetricCode(companyId, periods.get(0).getPeriodId(), "BPS")
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
