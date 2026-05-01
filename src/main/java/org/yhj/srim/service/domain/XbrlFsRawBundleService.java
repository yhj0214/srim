package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.client.DartReportType;
import org.yhj.srim.service.domain.resolver.XbrlDocumentResolver;
import org.yhj.srim.service.dto.FsRawBundle;
import org.yhj.srim.service.dto.XbrlRawBundle;
import org.yhj.srim.service.dto.XbrlResolvedBundles;

@Service
@RequiredArgsConstructor
@Slf4j
public class XbrlFsRawBundleService {

    private final XbrlDocumentResolver xbrlDocumentResolver;
    private final XbrlFsRawBundleAdapter xbrlFsRawBundleAdapter;

    @Transactional(readOnly = true)
    public FsRawBundle buildAnnualRawBundle(Long companyId, int fiscalYear, String fsDiv) {
        return buildCanonicalAnnualRawBundle(companyId, fiscalYear, fsDiv);
    }

    @Transactional(readOnly = true)
    public FsRawBundle buildRawBundle(Long companyId, int fiscalYear, DartReportType reportType, String fsDiv) {
        if (reportType == DartReportType.ANNUAL) {
            return buildCanonicalAnnualRawBundle(companyId, fiscalYear, fsDiv);
        }

        XbrlResolvedBundles resolved = xbrlDocumentResolver.resolveBundles(companyId, fiscalYear, reportType, fsDiv);
        return xbrlFsRawBundleAdapter.adapt(resolved.current(), resolved.previous());
    }

    private FsRawBundle buildCanonicalAnnualRawBundle(Long companyId, int fiscalYear, String fsDiv) {
        XbrlRawBundle currentAnchor = xbrlDocumentResolver.resolveLatestAnnualBundleContainingYear(companyId, fiscalYear, fsDiv);
        XbrlRawBundle previousAnchor = xbrlDocumentResolver.resolveLatestAnnualBundleContainingYear(companyId, fiscalYear - 1, fsDiv);

        if (currentAnchor != null && currentAnchor.document() != null && currentAnchor.document().bsnsYear() != fiscalYear) {
            log.info("연간 XBRL 최신 비교표 기준 적용 companyId={}, year={}, anchorYear={}, fsDiv={}",
                    companyId, fiscalYear, currentAnchor.document().bsnsYear(), fsDiv);
        }

        return new FsRawBundle(
                xbrlFsRawBundleAdapter.extractMetricsForTargetYear(currentAnchor, fiscalYear),
                xbrlFsRawBundleAdapter.extractMetricsForTargetYear(previousAnchor, fiscalYear - 1)
        );
    }
}
