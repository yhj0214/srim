package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.yhj.srim.service.dto.XbrlContextView;
import org.yhj.srim.service.dto.XbrlFactView;
import org.yhj.srim.service.dto.XbrlRawBundle;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AkHoldingsMetricOverrideRule implements XbrlCompanyMetricOverrideRule {

    private static final String AK_HOLDINGS_CORP_CODE = "00125080";
    private static final String METRIC_NET_INC = "NET_INC";
    private static final String METRIC_NET_INC_OWNER = "NET_INC_OWNER";
    private static final String METRIC_NET_INC_NONCONT = "NET_INC_NONCONT";
    private static final String[] CONCEPT_PROFIT_LOSS = {"ifrs-full:ProfitLoss", "ifrs:ProfitLoss"};
    private static final String[] LOCAL_NAME_PROFIT_LOSS = {"ProfitLoss"};
    private static final String MEMBER_KEYWORD_OWNER = "ownersofparent";
    private static final String MEMBER_KEYWORD_NONCONT = "noncontrolling";
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("1000");

    private final XbrlFactSelector xbrlFactSelector;

    @Override
    public XbrlCompanyMetricOverrideType type() {
        return XbrlCompanyMetricOverrideType.AK_HOLDINGS;
    }

    @Override
    public Map<String, BigDecimal> resolveOverrides(XbrlRawBundle bundle, Map<String, BigDecimal> baseMetrics) {
        boolean missingOwner = !baseMetrics.containsKey(METRIC_NET_INC_OWNER);
        boolean missingNoncont = !baseMetrics.containsKey(METRIC_NET_INC_NONCONT);
        if ((!missingOwner && !missingNoncont) || !baseMetrics.containsKey(METRIC_NET_INC)) {
            return Map.of();
        }

        XbrlFactView totalProfitLoss = firstPresentDurationFact(bundle, CONCEPT_PROFIT_LOSS, LOCAL_NAME_PROFIT_LOSS);
        Optional<ResolvedNetIncomeAttribution> resolved = resolve(bundle, totalProfitLoss);
        if (resolved.isEmpty()) {
            return Map.of();
        }

        Map<String, BigDecimal> overrides = new LinkedHashMap<>();
        if (missingOwner) {
            overrides.put(METRIC_NET_INC_OWNER, resolved.get().ownerFact().valueNumeric());
        }
        if (missingNoncont) {
            overrides.put(METRIC_NET_INC_NONCONT, resolved.get().noncontFact().valueNumeric());
        }
        return overrides;
    }

    private Optional<ResolvedNetIncomeAttribution> resolve(XbrlRawBundle bundle, XbrlFactView totalProfitLoss) {
        if (!supports(bundle) || totalProfitLoss == null || totalProfitLoss.valueNumeric() == null) {
            return Optional.empty();
        }

        Map<Long, XbrlContextView> contextById = bundle.contexts().stream()
                .collect(Collectors.toMap(XbrlContextView::xbrlContextId, context -> context));

        List<XbrlFactView> customProfitLossFacts = bundle.facts().stream()
                .filter(isCustomProfitLossFact())
                .filter(fact -> isSimpleConsolidatedDurationFact(contextById.get(fact.xbrlContextId()), fact))
                .toList();

        if (customProfitLossFacts.size() < 2) {
            return Optional.empty();
        }

        for (XbrlFactView ownerCandidate : customProfitLossFacts) {
            if (!hasMatchingMemberValue(bundle, contextById, ownerCandidate.valueNumeric(), MEMBER_KEYWORD_OWNER)) {
                continue;
            }

            for (XbrlFactView noncontCandidate : customProfitLossFacts) {
                if (ownerCandidate.xbrlFactId().equals(noncontCandidate.xbrlFactId())) {
                    continue;
                }
                if (!hasMatchingMemberValue(bundle, contextById, noncontCandidate.valueNumeric(), MEMBER_KEYWORD_NONCONT)) {
                    continue;
                }
                if (!matchesTotal(totalProfitLoss.valueNumeric(), ownerCandidate.valueNumeric(), noncontCandidate.valueNumeric())) {
                    continue;
                }

                return Optional.of(new ResolvedNetIncomeAttribution(
                        ownerCandidate,
                        noncontCandidate
                ));
            }
        }

        return Optional.empty();
    }

    private boolean supports(XbrlRawBundle bundle) {
        return bundle != null
                && bundle.document() != null
                && AK_HOLDINGS_CORP_CODE.equals(bundle.document().corpCode());
    }

    private Predicate<XbrlFactView> isCustomProfitLossFact() {
        return fact -> fact != null
                && fact.valueNumeric() != null
                && fact.conceptQname() != null
                && fact.conceptQname().contains(":")
                && !fact.conceptQname().startsWith("ifrs-full:")
                && !fact.conceptQname().startsWith("ifrs:")
                && containsIgnoreCase(fact.conceptQname(), "ProfitLoss")
                && containsIgnoreCase(fact.conceptLocalName(), "ProfitLoss");
    }

    private boolean isSimpleConsolidatedDurationFact(XbrlContextView context, XbrlFactView fact) {
        if (context == null || !"duration".equals(context.periodType())) {
            return false;
        }

        String signature = context.memberSignature() != null ? context.memberSignature() : fact.memberSignature();
        return containsIgnoreCase(signature, "ConsolidatedMember") && !signature.contains("|");
    }

    private boolean hasMatchingMemberValue(XbrlRawBundle bundle,
                                           Map<Long, XbrlContextView> contextById,
                                           BigDecimal expectedValue,
                                           String memberKeyword) {
        return bundle.facts().stream()
                .filter(fact -> fact.valueNumeric() != null)
                .filter(fact -> sameValue(fact.valueNumeric(), expectedValue))
                .anyMatch(fact -> matchesMemberKeyword(contextById.get(fact.xbrlContextId()), fact, memberKeyword));
    }

    private boolean matchesMemberKeyword(XbrlContextView context, XbrlFactView fact, String memberKeyword) {
        String signature = context != null ? context.memberSignature() : fact.memberSignature();
        String dimensions = context != null ? context.dimensionsJson() : null;

        return containsIgnoreCase(signature, "ConsolidatedMember")
                && containsIgnoreCase(signature, memberKeyword)
                && !containsIgnoreCase(signature, "SegmentsAxis")
                && !containsIgnoreCase(dimensions, "SegmentsAxis");
    }

    private boolean matchesTotal(BigDecimal total, BigDecimal owner, BigDecimal noncont) {
        return sameValue(total, owner.add(noncont));
    }

    private boolean sameValue(BigDecimal left, BigDecimal right) {
        return left != null
                && right != null
                && left.subtract(right).abs().compareTo(AMOUNT_TOLERANCE) <= 0;
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        return source != null && keyword != null && source.toLowerCase().contains(keyword.toLowerCase());
    }

    private XbrlFactView firstPresentDurationFact(XbrlRawBundle bundle, String[] conceptQnames, String[] conceptLocalNames) {
        for (String conceptQname : conceptQnames) {
            XbrlFactView fact = xbrlFactSelector.findDurationFactsByConcept(bundle, conceptQname)
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (fact != null) {
                return fact;
            }
        }
        for (String conceptLocalName : conceptLocalNames) {
            XbrlFactView fact = xbrlFactSelector.findDurationFactsByLocalName(bundle, conceptLocalName)
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (fact != null) {
                return fact;
            }
        }
        return null;
    }

    private record ResolvedNetIncomeAttribution(
            XbrlFactView ownerFact,
            XbrlFactView noncontFact
    ) {
    }
}
