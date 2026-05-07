package org.yhj.srim.service.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.service.domain.FinancialService;
import org.yhj.srim.service.domain.QuarterXbrlCollector;
import org.yhj.srim.service.domain.QuarterXbrlMetricProcessor;

@Service
@RequiredArgsConstructor
public class QuarterXbrlPipelineOrchestrator {
    private final QuarterXbrlCollector quarterXbrlCollector;
    private final QuarterXbrlMetricProcessor quarterXbrlMetricProcessor;
    private final FinancialService financialService;

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
