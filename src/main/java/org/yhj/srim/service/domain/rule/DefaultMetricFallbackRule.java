package org.yhj.srim.service.domain.rule;

import org.springframework.stereotype.Component;
import org.yhj.srim.service.dto.XbrlFactView;
import org.yhj.srim.service.dto.XbrlRawBundle;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class DefaultMetricFallbackRule {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public Optional<DefaultMetricFallbackValues> resolve(XbrlRawBundle bundle,
                                                         XbrlFactView totalFact,
                                                         XbrlFactView ownerFact,
                                                         XbrlFactView noncontFact) {
        return resolve(bundle,
                totalFact == null ? null : totalFact.valueNumeric(),
                ownerFact == null ? null : ownerFact.valueNumeric(),
                noncontFact == null ? null : noncontFact.valueNumeric());
    }

    public Optional<DefaultMetricFallbackValues> resolve(XbrlRawBundle bundle,
                                                         BigDecimal totalValue,
                                                         BigDecimal ownerValue,
                                                         BigDecimal noncontValue) {
        if (ownerValue != null || noncontValue != null || totalValue == null) {
            return Optional.empty();
        }
        if (hasOwnershipBreakdownEvidence(bundle)) {
            return Optional.empty();
        }

        return Optional.of(new DefaultMetricFallbackValues(totalValue, ZERO));
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
}
