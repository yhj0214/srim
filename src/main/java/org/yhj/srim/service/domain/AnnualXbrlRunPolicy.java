package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CommonError;
import org.yhj.srim.common.exception.code.CrawlingError;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnnualXbrlRunPolicy {
    private final AnnualXbrlMetricProcessor annualXbrlMetricProcessor;

    public boolean shouldFallbackToOfs(String requestedFsDiv, CustomException e) {
        return "CFS".equalsIgnoreCase(requestedFsDiv)
                && (e.getErrorCode() == CrawlingError.DART_DISCLOSURE_NOT_FOUND
                || e.getErrorCode() == CommonError.INVALID_INPUT);
    }

    public boolean shouldFallbackToOfsOnMissingXbrl(String requestedFsDiv, String resolvedFsDiv, CustomException e) {
        return "CFS".equalsIgnoreCase(requestedFsDiv)
                && "CFS".equalsIgnoreCase(resolvedFsDiv)
                && e.getErrorCode() == CrawlingError.DART_XBRL_NOT_AVAILABLE;
    }

    public String resolveAnnualProcessingFsDiv(Long companyId, int fiscalYear, String requestedFsDiv) {
        if (annualXbrlMetricProcessor.hasAnnualXbrlRaw(companyId, fiscalYear, requestedFsDiv)) {
            return requestedFsDiv;
        }
        if ("CFS".equalsIgnoreCase(requestedFsDiv)
                && annualXbrlMetricProcessor.hasAnnualXbrlRaw(companyId, fiscalYear, "OFS")) {
            log.info("XBRL 연간 metric 처리 fallback 적용 companyId={}, year={}, requestedFsDiv={}, resolvedFsDiv=OFS",
                    companyId, fiscalYear, requestedFsDiv);
            return "OFS";
        }
        return requestedFsDiv;
    }
}
