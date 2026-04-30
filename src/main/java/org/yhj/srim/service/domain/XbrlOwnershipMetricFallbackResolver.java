package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.yhj.srim.service.dto.XbrlFactView;
import org.yhj.srim.service.dto.XbrlRawBundle;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;

@Component
@RequiredArgsConstructor
public class XbrlOwnershipMetricFallbackResolver {

    private final XbrlNetIncomeAttributionFallbackResolver xbrlNetIncomeAttributionFallbackResolver;
    private final DefaultOwnershipMetricFallbackRule defaultOwnershipMetricFallbackRule;

    public OwnershipMetricValues resolveNetIncomeValues(XbrlRawBundle bundle,
                                                        XbrlFactView totalFact,
                                                        XbrlFactView ownerFact,
                                                        XbrlFactView noncontFact) {
        XbrlFactView resolvedOwner = ownerFact;
        XbrlFactView resolvedNoncont = noncontFact;

        if (resolvedOwner == null || resolvedNoncont == null) {
            XbrlNetIncomeAttributionFallbackResolver.ResolvedNetIncomeAttribution fallback =
                    xbrlNetIncomeAttributionFallbackResolver.resolve(bundle, totalFact).orElse(null);
            if (fallback != null) {
                if (resolvedOwner == null) {
                    resolvedOwner = fallback.ownerFact();
                }
                if (resolvedNoncont == null) {
                    resolvedNoncont = fallback.noncontFact();
                }
            }
        }

        Optional<OwnershipMetricValues> fallback =
                defaultOwnershipMetricFallbackRule.resolve(bundle, totalFact, resolvedOwner, resolvedNoncont);
        if (fallback.isPresent()) {
            return fallback.get();
        }
        return new OwnershipMetricValues(valueOf(resolvedOwner), valueOf(resolvedNoncont));
    }

    public OwnershipMetricValues resolveEquityValues(XbrlRawBundle bundle,
                                                     XbrlFactView totalFact,
                                                     XbrlFactView ownerFact,
                                                     XbrlFactView noncontFact) {
        Optional<OwnershipMetricValues> fallback =
                defaultOwnershipMetricFallbackRule.resolve(bundle, totalFact, ownerFact, noncontFact);
        if (fallback.isPresent()) {
            return fallback.get();
        }
        return new OwnershipMetricValues(valueOf(ownerFact), valueOf(noncontFact));
    }

    private BigDecimal valueOf(XbrlFactView fact) {
        return fact == null ? null : fact.valueNumeric();
    }
}
