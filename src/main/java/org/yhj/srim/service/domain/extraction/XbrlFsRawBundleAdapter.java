package org.yhj.srim.service.domain.extraction;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.yhj.srim.service.dto.FsRawBundle;
import org.yhj.srim.service.dto.XbrlDocumentView;
import org.yhj.srim.service.dto.XbrlRawBundle;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class XbrlFsRawBundleAdapter {

    private final XbrlBaseMetricExtractor xbrlBaseMetricExtractor;

    public FsRawBundle adapt(XbrlRawBundle currentBundle, XbrlRawBundle previousBundle) {
        Map<String, BigDecimal> curr = toMutableMetrics(currentBundle);
        Map<String, BigDecimal> prev = previousBundle == null
                ? new LinkedHashMap<>()
                : toMutableMetrics(previousBundle);

        return new FsRawBundle(curr, prev);
    }

    public FsRawBundle adapt(XbrlRawBundle currentBundle) {
        return adapt(currentBundle, null);
    }

    public Map<String, BigDecimal> extractMetricsForTargetYear(XbrlRawBundle bundle, int targetYear) {
        if (bundle == null) {
            return new LinkedHashMap<>();
        }
        return toMutableMetrics(withFiscalYear(bundle, targetYear));
    }

    private Map<String, BigDecimal> toMutableMetrics(XbrlRawBundle bundle) {
        return new LinkedHashMap<>(xbrlBaseMetricExtractor.extractBaseMetrics(bundle));
    }

    private XbrlRawBundle withFiscalYear(XbrlRawBundle bundle, int fiscalYear) {
        if (bundle == null || bundle.document() == null) {
            return bundle;
        }

        XbrlDocumentView document = bundle.document();
        XbrlDocumentView documentWithFiscalYear = new XbrlDocumentView(
                document.xbrlDocumentId(),
                document.corpCode(),
                document.companyId(),
                document.rceptNo(),
                document.reprtCode(),
                fiscalYear,
                document.fsDiv(),
                document.reportTp(),
                document.sourceUrl(),
                document.localPath(),
                document.taxonomyVersion(),
                document.parseVersion(),
                document.parsedAt()
        );

        return new XbrlRawBundle(documentWithFiscalYear, bundle.contexts(), bundle.facts());
    }
}
