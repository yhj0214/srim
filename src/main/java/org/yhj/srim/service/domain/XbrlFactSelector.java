package org.yhj.srim.service.domain;

import org.springframework.stereotype.Component;
import org.yhj.srim.service.dto.XbrlContextView;
import org.yhj.srim.service.dto.XbrlFactView;
import org.yhj.srim.service.dto.XbrlRawBundle;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
public class XbrlFactSelector {

    public List<XbrlFactView> findFactsByConcept(XbrlRawBundle bundle, String conceptQname) {
        return filterFacts(bundle, fact -> conceptQname.equals(fact.conceptQname()));
    }

    public Optional<XbrlFactView> findFirstFactByConcept(XbrlRawBundle bundle, String conceptQname) {
        return findFactsByConcept(bundle, conceptQname).stream().findFirst();
    }

    public List<XbrlFactView> findDurationFactsByConcept(XbrlRawBundle bundle, String conceptQname) {
        return filterFacts(bundle, fact -> conceptQname.equals(fact.conceptQname()), "duration", null);
    }

    public List<XbrlFactView> findInstantFactsByConcept(XbrlRawBundle bundle, String conceptQname) {
        return filterFacts(bundle, fact -> conceptQname.equals(fact.conceptQname()), "instant", null);
    }

    public List<XbrlFactView> findDurationFactsByLocalName(XbrlRawBundle bundle, String conceptLocalName) {
        return filterFacts(bundle, fact -> conceptLocalName.equals(fact.conceptLocalName()), "duration", null);
    }

    public List<XbrlFactView> findInstantFactsByLocalName(XbrlRawBundle bundle, String conceptLocalName) {
        return filterFacts(bundle, fact -> conceptLocalName.equals(fact.conceptLocalName()), "instant", null);
    }

    public List<XbrlFactView> findFactsByConceptAndMemberKeyword(XbrlRawBundle bundle,
                                                                 String conceptQname,
                                                                 String memberKeyword) {
        return filterFacts(bundle, fact -> conceptQname.equals(fact.conceptQname()), null, memberKeyword);
    }

    public List<XbrlFactView> findFactsByLocalNameAndMemberKeyword(XbrlRawBundle bundle,
                                                                   String conceptLocalName,
                                                                   String memberKeyword) {
        return filterFacts(bundle, fact -> conceptLocalName.equals(fact.conceptLocalName()), null, memberKeyword);
    }

    private List<XbrlFactView> filterFacts(XbrlRawBundle bundle, Predicate<XbrlFactView> predicate) {
        return filterFacts(bundle, predicate, null, null);
    }

    private List<XbrlFactView> filterFacts(XbrlRawBundle bundle,
                                           Predicate<XbrlFactView> predicate,
                                           String periodType,
                                           String memberKeyword) {
        Map<Long, XbrlContextView> contextById = bundle.contexts().stream()
                .collect(Collectors.toMap(XbrlContextView::xbrlContextId, context -> context));

        return bundle.facts().stream()
                .filter(predicate)
                .filter(fact -> matchesPeriodType(contextById.get(fact.xbrlContextId()), periodType))
                .filter(fact -> matchesMemberKeyword(contextById.get(fact.xbrlContextId()), memberKeyword))
                .sorted(preferredFactOrder(bundle, contextById))
                .toList();
    }

    private Comparator<XbrlFactView> preferredFactOrder(XbrlRawBundle bundle, Map<Long, XbrlContextView> contextById) {
        return Comparator
                .comparingInt((XbrlFactView fact) -> preferenceScore(bundle, contextById.get(fact.xbrlContextId()), fact))
                .thenComparing(fact -> contextDate(contextById.get(fact.xbrlContextId())), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(XbrlFactView::orderHint, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(XbrlFactView::xbrlFactId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private int preferenceScore(XbrlRawBundle bundle, XbrlContextView context, XbrlFactView fact) {
        int score = 0;
        int fiscalYear = bundle.document() != null && bundle.document().bsnsYear() != null
                ? bundle.document().bsnsYear()
                : -1;

        if (isCurrentYearContext(context, fiscalYear)) {
            score -= 1_000;
        } else if (isPreviousYearContext(context, fiscalYear)) {
            score += 1_000;
        } else if (isBeforePreviousYearContext(context, fiscalYear)) {
            score += 2_000;
        }

        String contextRef = context == null ? null : context.contextRef();
        if (containsIgnoreCase(contextRef, "CFY" + fiscalYear)) {
            score -= 500;
        } else if (containsIgnoreCase(contextRef, "CFY")) {
            score -= 400;
        } else if (containsIgnoreCase(contextRef, "PFY" + (fiscalYear - 1))) {
            score += 400;
        } else if (containsIgnoreCase(contextRef, "PFY")) {
            score += 500;
        } else if (containsIgnoreCase(contextRef, "BPFY")) {
            score += 700;
        }

        if (isSimpleConsolidatedContext(context, fact)) {
            score -= 200;
        } else if (hasAdditionalDimensions(context, fact)) {
            score += 200;
        }

        return score;
    }

    private boolean isCurrentYearContext(XbrlContextView context, int fiscalYear) {
        if (context == null || fiscalYear <= 0) {
            return false;
        }
        LocalDate contextDate = contextDate(context);
        return contextDate != null && contextDate.getYear() == fiscalYear;
    }

    private boolean isPreviousYearContext(XbrlContextView context, int fiscalYear) {
        if (context == null || fiscalYear <= 0) {
            return false;
        }
        LocalDate contextDate = contextDate(context);
        return contextDate != null && contextDate.getYear() == fiscalYear - 1;
    }

    private boolean isBeforePreviousYearContext(XbrlContextView context, int fiscalYear) {
        if (context == null || fiscalYear <= 0) {
            return false;
        }
        LocalDate contextDate = contextDate(context);
        return contextDate != null && contextDate.getYear() <= fiscalYear - 2;
    }

    private LocalDate contextDate(XbrlContextView context) {
        if (context == null) {
            return null;
        }
        if (context.instantDate() != null) {
            return context.instantDate();
        }
        return context.periodEnd();
    }

    private boolean isSimpleConsolidatedContext(XbrlContextView context, XbrlFactView fact) {
        String signature = context != null ? context.memberSignature() : fact.memberSignature();
        if (signature == null || signature.isBlank()) {
            return true;
        }
        return containsIgnoreCase(signature, "ConsolidatedMember") && !signature.contains("|");
    }

    private boolean hasAdditionalDimensions(XbrlContextView context, XbrlFactView fact) {
        String signature = context != null ? context.memberSignature() : fact.memberSignature();
        if (signature == null || signature.isBlank()) {
            return false;
        }
        return signature.contains("|");
    }

    private boolean matchesPeriodType(XbrlContextView context, String periodType) {
        if (periodType == null) {
            return true;
        }
        return context != null && periodType.equals(context.periodType());
    }

    private boolean matchesMemberKeyword(XbrlContextView context, String memberKeyword) {
        if (memberKeyword == null || memberKeyword.isBlank()) {
            return true;
        }
        if (context == null) {
            return false;
        }

        return containsIgnoreCase(context.memberSignature(), memberKeyword)
                || containsIgnoreCase(context.dimensionsJson(), memberKeyword);
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        return source != null && source.toLowerCase().contains(keyword.toLowerCase());
    }
}
