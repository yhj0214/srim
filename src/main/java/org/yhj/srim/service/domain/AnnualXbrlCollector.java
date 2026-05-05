package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.client.DartReportType;
import org.yhj.srim.client.dto.DartFilingRow;
import org.yhj.srim.client.dto.DartShareStatusRow;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.StockError;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.DartFsFiling;
import org.yhj.srim.service.crawl.DartCrawlingService;
import org.yhj.srim.service.crawl.XbrlFinancialStatementCrawlingService;
import org.yhj.srim.service.application.PriceChartApplicationService;
import org.yhj.srim.service.application.dto.CollectXbrlRawCommand;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnnualXbrlCollector {
    private final DartCrawlingService dartCrawlingService;
    private final XbrlFinancialStatementCrawlingService xbrlFinancialStatementCrawlingService;
    private final StockService stockService;
    private final DartFsFilingService dartFsFilingService;
    private final XbrlRawService xbrlRawService;
    private final PriceChartApplicationService priceChartFacadeService;

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

    public DartFsFiling collectAnnualFilingMetadata(Company company, int fiscalYear, String fsDiv) {
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

    public Long collectAnnualInputs(Company company, String corpCode, String rceptNo, int fiscalYear, String fsDiv) {
        collectAnnualShareStatus(company, corpCode, fiscalYear);
        collectAnnualPriceData(company, fiscalYear);

        return collectXbrlRaw(new CollectXbrlRawCommand(
                company.getCompanyId(),
                corpCode,
                rceptNo,
                fiscalYear,
                DartReportType.ANNUAL,
                fsDiv
        ));
    }

    public void collectCurrentYearPriceData(Company company) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = LocalDate.of(today.getYear(), 1, 1);
        try {
            priceChartFacadeService.ensurePriceData(company.getCompanyId(), startDate, today);
        } catch (Exception e) {
            log.warn("현재 연도 주가 추가 수집 스킵 companyId={}, startDate={}, endDate={}, detail={}",
                    company.getCompanyId(), startDate, today, e.getMessage());
        }
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
}
