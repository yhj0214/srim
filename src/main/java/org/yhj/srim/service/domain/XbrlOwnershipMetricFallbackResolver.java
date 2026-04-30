package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.yhj.srim.service.dto.XbrlFactView;
import org.yhj.srim.service.dto.XbrlRawBundle;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class XbrlOwnershipMetricFallbackResolver {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final XbrlNetIncomeAttributionFallbackResolver xbrlNetIncomeAttributionFallbackResolver;

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

        return toOwnershipMetricValues(bundle, totalFact, resolvedOwner, resolvedNoncont);
    }

    public OwnershipMetricValues resolveEquityValues(XbrlRawBundle bundle,
                                                     XbrlFactView totalFact,
                                                     XbrlFactView ownerFact,
                                                     XbrlFactView noncontFact) {
        return toOwnershipMetricValues(bundle, totalFact, ownerFact, noncontFact);
    }

    private OwnershipMetricValues toOwnershipMetricValues(XbrlRawBundle bundle,
                                                          XbrlFactView totalFact,
                                                          XbrlFactView ownerFact,
                                                          XbrlFactView noncontFact) {
        if (canDefaultOwnerAndNoncont(ownerFact, noncontFact, totalFact, bundle)) {
            return new OwnershipMetricValues(totalFact.valueNumeric(), ZERO);
        }

        return new OwnershipMetricValues(valueOf(ownerFact), valueOf(noncontFact));
    }

    private boolean canDefaultOwnerAndNoncont(XbrlFactView ownerFact,
                                              XbrlFactView noncontFact,
                                              XbrlFactView totalFact,
                                              XbrlRawBundle bundle) {
        return ownerFact == null
                && noncontFact == null
                && totalFact != null
                && totalFact.valueNumeric() != null
                && !hasOwnershipBreakdownEvidence(bundle);
    }

    private boolean hasOwnershipBreakdownEvidence(XbrlRawBundle bundle) {
        return bundle.facts().stream().anyMatch(this::isOwnershipBreakdownFact);
    }

    private boolean isOwnershipBreakdownFact(XbrlFactView fact) {
        if (fact == null) {
            return false;
        }

        return containsOwnershipKeyword(fact.conceptQname())
                || containsOwnershipKeyword(fact.conceptLocalName())
                || containsOwnershipKeyword(fact.labelKo())
                || containsOwnershipKeyword(fact.contextRef())
                || containsOwnershipKeyword(fact.memberSignature());
    }

    private boolean containsOwnershipKeyword(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        List<String> keywords = List.of(
                "ownersofparent",
                "equityattributabletoownersofparent",
                "profitlossattributabletoownersofparent",
                "noncontrolling",
                "profitlossattributabletononcontrollinginterests",
                "비지배",
                "지배기업 소유주",
                "지배주주"
        );
        String normalized = value.toLowerCase();
        return keywords.stream().anyMatch(keyword -> normalized.contains(keyword.toLowerCase()));
    }

    private BigDecimal valueOf(XbrlFactView fact) {
        return fact == null ? null : fact.valueNumeric();
    }

    public record OwnershipMetricValues(BigDecimal ownerValue, BigDecimal noncontValue) {
    }
}
