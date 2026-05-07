package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.repository.entity.FinPeriod;
import org.yhj.srim.service.domain.calculator.AnnualXbrlBaseMetricCalculator;
import org.yhj.srim.service.domain.calculator.AnnualXbrlDerivedMetricCalculator;
import org.yhj.srim.service.domain.calculator.AnnualXbrlPerShareMetricCalculator;
import org.yhj.srim.service.dto.FsRawBundle;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class QuarterXbrlMetricProcessor {
    private final AnnualXbrlBaseMetricCalculator annualXbrlBaseMetricCalculator;
    private final AnnualXbrlDerivedMetricCalculator annualXbrlDerivedMetricCalculator;
    private final AnnualXbrlPerShareMetricCalculator annualXbrlPerShareMetricCalculator;
    private final FinancialService financialService;

    // xbrl의 현재 지표값 추출 및 basemetric 생성, 반환
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> buildQuarterBaseMetricsFromXbrl(Long companyId,
                                                                   int fiscalYear,
                                                                   int fiscalQuarter,
                                                                   String fsDiv) {
        return buildStageMetrics(companyId, fiscalYear, fiscalQuarter, fsDiv,
                rawBundle -> annualXbrlBaseMetricCalculator.calculate(rawBundle.curr()));
    }

    /**
     * basemetric map을 생성하여 전달
     * 분기 finpriod 조회, 해당 분기의 base metric 교체 저장
     * 실제 저장된 metric개수 반환
     */
    @Transactional
    public int replaceQuarterBaseMetricsFromXbrl(Long companyId, int fiscalYear, int fiscalQuarter, String fsDiv) {
        return replaceStageMetrics(
                companyId,
                fiscalYear,
                fiscalQuarter,
                buildQuarterBaseMetricsFromXbrl(companyId, fiscalYear, fiscalQuarter, fsDiv),
                MetricStage.BASE
        );
    }

    /**
     * 분기 raw bundle확보
     * derivedMetric계산
     * derivedmetric반환, 저장x
     */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> buildQuarterDerivedMetricsFromXbrl(Long companyId,
                                                                      int fiscalYear,
                                                                      int fiscalQuarter,
                                                                      String fsDiv) {
        return buildStageMetrics(companyId, fiscalYear, fiscalQuarter, fsDiv,
                rawBundle -> annualXbrlDerivedMetricCalculator.calculate(rawBundle.curr(), rawBundle.prev(), fiscalYear));
    }

    /**
     * 반환받은 derived metric 저장
     * 저장된 metric 개수 반환
     */
    @Transactional
    public int replaceQuarterDerivedMetricsFromXbrl(Long companyId, int fiscalYear, int fiscalQuarter, String fsDiv) {
        return replaceStageMetrics(
                companyId,
                fiscalYear,
                fiscalQuarter,
                buildQuarterDerivedMetricsFromXbrl(companyId, fiscalYear, fiscalQuarter, fsDiv),
                MetricStage.DERIVED
        );
    }

    /**
     * xbrl raw를 조회 후 주당시표 계산 후 map으로 반환
     */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> buildQuarterPerShareMetricsFromXbrl(Long companyId,
                                                                       int fiscalYear,
                                                                       int fiscalQuarter,
                                                                       String fsDiv) {
        return buildStageMetrics(companyId, fiscalYear, fiscalQuarter, fsDiv,
                rawBundle -> annualXbrlPerShareMetricCalculator.calculate(companyId, rawBundle.curr(), fiscalYear));
    }

    /**
     * 반환받은 주당지표 metric을 저장
     * 저장된 metric개수 반환
     */
    @Transactional
    public int replaceQuarterPerShareMetricsFromXbrl(Long companyId, int fiscalYear, int fiscalQuarter, String fsDiv) {
        return replaceStageMetrics(
                companyId,
                fiscalYear,
                fiscalQuarter,
                buildQuarterPerShareMetricsFromXbrl(companyId, fiscalYear, fiscalQuarter, fsDiv),
                MetricStage.PER_SHARE
        );
    }

    // 해당 회사의 분기 xbrl raw가 실제 존재하는지 확인
    @Transactional(readOnly = true)
    public boolean hasQuarterXbrlRaw(Long companyId, int fiscalYear, int fiscalQuarter, String fsDiv) {
        return !financialService.loadQuarterXbrlRawBundle(companyId, fiscalYear, fiscalQuarter, fsDiv).curr().isEmpty();
    }


    /**
     * 분기 metric 처리의 상위 진입점
     * 해당 분기의 xbrl raw를 기준으로 base, derived, pershare 지표를 계산 및 저장 후 총 저장건수 반환
     */
    @Transactional
    public int processQuarterMetricsFromXbrl(Long companyId, int fiscalYear, int fiscalQuarter, String fsDiv) {
        int savedCount = 0;
        savedCount += replaceQuarterBaseMetricsFromXbrl(companyId, fiscalYear, fiscalQuarter, fsDiv);
        savedCount += replaceQuarterDerivedMetricsFromXbrl(companyId, fiscalYear, fiscalQuarter, fsDiv);
        savedCount += replaceQuarterPerShareMetricsFromXbrl(companyId, fiscalYear, fiscalQuarter, fsDiv);
        return savedCount;
    }

    // 분기 raw bundle을 읽고 전달받은 계산함수를 적용하여 metric map 생성
    private Map<String, BigDecimal> buildStageMetrics(Long companyId,
                                                      int fiscalYear,
                                                      int fiscalQuarter,
                                                      String fsDiv,
                                                      Function<FsRawBundle, Map<String, BigDecimal>> calculator) {
        FsRawBundle rawBundle = financialService.loadQuarterXbrlRawBundle(companyId, fiscalYear, fiscalQuarter, fsDiv);
        return calculator.apply(rawBundle);
    }

    // 계산된 분기 metric 맵을 해당 분기의 finperiod에 맞춰 실제 db에 저장하는 공통 헬퍼
    private int replaceStageMetrics(Long companyId,
                                    int fiscalYear,
                                    int fiscalQuarter,
                                    Map<String, BigDecimal> metrics,
                                    MetricStage stage) {
        FinPeriod period = financialService.findQuarterPeriod(companyId, fiscalYear, fiscalQuarter).orElse(null);
        return financialService.replaceMetrics(companyId, period, stage, metrics);
    }
}
