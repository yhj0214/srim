package org.yhj.srim.service.domain;

import org.springframework.stereotype.Component;
import org.yhj.srim.service.dto.XbrlFactView;
import org.yhj.srim.service.dto.XbrlRawBundle;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class DefaultOwnershipMetricFallbackRule {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public Optional<OwnershipMetricValues> resolve(XbrlRawBundle bundle,
                                                   XbrlFactView totalFact,
                                                   XbrlFactView ownerFact,
                                                   XbrlFactView noncontFact) {
        if (ownerFact != null || noncontFact != null || totalFact == null || totalFact.valueNumeric() == null) {
            return Optional.empty();
        }
        if (hasOwnershipBreakdownEvidence(bundle)) {
            return Optional.empty();
        }

        return Optional.of(new OwnershipMetricValues(totalFact.valueNumeric(), ZERO));
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
