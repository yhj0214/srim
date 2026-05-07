package org.yhj.srim.service.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.service.domain.AnnualXbrlCollector;
import org.yhj.srim.service.domain.AnnualXbrlExecutionService;
import org.yhj.srim.service.domain.AnnualXbrlMetricProcessor;
import org.yhj.srim.service.domain.AnnualXbrlRunPolicy;
import org.yhj.srim.service.domain.FinancialMetricService;
import org.yhj.srim.service.domain.FinancialService;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnnualXbrlPipelineOrchestrator {
    private final AnnualXbrlCollector annualXbrlCollector;
    private final AnnualXbrlExecutionService annualXbrlExecutionService;
    private final AnnualXbrlRunPolicy annualXbrlRunPolicy;
    private final AnnualXbrlMetricProcessor annualXbrlMetricProcessor;
    private final FinancialService financialService;
    private final FinancialMetricService financialMetricService;

    public int runAnnualXbrlPipeline(Long stockId, int startYear, int endYear, String fsDiv) {

        runAnnualRange(stockId, startYear, endYear, fsDiv, "연간 XBRL 원천 수집",
                fiscalYear -> {
                    collectAnnualXbrlPipelineInputs(stockId, fiscalYear, fsDiv);
                    return 0;
                }
        );

        int completedYears = runAnnualRange(stockId, startYear, endYear, fsDiv, "연간 XBRL 파이프라인 처리",
                fiscalYear -> {
                    processAnnualMetricsFromXbrl(stockId, fiscalYear, fsDiv);
                    return 1;
                }
        );
        collectCurrentYearPriceData(stockId);
        return completedYears;
    }

    private Long collectAnnualXbrlPipelineInputs(Long stockId, int fiscalYear, String fsDiv) {
        Company company = financialService.getOrCreateCompanyWithStockCode(stockId);
        return annualXbrlExecutionService.collectAnnualInputs(stockId, company, fiscalYear, fsDiv).documentId();
    }

    private void collectCurrentYearPriceData(Long stockId) {
        Company company = financialService.getOrCreateCompanyWithStockCode(stockId);
        annualXbrlCollector.collectCurrentYearPriceData(company);
    }

    private int rebuildAnnualMetrics(Long companyId, int fiscalYear, String fsDiv) {
        int savedMetricCount = annualXbrlMetricProcessor.processAnnualMetricsFromXbrl(
                companyId,
                fiscalYear,
                fsDiv
        );
        savedMetricCount += financialMetricService.rebuildAnnualSupplementalMetricsFromXbrl(
                companyId,
                fiscalYear
        );
        return savedMetricCount;
    }

    private int runAnnualRange(Long stockId,
                               int startYear,
                               int endYear,
                               String fsDiv,
                               String actionLabel,
                               AnnualYearTask task) {
        int result = 0;
        for (int fiscalYear = endYear; fiscalYear >= startYear; fiscalYear--) {
            try {
                result += task.run(fiscalYear);
            } catch (CustomException e) {
                log.warn("{} 스킵 stockId={}, year={}, fsDiv={}, code={}, detail={}",
                        actionLabel, stockId, fiscalYear, fsDiv, e.getErrorCode().getCode(), e.getDetail());
            }
        }
        return result;
    }

    private int processAnnualMetricsFromXbrl(Long stockId, int fiscalYear, String fsDiv) {
        Company company = financialService.getOrCreateCompanyWithStockCode(stockId);
        String resolvedFsDiv = annualXbrlRunPolicy.resolveAnnualProcessingFsDiv(company.getCompanyId(), fiscalYear, fsDiv);
        return rebuildAnnualMetrics(company.getCompanyId(), fiscalYear, resolvedFsDiv);
    }

    @FunctionalInterface
    private interface AnnualYearTask {
        int run(int fiscalYear);
    }
}
