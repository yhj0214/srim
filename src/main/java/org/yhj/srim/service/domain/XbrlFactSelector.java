package org.yhj.srim.service.domain;

import org.springframework.stereotype.Component;
import org.yhj.srim.service.dto.XbrlContextView;
import org.yhj.srim.service.dto.XbrlFactView;
import org.yhj.srim.service.dto.XbrlRawBundle;

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

    public List<XbrlFactView> findFactsByConceptAndMemberKeyword(XbrlRawBundle bundle,
                                                                 String conceptQname,
                                                                 String memberKeyword) {
        return filterFacts(bundle, fact -> conceptQname.equals(fact.conceptQname()), null, memberKeyword);
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
                .toList();
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
