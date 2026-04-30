package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.client.DartReportType;
import org.yhj.srim.repository.XbrlDocumentRepository;
import org.yhj.srim.repository.entity.XbrlDocument;
import org.yhj.srim.service.dto.XbrlRawBundle;
import org.yhj.srim.service.dto.XbrlResolvedBundles;

import java.util.List;

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

        if (currentDocument == null) {
            return new XbrlResolvedBundles(emptyBundle(), null);
        }

        XbrlRawBundle current = xbrlRawReaderService.getDocumentBundle(currentDocument.getXbrlDocumentId());
        XbrlRawBundle previous = previousDocument == null ? null :
                xbrlRawReaderService.getDocumentBundle(previousDocument.getXbrlDocumentId());

        return new XbrlResolvedBundles(current, previous);
    }

    @Transactional(readOnly = true)
    public XbrlRawBundle resolveCurrentBundle(Long companyId, int fiscalYear, DartReportType reportType, String fsDiv) {
        XbrlDocument currentDocument = findLatestDocument(companyId, fiscalYear, reportType, fsDiv);
        if (currentDocument == null) {
            return null;
        }
        return xbrlRawReaderService.getDocumentBundle(currentDocument.getXbrlDocumentId());
    }

    @Transactional(readOnly = true)
    public XbrlRawBundle resolveLatestAnnualBundleContainingYear(Long companyId, int targetYear, String fsDiv) {
        for (int anchorYear = targetYear + 2; anchorYear >= targetYear; anchorYear--) {
            XbrlRawBundle bundle = resolveCurrentBundle(companyId, anchorYear, DartReportType.ANNUAL, fsDiv);
            if (bundle != null && !bundle.facts().isEmpty()) {
                return bundle;
            }
        }
        return null;
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

    private XbrlRawBundle emptyBundle() {
        return new XbrlRawBundle(null, List.of(), List.of());
    }
}
