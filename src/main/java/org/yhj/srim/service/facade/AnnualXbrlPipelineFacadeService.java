package org.yhj.srim.service.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.client.DartReportType;
import org.yhj.srim.client.dto.DartFilingRow;
import org.yhj.srim.client.dto.DartShareStatusRow;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CommonError;
import org.yhj.srim.common.exception.code.CrawlingError;
import org.yhj.srim.common.exception.code.StockError;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.DartFsFiling;
import org.yhj.srim.service.crawl.DartCrawlingService;
import org.yhj.srim.service.crawl.XbrlFinancialStatementCrawlingService;
import org.yhj.srim.service.domain.DartFsFilingService;
import org.yhj.srim.service.domain.AnnualXbrlMetricProcessor;
import org.yhj.srim.service.domain.FinancialMetricService;
import org.yhj.srim.service.domain.FinancialService;
import org.yhj.srim.service.domain.StockService;
import org.yhj.srim.service.domain.XbrlAnnualDocumentLocator;
import org.yhj.srim.service.domain.XbrlRawService;
import org.yhj.srim.service.dto.XbrlAnnualDocumentRef;
import org.yhj.srim.service.facade.dto.CollectXbrlRawCommand;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnnualXbrlPipelineFacadeService {
    private final DartCrawlingService dartCrawlingService;
    private final XbrlFinancialStatementCrawlingService xbrlFinancialStatementCrawlingService;
    private final StockService stockService;
    private final DartFsFilingService dartFsFilingService;
    private final AnnualXbrlMetricProcessor annualXbrlMetricProcessor;
    private final FinancialService financialService;
    private final FinancialMetricService financialMetricService;
    private final XbrlAnnualDocumentLocator xbrlAnnualDocumentLocator;
    private final XbrlRawService xbrlRawService;
    private final PriceChartFacadeService priceChartFacadeService;

    public int processAnnualMetricsFromXbrl(Long stockId, int fiscalYear, String fsDiv) {
        Company company = financialService.getOrCreateCompanyWithStockCode(stockId);
        String resolvedFsDiv = resolveAnnualProcessingFsDiv(company, fiscalYear, fsDiv);
        int savedMetricCount = annualXbrlMetricProcessor.processAnnualMetricsFromXbrl(
                company.getCompanyId(),
                fiscalYear,
                resolvedFsDiv
        );
        savedMetricCount += financialMetricService.rebuildAnnualSupplementalMetricsFromXbrl(
                company.getCompanyId(),
                fiscalYear
        );
        return savedMetricCount;
    }

    public int collectAnnualFilingMetadata(Long stockId, int startYear, int endYear, String fsDiv) {
        int collectedCount = 0;
        for (int fiscalYear = endYear; fiscalYear >= startYear; fiscalYear--) {
            try {
                collectAnnualFilingMetadata(stockId, fiscalYear, fsDiv);
                collectedCount++;
            } catch (CustomException e) {
                log.warn("연간 filing 메타 수집 스킵 stockId={}, year={}, fsDiv={}, code={}, detail={}",
                        stockId, fiscalYear, fsDiv, e.getErrorCode().getCode(), e.getDetail());
            }
        }
        return collectedCount;
    }

    public int processAnnualMetricsFromXbrl(Long stockId, int startYear, int endYear, String fsDiv) {
        int savedMetricCount = 0;
        for (int fiscalYear = endYear; fiscalYear >= startYear; fiscalYear--) {
            try {
                savedMetricCount += processAnnualMetricsFromXbrl(stockId, fiscalYear, fsDiv);
            } catch (CustomException e) {
                log.warn("연간 XBRL metric 처리 스킵 stockId={}, year={}, fsDiv={}, code={}, detail={}",
                        stockId, fiscalYear, fsDiv, e.getErrorCode().getCode(), e.getDetail());
            }
        }
        return savedMetricCount;
    }

    public Long collectAnnualFilingMetadata(Long stockId, int fiscalYear, String fsDiv) {
        Company company = financialService.getOrCreateCompanyWithStockCode(stockId);
        return collectAnnualFilingMetadata(company, fiscalYear, fsDiv).getFsFilingId();
    }

    public Long runAnnualXbrlPipeline(Long stockId, String corpCode, String rceptNo, int bsnsYear, String fsDiv) {
        Company company = financialService.getOrCreateCompanyWithStockCode(stockId);

        collectAnnualShareStatus(company, corpCode, bsnsYear);
        collectAnnualPriceData(company, bsnsYear);

        Long documentId = collectXbrlRaw(new CollectXbrlRawCommand(
                company.getCompanyId(),
                corpCode,
                rceptNo,
                bsnsYear,
                DartReportType.ANNUAL,
                fsDiv
        ));

        int savedMetricCount = annualXbrlMetricProcessor.processAnnualMetricsFromXbrl(
                company.getCompanyId(),
                bsnsYear,
                fsDiv
        );
        savedMetricCount += financialMetricService.rebuildAnnualSupplementalMetricsFromXbrl(
                company.getCompanyId(),
                bsnsYear
        );

        log.info("XBRL 연간 파이프라인 처리 완료 stockId={}, companyId={}, year={}, fsDiv={}, documentId={}, savedMetricCount={}",
                stockId, company.getCompanyId(), bsnsYear, fsDiv, documentId, savedMetricCount);

        return documentId;
    }

    public Long runAnnualXbrlPipeline(Long stockId, int fiscalYear, String fsDiv) {
        Company company = financialService.getOrCreateCompanyWithStockCode(stockId);
        String resolvedFsDiv = resolveAnnualFsDiv(company, fiscalYear, fsDiv);
        return runAnnualXbrlPipelineWithResolvedFsDiv(stockId, company, fiscalYear, fsDiv, resolvedFsDiv);
    }

    public int runAnnualXbrlPipeline(Long stockId, int startYear, int endYear, String fsDiv) {
        for (int fiscalYear = endYear; fiscalYear >= startYear; fiscalYear--) {
            try {
                collectAnnualXbrlPipelineInputs(stockId, fiscalYear, fsDiv);
            } catch (CustomException e) {
                log.warn("연간 XBRL 원천 수집 스킵 stockId={}, year={}, fsDiv={}, code={}, detail={}",
                        stockId, fiscalYear, fsDiv, e.getErrorCode().getCode(), e.getDetail());
            }
        }

        int completedYears = 0;
        for (int fiscalYear = endYear; fiscalYear >= startYear; fiscalYear--) {
            try {
                processAnnualMetricsFromXbrl(stockId, fiscalYear, fsDiv);
                completedYears++;
            } catch (CustomException e) {
                log.warn("연간 XBRL 파이프라인 처리 스킵 stockId={}, year={}, fsDiv={}, code={}, detail={}",
                        stockId, fiscalYear, fsDiv, e.getErrorCode().getCode(), e.getDetail());
            }
        }
        collectCurrentYearPriceData(stockId);
        return completedYears;
    }

    public Long collectXbrlRaw(CollectXbrlRawCommand command) {
        Optional<Long> existingDocumentId = xbrlRawService.findStoredDocumentId(
                command.rceptNo(),
                command.reportType().code(),
                command.fsDiv()
        );
        if (existingDocumentId.isPresent()) {
            return existingDocumentId.get();
        }

        XbrlFinancialStatementCrawlingService.XbrlRawBatch batch =
                xbrlFinancialStatementCrawlingService.crawlFinancialStatementsXbrl(
                        command.corpCode(),
                        command.rceptNo(),
                        command.bsnsYear(),
                        command.reportType(),
                        command.fsDiv()
                );
        return xbrlRawService.saveFinancialStatementsXbrl(command.corpCode(), command.companyId(), batch);
    }

    private DartFsFiling collectAnnualFilingMetadata(Company company, int fiscalYear, String fsDiv) {
        String corpCode = company.getStockCode().getDartCorpCode();
        if (corpCode == null || corpCode.length() != 8) {
            throw new CustomException(StockError.DART_CORP_CODE_INVALID);
        }

        DartFilingRow filingRow = dartCrawlingService.crawlLatestAnnualFiling(corpCode, fiscalYear);
        return dartFsFilingService.saveAnnualFilingMetadata(
                corpCode,
                company.getCompanyId(),
                fiscalYear,
                filingRow,
                fsDiv
        );
    }

    private Long collectAnnualXbrlPipelineInputs(Long stockId, int fiscalYear, String fsDiv) {
        Company company = financialService.getOrCreateCompanyWithStockCode(stockId);
        String resolvedFsDiv = resolveAnnualFsDiv(company, fiscalYear, fsDiv);
        return collectAnnualXbrlPipelineInputsWithResolvedFsDiv(stockId, company, fiscalYear, fsDiv, resolvedFsDiv);
    }

    private Long collectAnnualXbrlPipelineInputsWithResolvedFsDiv(Long stockId,
                                                                  Company company,
                                                                  int fiscalYear,
                                                                  String requestedFsDiv,
                                                                  String resolvedFsDiv) {
        XbrlAnnualDocumentRef documentRef = xbrlAnnualDocumentLocator.resolve(company.getCompanyId(), fiscalYear, resolvedFsDiv);

        log.info("XBRL 연간 문서 선택 완료 stockId={}, companyId={}, year={}, requestedFsDiv={}, resolvedFsDiv={}, rceptNo={}",
                stockId, company.getCompanyId(), fiscalYear, requestedFsDiv, resolvedFsDiv, documentRef.rceptNo());

        try {
            collectAnnualShareStatus(company, documentRef.corpCode(), fiscalYear);
            collectAnnualPriceData(company, fiscalYear);

            return collectXbrlRaw(new CollectXbrlRawCommand(
                    company.getCompanyId(),
                    documentRef.corpCode(),
                    documentRef.rceptNo(),
                    fiscalYear,
                    DartReportType.ANNUAL,
                    resolvedFsDiv
            ));
        } catch (CustomException e) {
            if (!shouldFallbackToOfsOnMissingXbrl(requestedFsDiv, resolvedFsDiv, e)) {
                throw e;
            }

            collectAnnualFilingMetadata(company, fiscalYear, "OFS");
            log.info("XBRL 파일 미존재 fallback 적용 companyId={}, year={}, requestedFsDiv={}, resolvedFsDiv=OFS",
                    company.getCompanyId(), fiscalYear, requestedFsDiv);
            return collectAnnualXbrlPipelineInputsWithResolvedFsDiv(stockId, company, fiscalYear, requestedFsDiv, "OFS");
        }
    }

    private String resolveAnnualFsDiv(Company company, int fiscalYear, String requestedFsDiv) {
        try {
            collectAnnualFilingMetadata(company, fiscalYear, requestedFsDiv);
            return requestedFsDiv;
        } catch (CustomException e) {
            if (!shouldFallbackToOfs(requestedFsDiv, e)) {
                throw e;
            }

            collectAnnualFilingMetadata(company, fiscalYear, "OFS");
            log.info("XBRL 연간 문서 fallback 적용 companyId={}, year={}, requestedFsDiv={}, resolvedFsDiv=OFS",
                    company.getCompanyId(), fiscalYear, requestedFsDiv);
            return "OFS";
        }
    }

    private boolean shouldFallbackToOfs(String requestedFsDiv, CustomException e) {
        return "CFS".equalsIgnoreCase(requestedFsDiv)
                && (e.getErrorCode() == CrawlingError.DART_DISCLOSURE_NOT_FOUND
                || e.getErrorCode() == CommonError.INVALID_INPUT);
    }

    private String resolveAnnualProcessingFsDiv(Company company, int fiscalYear, String requestedFsDiv) {
        if (annualXbrlMetricProcessor.hasAnnualXbrlRaw(company.getCompanyId(), fiscalYear, requestedFsDiv)) {
            return requestedFsDiv;
        }
        if ("CFS".equalsIgnoreCase(requestedFsDiv)
                && annualXbrlMetricProcessor.hasAnnualXbrlRaw(company.getCompanyId(), fiscalYear, "OFS")) {
            log.info("XBRL 연간 metric 처리 fallback 적용 companyId={}, year={}, requestedFsDiv={}, resolvedFsDiv=OFS",
                    company.getCompanyId(), fiscalYear, requestedFsDiv);
            return "OFS";
        }
        return requestedFsDiv;
    }

    private Long runAnnualXbrlPipelineWithResolvedFsDiv(Long stockId,
                                                        Company company,
                                                        int fiscalYear,
                                                        String requestedFsDiv,
                                                        String resolvedFsDiv) {
        XbrlAnnualDocumentRef documentRef = xbrlAnnualDocumentLocator.resolve(company.getCompanyId(), fiscalYear, resolvedFsDiv);

        log.info("XBRL 연간 문서 선택 완료 stockId={}, companyId={}, year={}, requestedFsDiv={}, resolvedFsDiv={}, rceptNo={}",
                stockId, company.getCompanyId(), fiscalYear, requestedFsDiv, resolvedFsDiv, documentRef.rceptNo());

        try {
            return runAnnualXbrlPipeline(stockId, documentRef.corpCode(), documentRef.rceptNo(), fiscalYear, resolvedFsDiv);
        } catch (CustomException e) {
            if (!shouldFallbackToOfsOnMissingXbrl(requestedFsDiv, resolvedFsDiv, e)) {
                throw e;
            }

            collectAnnualFilingMetadata(company, fiscalYear, "OFS");
            log.info("XBRL 파일 미존재 fallback 적용 companyId={}, year={}, requestedFsDiv={}, resolvedFsDiv=OFS",
                    company.getCompanyId(), fiscalYear, requestedFsDiv);
            return runAnnualXbrlPipelineWithResolvedFsDiv(stockId, company, fiscalYear, requestedFsDiv, "OFS");
        }
    }

    private boolean shouldFallbackToOfsOnMissingXbrl(String requestedFsDiv, String resolvedFsDiv, CustomException e) {
        return "CFS".equalsIgnoreCase(requestedFsDiv)
                && "CFS".equalsIgnoreCase(resolvedFsDiv)
                && e.getErrorCode() == CrawlingError.DART_XBRL_NOT_AVAILABLE;
    }

    private void collectAnnualShareStatus(Company company, String corpCode, int fiscalYear) {
        List<DartShareStatusRow> shareStatusRows = dartCrawlingService.crawlShareStatus(corpCode, fiscalYear);
        stockService.replaceShareStatus(company, fiscalYear, shareStatusRows);
    }

    private void collectAnnualPriceData(Company company, int fiscalYear) {
        LocalDate startDate = LocalDate.of(fiscalYear, 1, 1);
        LocalDate endDate = LocalDate.of(fiscalYear, 12, 31);
        priceChartFacadeService.ensurePriceData(company.getCompanyId(), startDate, endDate);
    }

    private void collectCurrentYearPriceData(Long stockId) {
        Company company = financialService.getOrCreateCompanyWithStockCode(stockId);
        LocalDate today = LocalDate.now();
        LocalDate startDate = LocalDate.of(today.getYear(), 1, 1);
        try {
            priceChartFacadeService.ensurePriceData(company.getCompanyId(), startDate, today);
        } catch (Exception e) {
            log.warn("현재 연도 주가 추가 수집 스킵 stockId={}, companyId={}, startDate={}, endDate={}, detail={}",
                    stockId, company.getCompanyId(), startDate, today, e.getMessage());
        }
    }
}
