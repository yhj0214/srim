package org.yhj.srim.service.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.service.domain.XbrlAnnualDocumentLocator;
import org.yhj.srim.service.dto.XbrlAnnualDocumentRef;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnnualXbrlExecutionService {
    private final AnnualXbrlCollector annualXbrlCollector;
    private final AnnualXbrlRunPolicy annualXbrlRunPolicy;
    private final XbrlAnnualDocumentLocator xbrlAnnualDocumentLocator;

    public ExecutionResult collectAnnualInputs(Long stockId, Company company, int fiscalYear, String requestedFsDiv) {
        String resolvedFsDiv = resolveAnnualFsDiv(company, fiscalYear, requestedFsDiv);
        return collectAnnualInputsWithResolvedFsDiv(stockId, company, fiscalYear, requestedFsDiv, resolvedFsDiv);
    }

    private ExecutionResult collectAnnualInputsWithResolvedFsDiv(Long stockId,
                                                                 Company company,
                                                                 int fiscalYear,
                                                                 String requestedFsDiv,
                                                                 String resolvedFsDiv) {
        XbrlAnnualDocumentRef documentRef = xbrlAnnualDocumentLocator.resolve(company.getCompanyId(), fiscalYear, resolvedFsDiv);

        log.info("XBRL 연간 문서 선택 완료 stockId={}, companyId={}, year={}, requestedFsDiv={}, resolvedFsDiv={}, rceptNo={}",
                stockId, company.getCompanyId(), fiscalYear, requestedFsDiv, resolvedFsDiv, documentRef.rceptNo());

        try {
            Long documentId = annualXbrlCollector.collectAnnualInputs(
                    company,
                    documentRef.corpCode(),
                    documentRef.rceptNo(),
                    fiscalYear,
                    resolvedFsDiv
            );
            return new ExecutionResult(documentId, resolvedFsDiv);
        } catch (CustomException e) {
            if (!annualXbrlRunPolicy.shouldFallbackToOfsOnMissingXbrl(requestedFsDiv, resolvedFsDiv, e)) {
                throw e;
            }

            annualXbrlCollector.collectAnnualFilingMetadata(company, fiscalYear, "OFS");
            log.info("XBRL 파일 미존재 fallback 적용 companyId={}, year={}, requestedFsDiv={}, resolvedFsDiv=OFS",
                    company.getCompanyId(), fiscalYear, requestedFsDiv);
            return collectAnnualInputsWithResolvedFsDiv(stockId, company, fiscalYear, requestedFsDiv, "OFS");
        }
    }

    private String resolveAnnualFsDiv(Company company, int fiscalYear, String requestedFsDiv) {
        try {
            annualXbrlCollector.collectAnnualFilingMetadata(company, fiscalYear, requestedFsDiv);
            return requestedFsDiv;
        } catch (CustomException e) {
            if (!annualXbrlRunPolicy.shouldFallbackToOfs(requestedFsDiv, e)) {
                throw e;
            }

            annualXbrlCollector.collectAnnualFilingMetadata(company, fiscalYear, "OFS");
            log.info("XBRL 연간 문서 fallback 적용 companyId={}, year={}, requestedFsDiv={}, resolvedFsDiv=OFS",
                    company.getCompanyId(), fiscalYear, requestedFsDiv);
            return "OFS";
        }
    }

    public record ExecutionResult(Long documentId, String resolvedFsDiv) {
    }
}
