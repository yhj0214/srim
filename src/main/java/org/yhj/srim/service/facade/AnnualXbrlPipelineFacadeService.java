package org.yhj.srim.service.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.service.domain.AnnualXbrlMetricProcessor;
import org.yhj.srim.service.domain.FinancialMetricService;
import org.yhj.srim.service.domain.FinancialService;
import org.yhj.srim.service.facade.dto.CollectXbrlRawCommand;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnnualXbrlPipelineFacadeService {
    private final AnnualXbrlCollector annualXbrlCollector;
    private final AnnualXbrlExecutionService annualXbrlExecutionService;
    private final AnnualXbrlRunPolicy annualXbrlRunPolicy;
    private final AnnualXbrlMetricProcessor annualXbrlMetricProcessor;
    private final FinancialService financialService;
    private final FinancialMetricService financialMetricService;

    public int processAnnualMetricsFromXbrl(Long stockId, int fiscalYear, String fsDiv) {
        Company company = financialService.getOrCreateCompanyWithStockCode(stockId);
        String resolvedFsDiv = annualXbrlRunPolicy.resolveAnnualProcessingFsDiv(company.getCompanyId(), fiscalYear, fsDiv);
        return rebuildAnnualMetrics(company.getCompanyId(), fiscalYear, resolvedFsDiv);
    }

    public int collectAnnualFilingMetadata(Long stockId, int startYear, int endYear, String fsDiv) {
        return runAnnualRange(
                stockId,
                startYear,
                endYear,
                fsDiv,
                "연간 filing 메타 수집",
                fiscalYear -> {
                    collectAnnualFilingMetadata(stockId, fiscalYear, fsDiv);
                    return 1;
                }
        );
    }

    public int processAnnualMetricsFromXbrl(Long stockId, int startYear, int endYear, String fsDiv) {
        return runAnnualRange(
                stockId,
                startYear,
                endYear,
                fsDiv,
                "연간 XBRL metric 처리",
                fiscalYear -> processAnnualMetricsFromXbrl(stockId, fiscalYear, fsDiv)
        );
    }

    public Long collectAnnualFilingMetadata(Long stockId, int fiscalYear, String fsDiv) {
        Company company = financialService.getOrCreateCompanyWithStockCode(stockId);
        return annualXbrlCollector.collectAnnualFilingMetadata(company, fiscalYear, fsDiv).getFsFilingId();
    }

    public Long runAnnualXbrlPipeline(Long stockId, String corpCode, String rceptNo, int bsnsYear, String fsDiv) {
        Company company = financialService.getOrCreateCompanyWithStockCode(stockId);
        Long documentId = annualXbrlCollector.collectAnnualInputs(company, corpCode, rceptNo, bsnsYear, fsDiv);

        int savedMetricCount = rebuildAnnualMetrics(company.getCompanyId(), bsnsYear, fsDiv);

        log.info("XBRL 연간 파이프라인 처리 완료 stockId={}, companyId={}, year={}, fsDiv={}, documentId={}, savedMetricCount={}",
                stockId, company.getCompanyId(), bsnsYear, fsDiv, documentId, savedMetricCount);

        return documentId;
    }

    public Long runAnnualXbrlPipeline(Long stockId, int fiscalYear, String fsDiv) {
        Company company = financialService.getOrCreateCompanyWithStockCode(stockId);
        AnnualXbrlExecutionService.ExecutionResult result =
                annualXbrlExecutionService.collectAnnualInputs(stockId, company, fiscalYear, fsDiv);
        int savedMetricCount = rebuildAnnualMetrics(company.getCompanyId(), fiscalYear, result.resolvedFsDiv());

        log.info("XBRL 연간 파이프라인 처리 완료 stockId={}, companyId={}, year={}, fsDiv={}, documentId={}, savedMetricCount={}",
                stockId, company.getCompanyId(), fiscalYear, result.resolvedFsDiv(), result.documentId(), savedMetricCount);

        return result.documentId();
    }

    public int runAnnualXbrlPipeline(Long stockId, int startYear, int endYear, String fsDiv) {
        runAnnualRange(
                stockId,
                startYear,
                endYear,
                fsDiv,
                "연간 XBRL 원천 수집",
                fiscalYear -> {
                    collectAnnualXbrlPipelineInputs(stockId, fiscalYear, fsDiv);
                    return 0;
                }
        );

        int completedYears = runAnnualRange(
                stockId,
                startYear,
                endYear,
                fsDiv,
                "연간 XBRL 파이프라인 처리",
                fiscalYear -> {
                    processAnnualMetricsFromXbrl(stockId, fiscalYear, fsDiv);
                    return 1;
                }
        );
        collectCurrentYearPriceData(stockId);
        return completedYears;
    }

    public Long collectXbrlRaw(CollectXbrlRawCommand command) {
        return annualXbrlCollector.collectXbrlRaw(command);
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

    @FunctionalInterface
    private interface AnnualYearTask {
        int run(int fiscalYear);
    }
}
