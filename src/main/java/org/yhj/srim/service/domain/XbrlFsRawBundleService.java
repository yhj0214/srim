package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.client.DartReportType;
import org.yhj.srim.service.dto.FsRawBundle;
import org.yhj.srim.service.dto.XbrlResolvedBundles;

@Service
@RequiredArgsConstructor
public class XbrlFsRawBundleService {

    private final XbrlDocumentResolver xbrlDocumentResolver;
    private final XbrlFsRawBundleAdapter xbrlFsRawBundleAdapter;

    @Transactional(readOnly = true)
    public FsRawBundle buildAnnualRawBundle(Long companyId, int fiscalYear, String fsDiv) {
        return buildRawBundle(companyId, fiscalYear, DartReportType.ANNUAL, fsDiv);
    }

    @Transactional(readOnly = true)
    public FsRawBundle buildRawBundle(Long companyId, int fiscalYear, DartReportType reportType, String fsDiv) {

        XbrlResolvedBundles resolved = xbrlDocumentResolver.resolveBundles(companyId, fiscalYear, reportType, fsDiv);
        return xbrlFsRawBundleAdapter.adapt(resolved.current(), resolved.previous());
    }
}
