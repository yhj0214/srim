package org.yhj.srim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.repository.CompanyRepository;
import org.yhj.srim.repository.FinMetricValueRepository;
import org.yhj.srim.repository.FinPeriodRepository;
import org.yhj.srim.repository.StockShareStatusRepository;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.FinMetricValue;
import org.yhj.srim.repository.entity.FinPeriod;
import org.yhj.srim.repository.entity.StockShareStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BpsCalculatorService {



    private final FinPeriodRepository finPeriodRepository;
    private final FinMetricValueRepository finMetricValueRepository;
    private final CompanyRepository companyRepository;


    private static final String METRIC_EQUITY_OWNER = "TOTAL_EQUITY_OWNER";
    private static final String METRIC_BPS           = "BPS";           // BPS
    private final StockShareStatusRepository stockShareStatusRepository;

    /**
     * 한 회사에 대해 연간 + 분기 모든 기간의 BPS를 재계산해서 저장.
     * @return 새로 저장/업데이트한 BPS 레코드 수
     */
    @Transactional
    public int recalcAllBpsForCompany(Long companyId) {
        int updated = 0;
        updated += recalcBpsForBasis(companyId, "YEAR");
        // updated += recalcBpsForBasis(companyId, "QUARTER");
        return updated;
    }

    /**
     * 기준(basis: YEAR/QUARTER)에 대해, 해당 회사의 모든 기간에 대해 BPS 재계산.
     */
    @Transactional
    public int recalcBpsForBasis(Long companyId, String basis) {
        List<FinPeriod> periods;
        if ("YEAR".equals(basis)) {
            periods = finPeriodRepository.findYearlyPeriods(companyId);
        } else {
            periods = finPeriodRepository.findQuarterlyPeriods(companyId);
        }

        // 디버깅용: 기간이 몇 개 잡혔는지 로그
        log.info("[BPS] companyId={}, basis={}, periods={}", companyId, basis, periods.size());

        if (periods.isEmpty()) {
            return 0;
        }

        int updated = 0;

        for (FinPeriod period : periods) {
            Long periodId = period.getPeriodId();

            // 지배주주자본 = TOTAL_EQUITY_OWNER
            Optional<BigDecimal> equityOpt = findEquityOwner(companyId, period);

            // 주식 수 = stock_share_status or company
            Optional<BigDecimal> sharesOpt = findSharesForPeriod(companyId, period);

            if (equityOpt.isEmpty() || sharesOpt.isEmpty()) {
                log.debug("[BPS] skip periodId={} (equityPresent={}, sharesPresent={})",
                        periodId, equityOpt.isPresent(), sharesOpt.isPresent());
                continue;
            }

            BigDecimal equity = equityOpt.get();
            BigDecimal shares = sharesOpt.get();

            if (shares.compareTo(BigDecimal.ZERO) == 0) {
                log.debug("[BPS] skip periodId={} (shares is ZERO)", periodId);
                continue;
            }

            // BPS = 지배주주자본 / 발행주식수 (원단위 정수)
            BigDecimal bps = equity.divide(
                    shares,
                    0,                     // 소수점 없이 원단위
                    RoundingMode.HALF_UP
            );

            upsertBps(companyId, period, bps);
            updated++;
        }

        log.info("[BPS] calc done - companyId={}, basis={}, updated={}", companyId, basis, updated);
        return updated;
    }
    private Optional<BigDecimal> findEquityOwner(Long companyId, FinPeriod period) {
        return finMetricValueRepository
                .findByCompanyIdAndPeriodAndMetricCode(companyId, period, METRIC_EQUITY_OWNER)
                .map(FinMetricValue::getValueNum);
    }

    private Optional<BigDecimal> findSharesForPeriod(Long companyId, FinPeriod period) {
        // 기준일: period_end 사용 (없으면 연도 기준으로 대충 말일 )
        LocalDate baseDate = period.getPeriodEnd();
        if (baseDate == null && period.getFiscalYear() != null) {
            // period_end가 비어 있으면 회계연도 기준 12월 31일로 보정 (임시)
            baseDate = LocalDate.of(period.getFiscalYear(), 12, 31);
        }

        //  stock_share_status에서 기준일 이전/같은 결산일 중 가장 최근 것 조회
        //    se는 '합계' 기준으로 보는게 자연스러워 보임 (필요하면 '보통주'로 바꿔도 됨)
        Optional<StockShareStatus> statusOpt =
                stockShareStatusRepository
                        .findTopByCompany_CompanyIdAndSettlementDateLessThanEqualAndSeOrderBySettlementDateDesc(
                                companyId, baseDate, "합계"
                        );

        if (statusOpt.isPresent()) {
            StockShareStatus status = statusOpt.get();

            // 유통주식수(distbStockCo)를 최우선으로 사용
            Long shares = status.getDistbStockCo();

            // 없으면 발행주식총수(istcTotqy)로 fallback
            if (shares == null || shares == 0L) {
                shares = status.getIstcTotqy();
            }

            if (shares != null && shares > 0L) {
                return Optional.of(BigDecimal.valueOf(shares));
            }
            // 여기까지 왔다는 건 stock_share_status에는 있는데 값이 0 이거나 null → company로 fallback
        }

        // 3) stock_share_status에서 못 찾았거나, 유의미한 값이 없으면 company.sharesOutstanding 사용
        return companyRepository.findById(companyId)
                .map(Company::getSharesOutstanding)
                .filter(s -> s != null && s > 0L)
                .map(BigDecimal::valueOf);
    }


    /**
     * BPS 값을 fin_metric_value에 upsert (없으면 생성, 있으면 수정).
     */
    private void upsertBps(Long companyId, FinPeriod period, BigDecimal bps) {
        FinMetricValue bpsValue = finMetricValueRepository
                .findByCompanyIdAndPeriodAndMetricCode(companyId, period, METRIC_BPS)
                .orElseGet(() ->
                        FinMetricValue.builder()
                                .companyId(companyId)
                                .period(period)
                                .metricCode(METRIC_BPS)
//                                .source("CALC") // 계산값이라는 표시용, DB 변경 필요
                                .source("MANUAL")
                                .build()
                );

        bpsValue.setValueNum(bps);
        finMetricValueRepository.save(bpsValue);
    }
}