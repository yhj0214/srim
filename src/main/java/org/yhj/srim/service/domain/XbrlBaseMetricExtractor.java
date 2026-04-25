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
    private static final String METRIC_OP_INC = "OP_INC";
    private static final String METRIC_NET_INC = "NET_INC";
    private static final String METRIC_NET_INC_OWNER = "NET_INC_OWNER";
    private static final String METRIC_NET_INC_NONCONT = "NET_INC_NONCONT";
    private static final String METRIC_TOTAL_ASSETS = "TOTAL_ASSETS";
    private static final String METRIC_TOTAL_LIABILITIES = "TOTAL_LIABILITIES";
    private static final String METRIC_TOTAL_EQUITY = "TOTAL_EQUITY";
    private static final String METRIC_TOTAL_EQUITY_OWNER = "TOTAL_EQUITY_OWNER";
    private static final String METRIC_TOTAL_EQUITY_NONCONT = "TOTAL_EQUITY_NONCONT";
    private static final String METRIC_CURRENT_ASSETS = "CURRENT_ASSETS";
    private static final String METRIC_CURRENT_LIABILITIES = "CURRENT_LIABILITIES";

    private static final String[] CONCEPT_REVENUE = {"ifrs-full:Revenue", "ifrs:Revenue"};
    private static final String[] LOCAL_NAME_REVENUE = {"Revenue"};
    private static final String[] CONCEPT_OP_INC = {
            "ifrs-full:ProfitLossFromOperatingActivities",
            "ifrs:ProfitLossFromOperatingActivities"
    };
    private static final String[] LOCAL_NAME_OP_INC = {"ProfitLossFromOperatingActivities"};
    private static final String[] CONCEPT_PROFIT_LOSS = {"ifrs-full:ProfitLoss", "ifrs:ProfitLoss"};
    private static final String[] LOCAL_NAME_PROFIT_LOSS = {"ProfitLoss"};
    private static final String[] CONCEPT_PROFIT_LOSS_OWNER = {
            "ifrs-full:ProfitLossAttributableToOwnersOfParent",
            "ifrs:ProfitLossAttributableToOwnersOfParent"
    };
    private static final String[] LOCAL_NAME_PROFIT_LOSS_OWNER = {"ProfitLossAttributableToOwnersOfParent"};
    private static final String[] CONCEPT_PROFIT_LOSS_NONCONT = {
            "ifrs-full:ProfitLossAttributableToNoncontrollingInterests",
            "ifrs:ProfitLossAttributableToNoncontrollingInterests"
    };
    private static final String[] LOCAL_NAME_PROFIT_LOSS_NONCONT = {"ProfitLossAttributableToNoncontrollingInterests"};
    private static final String[] CONCEPT_TOTAL_ASSETS = {"ifrs-full:Assets", "ifrs:Assets"};
    private static final String[] LOCAL_NAME_TOTAL_ASSETS = {"Assets"};
    private static final String[] CONCEPT_TOTAL_LIABILITIES = {"ifrs-full:Liabilities", "ifrs:Liabilities"};
    private static final String[] LOCAL_NAME_TOTAL_LIABILITIES = {"Liabilities"};
    private static final String[] CONCEPT_EQUITY = {"ifrs-full:Equity", "ifrs:Equity"};
    private static final String[] LOCAL_NAME_EQUITY = {"Equity"};
    private static final String[] CONCEPT_EQUITY_OWNER = {
            "ifrs-full:EquityAttributableToOwnersOfParent",
            "ifrs:EquityAttributableToOwnersOfParent"
    };
    private static final String[] LOCAL_NAME_EQUITY_OWNER = {"EquityAttributableToOwnersOfParent"};
    private static final String[] CONCEPT_NONCONTROLLING_INTERESTS = {
            "ifrs-full:NoncontrollingInterests",
            "ifrs:NoncontrollingInterests"
    };
    private static final String[] LOCAL_NAME_NONCONTROLLING_INTERESTS = {"NoncontrollingInterests"};
    private static final String[] CONCEPT_CURRENT_ASSETS = {"ifrs-full:CurrentAssets", "ifrs:CurrentAssets"};
    private static final String[] LOCAL_NAME_CURRENT_ASSETS = {"CurrentAssets"};
    private static final String[] CONCEPT_CURRENT_LIABILITIES = {"ifrs-full:CurrentLiabilities", "ifrs:CurrentLiabilities"};
    private static final String[] LOCAL_NAME_CURRENT_LIABILITIES = {"CurrentLiabilities"};

    private static final String MEMBER_KEYWORD_OWNER = "ownersofparent";
    private static final String MEMBER_KEYWORD_NONCONT = "noncontrolling";

    private final XbrlFactSelector xbrlFactSelector;

    public Map<String, BigDecimal> extractBaseMetrics(XbrlRawBundle bundle) {
        Map<String, BigDecimal> metrics = new LinkedHashMap<>();

        putIfPresent(metrics, METRIC_SALES,
                firstPresentDurationFact(bundle, CONCEPT_REVENUE, LOCAL_NAME_REVENUE));
        putIfPresent(metrics, METRIC_OP_INC,
                firstPresentDurationFact(bundle, CONCEPT_OP_INC, LOCAL_NAME_OP_INC));
        putIfPresent(metrics, METRIC_NET_INC,
                firstPresentDurationFact(bundle, CONCEPT_PROFIT_LOSS, LOCAL_NAME_PROFIT_LOSS));
        putIfPresent(metrics, METRIC_NET_INC_OWNER, firstNonNull(
                firstPresentDurationFact(bundle, CONCEPT_PROFIT_LOSS_OWNER, LOCAL_NAME_PROFIT_LOSS_OWNER),
                firstPresentMemberFact(bundle, CONCEPT_PROFIT_LOSS, LOCAL_NAME_PROFIT_LOSS, MEMBER_KEYWORD_OWNER)
        ));
        putIfPresent(metrics, METRIC_NET_INC_NONCONT, firstNonNull(
                firstPresentDurationFact(bundle, CONCEPT_PROFIT_LOSS_NONCONT, LOCAL_NAME_PROFIT_LOSS_NONCONT),
                firstPresentMemberFact(bundle, CONCEPT_PROFIT_LOSS, LOCAL_NAME_PROFIT_LOSS, MEMBER_KEYWORD_NONCONT)
        ));
        putIfPresent(metrics, METRIC_TOTAL_ASSETS,
                firstPresentInstantFact(bundle, CONCEPT_TOTAL_ASSETS, LOCAL_NAME_TOTAL_ASSETS));
        putIfPresent(metrics, METRIC_TOTAL_LIABILITIES,
                firstPresentInstantFact(bundle, CONCEPT_TOTAL_LIABILITIES, LOCAL_NAME_TOTAL_LIABILITIES));
        putIfPresent(metrics, METRIC_TOTAL_EQUITY,
                firstPresentInstantFact(bundle, CONCEPT_EQUITY, LOCAL_NAME_EQUITY));
        putIfPresent(metrics, METRIC_TOTAL_EQUITY_OWNER, firstNonNull(
                firstPresentInstantFact(bundle, CONCEPT_EQUITY_OWNER, LOCAL_NAME_EQUITY_OWNER),
                firstPresentMemberFact(bundle, CONCEPT_EQUITY, LOCAL_NAME_EQUITY, MEMBER_KEYWORD_OWNER)
        ));
        putIfPresent(metrics, METRIC_TOTAL_EQUITY_NONCONT, firstNonNull(
                firstPresentInstantFact(bundle, CONCEPT_NONCONTROLLING_INTERESTS, LOCAL_NAME_NONCONTROLLING_INTERESTS),
                firstPresentMemberFact(bundle, CONCEPT_EQUITY, LOCAL_NAME_EQUITY, MEMBER_KEYWORD_NONCONT)
        ));
        putIfPresent(metrics, METRIC_CURRENT_ASSETS,
                firstPresentInstantFact(bundle, CONCEPT_CURRENT_ASSETS, LOCAL_NAME_CURRENT_ASSETS));
        putIfPresent(metrics, METRIC_CURRENT_LIABILITIES,
                firstPresentInstantFact(bundle, CONCEPT_CURRENT_LIABILITIES, LOCAL_NAME_CURRENT_LIABILITIES));

        return metrics;
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

    private XbrlFactView firstPresentInstantFact(XbrlRawBundle bundle, String[] conceptQnames, String[] conceptLocalNames) {
        for (String conceptQname : conceptQnames) {
            XbrlFactView fact = xbrlFactSelector.findInstantFactsByConcept(bundle, conceptQname)
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (fact != null) {
                return fact;
            }
        }
        for (String conceptLocalName : conceptLocalNames) {
            XbrlFactView fact = xbrlFactSelector.findInstantFactsByLocalName(bundle, conceptLocalName)
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (fact != null) {
                return fact;
            }
        }
        return null;
    }

    private XbrlFactView firstPresentMemberFact(XbrlRawBundle bundle,
                                                String[] conceptQnames,
                                                String[] conceptLocalNames,
                                                String memberKeyword) {
        for (String conceptQname : conceptQnames) {
            XbrlFactView fact = xbrlFactSelector.findFactsByConceptAndMemberKeyword(bundle, conceptQname, memberKeyword)
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (fact != null) {
                return fact;
            }
        }
        for (String conceptLocalName : conceptLocalNames) {
            XbrlFactView fact = xbrlFactSelector.findFactsByLocalNameAndMemberKeyword(bundle, conceptLocalName, memberKeyword)
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (fact != null) {
                return fact;
            }
        }
        return null;
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
