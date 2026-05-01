package org.yhj.srim.service.domain.resolver;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CommonError;
import org.yhj.srim.common.exception.code.StockError;
import org.yhj.srim.repository.CompanyRepository;
import org.yhj.srim.repository.DartFsFilingRepository;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.DartFsFiling;
import org.yhj.srim.service.dto.XbrlAnnualDocumentRef;

@Service
@RequiredArgsConstructor
public class XbrlAnnualDocumentLocator {

    private final CompanyRepository companyRepository;
    private final DartFsFilingRepository dartFsFilingRepository;

    @Transactional(readOnly = true)
    public XbrlAnnualDocumentRef resolve(Long companyId, int fiscalYear, String fsDiv) {
        Company company = companyRepository.findWithStockCodeByCompanyId(companyId)
                .orElseThrow(() -> new CustomException(StockError.COMPANY_NOT_FOUND, "companyId=" + companyId));

        String corpCode = company.getStockCode().getDartCorpCode();
        if (corpCode == null || corpCode.isBlank()) {
            throw new CustomException(StockError.DART_CORP_CODE_INVALID);
        }

        DartFsFiling filing = dartFsFilingRepository
                .findTopByCompanyIdAndBsnsYearAndReprtCodeAndFsDivOrderByRceptDtDescRceptNoDesc(
                        companyId,
                        fiscalYear,
                        "11011",
                        fsDiv
                )
                .orElseThrow(() -> new CustomException(
                        CommonError.INVALID_INPUT,
                        "companyId=" + companyId + ", fiscalYear=" + fiscalYear + ", fsDiv=" + fsDiv
                ));

        return new XbrlAnnualDocumentRef(corpCode, filing.getRceptNo(), fiscalYear, fsDiv);
    }
}
