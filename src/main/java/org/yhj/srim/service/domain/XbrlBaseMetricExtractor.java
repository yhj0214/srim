package org.yhj.srim.service.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.yhj.srim.service.dto.XbrlFactView;
import org.yhj.srim.service.dto.XbrlRawBundle;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class XbrlBaseMetricExtractor {

    private static final String METRIC_SALES = "SALES";
    private static final String METRIC_NET_INC = "NET_INC";
    private static final String METRIC_NET_INC_OWNER = "NET_INC_OWNER";
    private static final String METRIC_NET_INC_NONCONT = "NET_INC_NONCONT";
    private static final String METRIC_TOTAL_EQUITY = "TOTAL_EQUITY";
    private static final String METRIC_TOTAL_EQUITY_OWNER = "TOTAL_EQUITY_OWNER";
    private static final String METRIC_TOTAL_EQUITY_NONCONT = "TOTAL_EQUITY_NONCONT";

    private static final String CONCEPT_REVENUE = "ifrs-full:Revenue";
    private static final String CONCEPT_PROFIT_LOSS = "ifrs-full:ProfitLoss";
    private static final String CONCEPT_PROFIT_LOSS_OWNER = "ifrs-full:ProfitLossAttributableToOwnersOfParent";
    private static final String CONCEPT_PROFIT_LOSS_NONCONT = "ifrs-full:ProfitLossAttributableToNoncontrollingInterests";
    private static final String CONCEPT_EQUITY = "ifrs-full:Equity";
    private static final String CONCEPT_EQUITY_OWNER = "ifrs-full:EquityAttributableToOwnersOfParent";
    private static final String CONCEPT_NONCONTROLLING_INTERESTS = "ifrs-full:NoncontrollingInterests";

    private static final String MEMBER_KEYWORD_OWNER = "ownersofparent";
    private static final String MEMBER_KEYWORD_NONCONT = "noncontrolling";

    private final XbrlFactSelector xbrlFactSelector;

    public Map<String, BigDecimal> extractBaseMetrics(XbrlRawBundle bundle) {
        Map<String, BigDecimal> metrics = new LinkedHashMap<>();

        putIfPresent(metrics, METRIC_SALES,
                xbrlFactSelector.findDurationFactsByConcept(bundle, CONCEPT_REVENUE).stream().findFirst().orElse(null));
        putIfPresent(metrics, METRIC_NET_INC,
                xbrlFactSelector.findDurationFactsByConcept(bundle, CONCEPT_PROFIT_LOSS).stream().findFirst().orElse(null));
        putIfPresent(metrics, METRIC_NET_INC_OWNER, firstNonNull(
                xbrlFactSelector.findDurationFactsByConcept(bundle, CONCEPT_PROFIT_LOSS_OWNER).stream().findFirst().orElse(null),
                xbrlFactSelector.findFactsByConceptAndMemberKeyword(bundle, CONCEPT_PROFIT_LOSS, MEMBER_KEYWORD_OWNER)
                        .stream().findFirst().orElse(null)
        ));
        putIfPresent(metrics, METRIC_NET_INC_NONCONT, firstNonNull(
                xbrlFactSelector.findDurationFactsByConcept(bundle, CONCEPT_PROFIT_LOSS_NONCONT).stream().findFirst().orElse(null),
                xbrlFactSelector.findFactsByConceptAndMemberKeyword(bundle, CONCEPT_PROFIT_LOSS, MEMBER_KEYWORD_NONCONT)
                        .stream().findFirst().orElse(null)
        ));
        putIfPresent(metrics, METRIC_TOTAL_EQUITY,
                xbrlFactSelector.findInstantFactsByConcept(bundle, CONCEPT_EQUITY).stream().findFirst().orElse(null));
        putIfPresent(metrics, METRIC_TOTAL_EQUITY_OWNER, firstNonNull(
                xbrlFactSelector.findInstantFactsByConcept(bundle, CONCEPT_EQUITY_OWNER).stream().findFirst().orElse(null),
                xbrlFactSelector.findFactsByConceptAndMemberKeyword(bundle, CONCEPT_EQUITY, MEMBER_KEYWORD_OWNER)
                        .stream().findFirst().orElse(null)
        ));
        putIfPresent(metrics, METRIC_TOTAL_EQUITY_NONCONT, firstNonNull(
                xbrlFactSelector.findInstantFactsByConcept(bundle, CONCEPT_NONCONTROLLING_INTERESTS).stream().findFirst().orElse(null),
                xbrlFactSelector.findFactsByConceptAndMemberKeyword(bundle, CONCEPT_EQUITY, MEMBER_KEYWORD_NONCONT)
                        .stream().findFirst().orElse(null)
        ));

        return metrics;
    }

    private void putIfPresent(Map<String, BigDecimal> metrics, String metricCode, XbrlFactView fact) {
        if (fact == null || fact.valueNumeric() == null) {
            return;
        }
        metrics.put(metricCode, fact.valueNumeric());
    }

    private XbrlFactView firstNonNull(XbrlFactView primary, XbrlFactView fallback) {
        return primary != null ? primary : fallback;
    }
}
