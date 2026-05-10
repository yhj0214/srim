package org.yhj.srim.service.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.service.domain.FinancialService;
import org.yhj.srim.service.domain.QuarterXbrlCollector;
import org.yhj.srim.service.domain.QuarterXbrlMetricProcessor;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuarterXbrlPipelineOrchestrator {
    private final QuarterXbrlCollector quarterXbrlCollector;
    private final QuarterXbrlMetricProcessor quarterXbrlMetricProcessor;
    private final FinancialService financialService;

    public int runQuarterXbrlPipelineRange(Long stockId, int startYear, int endYear, String fsDiv) {
        int savedMetricCount = 0;
        for (int fiscalYear = endYear; fiscalYear >= startYear; fiscalYear--) {
            for (int fiscalQuarter = 1; fiscalQuarter <= 4; fiscalQuarter++) {
                try {
                    savedMetricCount += runQuarterXbrlPipeline(stockId, fiscalYear, fiscalQuarter, fsDiv);
                } catch (CustomException e) {
                    log.warn("분기 XBRL 파이프라인 처리 스킵 stockId={}, year={}, quarter={}, fsDiv={}, code={}, detail={}",
                            stockId, fiscalYear, fiscalQuarter, fsDiv, e.getErrorCode().getCode(), e.getDetail());
                }
            }
        }
        return savedMetricCount;
    }

    // 분기 raw수집, metric처리 이후 처리된 metric개수 반환
    public int runQuarterXbrlPipeline(Long stockId, int fiscalYear, int fiscalQuarter, String fsDiv) {
        collectQuarterXbrlPipelineInputs(stockId, fiscalYear, fiscalQuarter, fsDiv);
        return processQuarterMetricsFromXbrl(stockId, fiscalYear, fiscalQuarter, fsDiv);
    }

    // 분기 raw 수집
    private Long collectQuarterXbrlPipelineInputs(Long stockId, int fiscalYear, int fiscalQuarter, String fsDiv) {
        Company company = financialService.getOrCreateCompanyWithStockCode(stockId);
        return quarterXbrlCollector.collectQuarterInputs(company, fiscalYear, fiscalQuarter, fsDiv);
    }

    // 수집된 raw기반 metric생성 및 개수 반환
    private int processQuarterMetricsFromXbrl(Long stockId, int fiscalYear, int fiscalQuarter, String fsDiv) {
        Company company = financialService.getOrCreateCompanyWithStockCode(stockId);
        return quarterXbrlMetricProcessor.processQuarterMetricsFromXbrl(
                company.getCompanyId(),
                fiscalYear,
                fiscalQuarter,
                fsDiv
        );
    }
}
