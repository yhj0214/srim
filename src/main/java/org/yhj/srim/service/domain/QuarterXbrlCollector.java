package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.yhj.srim.client.DartReportType;
import org.yhj.srim.client.dto.DartFilingRow;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CommonError;
import org.yhj.srim.common.exception.code.StockError;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.DartFsFiling;
import org.yhj.srim.service.application.dto.CollectXbrlRawCommand;
import org.yhj.srim.service.crawl.DartCrawlingService;
import org.yhj.srim.service.crawl.XbrlFinancialStatementCrawlingService;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class QuarterXbrlCollector {
    private final DartCrawlingService dartCrawlingService;
    private final DartFsFilingService dartFsFilingService;
    private final XbrlFinancialStatementCrawlingService xbrlFinancialStatementCrawlingService;
    private final XbrlRawService xbrlRawService;
    private final FinancialService financialService;

    /**
     * 분기 보고서타입으로 변환한 후
     * 해당 분기의 filing데이터 저장
     * filing 기준으로 xbrl raw저장
     * 최종 documentid 반환
     */
    public Long collectQuarterInputs(Company company, int fiscalYear, int fiscalQuarter, String fsDiv) {
        DartReportType reportType = resolveQuarterReportType(fiscalQuarter);
        DartFsFiling filing = collectQuarterFilingMetadata(company, fiscalYear, reportType, fsDiv);
        financialService.ensureQuarterPeriod(company.getCompanyId(), fiscalYear, fiscalQuarter);

        return collectXbrlRaw(new CollectXbrlRawCommand(
                company.getCompanyId(),
                filing.getCorpCode(),
                filing.getRceptNo(),
                fiscalYear,
                reportType,
                fsDiv
        ));
    }

    // 분기 filing 메타를 수집하고 dartfsfiling으로 저장
    public DartFsFiling collectQuarterFilingMetadata(Company company,
                                                     int fiscalYear,
                                                     DartReportType reportType,
                                                     String fsDiv) {
        String corpCode = company.getStockCode().getDartCorpCode();
        if (corpCode == null || corpCode.length() != 8) {
            throw new CustomException(StockError.DART_CORP_CODE_INVALID);
        }

        DartFilingRow filingRow = dartCrawlingService.crawlLatestFiling(corpCode, fiscalYear, reportType);
        return dartFsFilingService.saveFilingMetadata(
                corpCode,
                company.getCompanyId(),
                fiscalYear,
                filingRow,
                reportType,
                fsDiv
        );
    }

    /**
     * 저장된 문서가 있으면 기존 documentId재사용
     * 없으면 raw수집 + 저장
     */
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

    // 분기 숫자를 dart보고서 타입으로 변환
    private DartReportType resolveQuarterReportType(int fiscalQuarter) {
        return switch (fiscalQuarter) {
            case 1 -> DartReportType.FIRST_QUARTER;
            case 2 -> DartReportType.HALF_YEAR;
            case 3 -> DartReportType.THIRD_QUARTER;
            default -> throw new CustomException(
                    CommonError.INVALID_INPUT,
                    "지원하지 않는 분기 값입니다. fiscalQuarter=" + fiscalQuarter
            );
        };
    }
}
