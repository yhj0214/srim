package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.client.DartReportType;
import org.yhj.srim.repository.XbrlDocumentRepository;
import org.yhj.srim.repository.entity.XbrlDocument;
import org.yhj.srim.service.dto.XbrlRawBundle;
import org.yhj.srim.service.dto.XbrlResolvedBundles;

@Service
@RequiredArgsConstructor
public class XbrlDocumentResolver {

    private final XbrlDocumentRepository xbrlDocumentRepository;
    private final XbrlRawReaderService xbrlRawReaderService;

    @Transactional(readOnly = true)
    public XbrlResolvedBundles resolveAnnualBundles(Long companyId, int fiscalYear, String fsDiv) {
        return resolveBundles(companyId, fiscalYear, DartReportType.ANNUAL, fsDiv);
    }

    @Transactional(readOnly = true)
    public XbrlResolvedBundles resolveBundles(Long companyId, int fiscalYear, DartReportType reportType, String fsDiv) {

        XbrlDocument currentDocument = findLatestDocument(companyId, fiscalYear, reportType, fsDiv);
        XbrlDocument previousDocument = findLatestDocument(companyId, fiscalYear - 1, reportType, fsDiv);

        XbrlRawBundle current = xbrlRawReaderService.getDocumentBundle(currentDocument.getXbrlDocumentId());
        XbrlRawBundle previous = previousDocument == null ? null :
                xbrlRawReaderService.getDocumentBundle(previousDocument.getXbrlDocumentId());

        return new XbrlResolvedBundles(current, previous);
    }

    private XbrlDocument findLatestDocument(Long companyId, int fiscalYear, DartReportType reportType, String fsDiv) {

        return xbrlDocumentRepository
                .findTopByCompany_CompanyIdAndBsnsYearAndReprtCodeAndFsDivOrderByParsedAtDescRceptNoDesc(
                        companyId,
                        fiscalYear,
                        reportType.code(),
                        fsDiv
                )
                .orElse(null);
    }
}
