package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.yhj.srim.service.dto.XbrlFactView;
import org.yhj.srim.service.dto.XbrlRawBundle;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class XbrlNetIncomeAttributionFallbackResolver {

    private final List<XbrlNetIncomeAttributionFallbackRule> rules;

    public Optional<ResolvedNetIncomeAttribution> resolve(XbrlRawBundle bundle, XbrlFactView totalProfitLoss) {
        for (XbrlNetIncomeAttributionFallbackRule rule : rules) {
            Optional<ResolvedNetIncomeAttribution> resolved = rule.resolve(bundle, totalProfitLoss);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    public record ResolvedNetIncomeAttribution(
            XbrlFactView ownerFact,
            XbrlFactView noncontFact
    ) {
    }
}
