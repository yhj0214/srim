package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.service.domain.resolver.XbrlAnnualDocumentLocator;
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
        // xbrl document를 확보
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

    /**
     * 처음에는 요청된 fsDiv로 메타데이터 수집을 시도하고, 실패할 경우 OFS로 fallback하여 메타데이터 수집을 시도함.
     * fsfiling데이터 수집 및 저장
     */
    private String resolveAnnualFsDiv(Company company, int fiscalYear, String requestedFsDiv) {
        try {
            annualXbrlCollector.collectAnnualFilingMetadata(company, fiscalYear, requestedFsDiv);
            return requestedFsDiv;
        } catch (CustomException e) {
            // 이미 OFS로 fallback 했는데도 메타데이터 수집에 실패한 경우에는 더 이상 대체할 fsDiv가 없으므로 예외를 던짐.
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
