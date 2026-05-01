package org.yhj.srim.service.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yhj.srim.service.dto.XbrlContextView;
import org.yhj.srim.service.dto.XbrlDocumentView;
import org.yhj.srim.service.dto.XbrlFactView;
import org.yhj.srim.service.dto.XbrlRawBundle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class XbrlCompanyMetricOverrideResolverTest {

    private final XbrlCompanyMetricOverrideResolver resolver =
            new XbrlCompanyMetricOverrideResolver(
                    List.of(new AkHoldingsMetricOverrideRule(new XbrlFactSelector()))
            );

    @Test
    @DisplayName("등록된 회사는 필요한 metric만 partial override한다.")
    void resolveOverrides_appliesPartialOverrideForRegisteredCompany() {
        Map<String, BigDecimal> baseMetrics = new LinkedHashMap<>();
        baseMetrics.put("NET_INC", new BigDecimal("57377832577"));

        assertThat(resolver.resolveOverrides(akHoldingsBundle(), baseMetrics))
                .containsEntry("NET_INC_OWNER", new BigDecimal("28278294009"))
                .containsEntry("NET_INC_NONCONT", new BigDecimal("29099538568"));
    }

    @Test
    @DisplayName("이미 있는 metric은 유지하고 비어 있는 metric만 partial override한다.")
    void resolveOverrides_onlyOverridesMissingMetrics() {
        Map<String, BigDecimal> baseMetrics = new LinkedHashMap<>();
        baseMetrics.put("NET_INC", new BigDecimal("57377832577"));
        baseMetrics.put("NET_INC_OWNER", new BigDecimal("111"));

        assertThat(resolver.resolveOverrides(akHoldingsBundle(), baseMetrics))
                .doesNotContainKey("NET_INC_OWNER")
                .containsEntry("NET_INC_NONCONT", new BigDecimal("29099538568"));
    }

    @Test
    @DisplayName("등록되지 않은 회사는 회사별 override를 적용하지 않는다.")
    void resolveOverrides_returnsEmptyForUnregisteredCompany() {
        Map<String, BigDecimal> baseMetrics = Map.of("NET_INC", new BigDecimal("100"));
        XbrlRawBundle bundle = new XbrlRawBundle(
                new XbrlDocumentView(
                        1L,
                        "99999999",
                        1L,
                        "20250321000001",
                        "11011",
                        2024,
                        "CFS",
                        "연간",
                        null,
                        null,
                        null,
                        null,
                        LocalDateTime.of(2025, 3, 21, 0, 0)
                ),
                List.of(),
                List.of()
        );

        assertThat(resolver.resolveOverrides(bundle, baseMetrics)).isEmpty();
    }

    private XbrlRawBundle akHoldingsBundle() {
        return new XbrlRawBundle(
                new XbrlDocumentView(
                        1L,
                        "00125080",
                        1L,
                        "20200330003219",
                        "11011",
                        2019,
                        "CFS",
                        "연간",
                        null,
                        null,
                        null,
                        null,
                        LocalDateTime.of(2020, 3, 30, 0, 0)
                ),
                List.of(
                        context(1L, "CFY2019dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember", "duration", null),
                        context(2L, "CFY2019dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember_ifrs-full_ComponentsOfEquityAxis_ifrs-full_EquityAttributableToOwnersOfParentMember", "duration",
                                "ifrs-full:ComponentsOfEquityAxis=ifrs-full:EquityAttributableToOwnersOfParentMember|ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember"),
                        context(3L, "CFY2019dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember_ifrs-full_ComponentsOfEquityAxis_ifrs-full_NoncontrollingInterestsMember", "duration",
                                "ifrs-full:ComponentsOfEquityAxis=ifrs-full:NoncontrollingInterestsMember|ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember")
                ),
                List.of(
                        fact(4660992L, 1L, "ctx-total", "ifrs-full:ProfitLoss", "ProfitLoss", new BigDecimal("57377832577")),
                        fact(4660684L, 1L, "ctx-owner-candidate", "entity00125080:udf_IS_201711101253162_ProfitLoss", "udf_IS_201711101253162_ProfitLoss", new BigDecimal("28278294009")),
                        fact(4660687L, 1L, "ctx-noncont-candidate", "entity00125080:udf_IS_20171110125339361_ProfitLoss", "udf_IS_20171110125339361_ProfitLoss", new BigDecimal("29099538568")),
                        fact(4659958L, 2L, "ctx-owner-member", "entity00125080:udf_CE_owner", "udf", new BigDecimal("28278294009")),
                        fact(4659960L, 3L, "ctx-noncont-member", "entity00125080:udf_CE_noncont", "udf", new BigDecimal("29099538568"))
                )
        );
    }

    private XbrlContextView context(Long contextId, String contextRef, String periodType, String memberSignature) {
        return new XbrlContextView(
                contextId,
                contextRef,
                "hash-" + contextId,
                "00125080",
                periodType,
                LocalDate.of(2019, 1, 1),
                LocalDate.of(2019, 12, 31),
                null,
                "[]",
                memberSignature
        );
    }

    private XbrlFactView fact(Long factId,
                              Long contextId,
                              String contextRef,
                              String conceptQname,
                              String localName,
                              BigDecimal value) {
        return new XbrlFactView(
                factId,
                contextId,
                contextRef,
                conceptQname,
                localName,
                null,
                null,
                null,
                null,
                value.toPlainString(),
                value,
                false,
                null,
                1
        );
    }
}
