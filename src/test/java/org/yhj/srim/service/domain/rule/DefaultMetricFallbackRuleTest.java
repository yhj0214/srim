package org.yhj.srim.service.domain.rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yhj.srim.service.dto.XbrlFactView;
import org.yhj.srim.service.dto.XbrlRawBundle;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMetricFallbackRuleTest {

    private final DefaultMetricFallbackRule rule = new DefaultMetricFallbackRule();

    @Test
    @DisplayName("지배·비지배 fact와 관련 흔적이 모두 없으면 owner=total, noncont=0을 반환한다.")
    void resolve_returnsDefaultValuesWhenBreakdownIsMissing() {
        XbrlFactView totalFact = fact(1L, "ifrs-full:Equity", null, new BigDecimal("88416055935"));

        assertThat(rule.resolve(bundle(totalFact), totalFact, null, null))
                .contains(new DefaultMetricFallbackValues(new BigDecimal("88416055935"), BigDecimal.ZERO));
    }

    @Test
    @DisplayName("ownership 관련 흔적이 있으면 기본 fallback를 적용하지 않는다.")
    void resolve_returnsEmptyWhenOwnershipBreakdownEvidenceExists() {
        XbrlFactView totalFact = fact(1L, "ifrs-full:Equity", null, new BigDecimal("88416055935"));
        XbrlFactView ownerHintFact = fact(
                2L,
                "ifrs-full:ProfitLoss",
                "ifrs-full:AttributableToOwnersOfParentAxis=ifrs-full:OwnersOfParentMember",
                new BigDecimal("100")
        );

        assertThat(rule.resolve(bundle(totalFact, ownerHintFact), totalFact, null, null)).isEmpty();
    }

    @Test
    @DisplayName("이미 지배 또는 비지배 fact가 있으면 기본 fallback를 적용하지 않는다.")
    void resolve_returnsEmptyWhenOwnerOrNoncontExists() {
        XbrlFactView totalFact = fact(1L, "ifrs-full:Equity", null, new BigDecimal("88416055935"));
        XbrlFactView ownerFact = fact(2L, "ifrs-full:EquityAttributableToOwnersOfParent", null, new BigDecimal("88000000000"));

        assertThat(rule.resolve(bundle(totalFact, ownerFact), totalFact, ownerFact, null)).isEmpty();
    }

    private XbrlRawBundle bundle(XbrlFactView... facts) {
        return new XbrlRawBundle(null, List.of(), List.of(facts));
    }

    private XbrlFactView fact(Long factId, String conceptQname, String memberSignature, BigDecimal value) {
        return new XbrlFactView(
                factId,
                null,
                null,
                conceptQname,
                conceptQname == null ? null : conceptQname.substring(conceptQname.indexOf(':') + 1),
                null,
                null,
                null,
                null,
                value == null ? null : value.toPlainString(),
                value,
                false,
                memberSignature,
                1
        );
    }
}
