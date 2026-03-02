package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.repository.FinMetricValueRepository;
import org.yhj.srim.repository.FinPeriodRepository;
import org.yhj.srim.repository.StockPriceRepository;
import org.yhj.srim.repository.entity.FinMetricValue;
import org.yhj.srim.repository.entity.FinPeriod;
import org.yhj.srim.repository.entity.StockPrice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceBasedMetricService {

    private static final String METRIC_EPS = "EPS";
    private static final String METRIC_BPS = "BPS";
    private static final String METRIC_PER = "PER";
    private static final String METRIC_PBR = "PBR";

    private final FinPeriodRepository finPeriodRepository;
    private final FinMetricValueRepository finMetricValueRepository;
    private final StockPriceRepository stockPriceRepository;

    @Transactional
    public int recalcAnnualPriceMetrics(Long companyId) {
        List<FinPeriod> periods = finPeriodRepository.findYearlyPeriods(companyId);
        if (periods.isEmpty()) {
            log.debug("[PRICE-METRIC] no periods - companyId={}", companyId);
            return 0;
        }

        int updated = 0;
        for (FinPeriod period : periods) {
            Integer fiscalYear = period.getFiscalYear();
            if (fiscalYear == null) {
                continue;
            }

            Optional<BigDecimal> priceOpt = findYearEndPrice(companyId, fiscalYear);
            if (priceOpt.isEmpty()) {
                log.debug("[PRICE-METRIC] no year-end price - companyId={}, year={}", companyId, fiscalYear);
                continue;
            }

            BigDecimal price = priceOpt.get();

            Optional<BigDecimal> epsOpt = findMetric(companyId, period, METRIC_EPS);
            if (epsOpt.isPresent()) {
                BigDecimal eps = epsOpt.get();
                if (eps.compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal per = price.divide(eps, 2, RoundingMode.HALF_UP);
                    upsertMetric(companyId, period, METRIC_PER, per);
                    updated++;
                }
            }

            Optional<BigDecimal> bpsOpt = findMetric(companyId, period, METRIC_BPS);
            if (bpsOpt.isPresent()) {
                BigDecimal bps = bpsOpt.get();
                if (bps.compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal pbr = price.divide(bps, 2, RoundingMode.HALF_UP);
                    upsertMetric(companyId, period, METRIC_PBR, pbr);
                    updated++;
                }
            }
        }

        log.info("[PRICE-METRIC] done - companyId={}, updated={}", companyId, updated);
        return updated;
    }

    private Optional<BigDecimal> findYearEndPrice(Long companyId, int fiscalYear) {
        LocalDate yearEnd = LocalDate.of(fiscalYear, 12, 31);
        return stockPriceRepository
                .findTopByCompany_CompanyIdAndTradeDateLessThanEqualOrderByTradeDateDesc(companyId, yearEnd)
                .map(StockPrice::getPrice);
    }

    private Optional<BigDecimal> findMetric(Long companyId, FinPeriod period, String metricCode) {
        return finMetricValueRepository
                .findByCompanyIdAndPeriodAndMetricCode(companyId, period, metricCode)
                .map(FinMetricValue::getValueNum);
    }

    private void upsertMetric(Long companyId, FinPeriod period, String metricCode, BigDecimal value) {
        FinMetricValue metricValue = finMetricValueRepository
                .findByCompanyIdAndPeriodAndMetricCode(companyId, period, metricCode)
                .orElseGet(() -> FinMetricValue.builder()
                        .companyId(companyId)
                        .period(period)
                        .metricCode(metricCode)
                        .source("MANUAL")
                        .build());

        metricValue.setValueNum(value);
        finMetricValueRepository.save(metricValue);
    }
}
