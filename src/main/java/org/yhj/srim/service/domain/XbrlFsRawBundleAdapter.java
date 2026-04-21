package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.yhj.srim.service.dto.FsRawBundle;
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

    private Map<String, BigDecimal> toMutableMetrics(XbrlRawBundle bundle) {
        return new LinkedHashMap<>(xbrlBaseMetricExtractor.extractBaseMetrics(bundle));
    }
}
