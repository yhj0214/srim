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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XbrlFactSelectorTest {

    private final XbrlFactSelector selector = new XbrlFactSelector();

    @Test
    @DisplayName("concept 기준으로 fact를 찾는다.")
    void findFactsByConcept_filtersByConcept() {
        XbrlRawBundle bundle = sampleBundle();

        List<XbrlFactView> facts = selector.findFactsByConcept(bundle, "ifrs-full:Revenue");

        assertThat(facts).hasSize(1);
        assertThat(facts.get(0).valueNumeric()).isEqualByComparingTo("1014156314426");
    }

    @Test
    @DisplayName("periodType 기준으로 duration/instant fact를 구분한다.")
    void findFactsByPeriodType_filtersByContextPeriodType() {
        XbrlRawBundle bundle = sampleBundle();

        List<XbrlFactView> durationFacts = selector.findDurationFactsByConcept(bundle, "ifrs-full:Revenue");
        List<XbrlFactView> instantFacts = selector.findInstantFactsByConcept(bundle, "ifrs-full:Equity");

        assertThat(durationFacts).extracting(XbrlFactView::contextRef).containsExactly("ctx-duration");
        assertThat(instantFacts).extracting(XbrlFactView::contextRef).containsExactly("ctx-instant-owner");
    }

    @Test
    @DisplayName("member keyword 기준으로 owner/noncont fact를 구분한다.")
    void findFactsByConceptAndMemberKeyword_filtersByMemberContext() {
        XbrlRawBundle bundle = sampleBundle();

        List<XbrlFactView> ownerFacts = selector.findFactsByConceptAndMemberKeyword(
                bundle,
                "ifrs-full:ProfitLoss",
                "ownersofparent"
        );

        List<XbrlFactView> noncontFacts = selector.findFactsByConceptAndMemberKeyword(
                bundle,
                "ifrs-full:ProfitLoss",
                "noncontrolling"
        );

        assertThat(ownerFacts).extracting(XbrlFactView::contextRef).containsExactly("ctx-duration-owner");
        assertThat(noncontFacts).extracting(XbrlFactView::contextRef).containsExactly("ctx-duration-noncont");
    }

    private XbrlRawBundle sampleBundle() {
        return new XbrlRawBundle(
                new XbrlDocumentView(
                        1L,
                        "00126380",
                        10L,
                        "20250321001234",
                        "11011",
                        2024,
                        "CFS",
                        "연간",
                        "https://example.com/xbrl.zip",
                        "/tmp/example.zip",
                        "https://taxonomy.example/ifrs-full.xsd",
                        "test-v1",
                        LocalDateTime.of(2025, 3, 21, 10, 0)
                ),
                List.of(
                        new XbrlContextView(
                                11L,
                                "ctx-duration",
                                "a".repeat(64),
                                "00126380",
                                "duration",
                                LocalDate.of(2024, 1, 1),
                                LocalDate.of(2024, 12, 31),
                                null,
                                """
                                [{"axis":"dart:ConsolidatedOrSeparateFinancialStatementsAxis","member":"dart:ConsolidatedFinancialStatementsMember","typed":false}]
                                """.trim(),
                                "dart:ConsolidatedOrSeparateFinancialStatementsAxis=dart:ConsolidatedFinancialStatementsMember"
                        ),
                        new XbrlContextView(
                                12L,
                                "ctx-instant-owner",
                                "b".repeat(64),
                                "00126380",
                                "instant",
                                null,
                                null,
                                LocalDate.of(2024, 12, 31),
                                """
                                [{"axis":"ifrs-full:AttributableToOwnersOfParentAxis","member":"ifrs-full:OwnersOfParentMember","typed":false}]
                                """.trim(),
                                "ifrs-full:AttributableToOwnersOfParentAxis=ifrs-full:OwnersOfParentMember"
                        ),
                        new XbrlContextView(
                                13L,
                                "ctx-duration-owner",
                                "c".repeat(64),
                                "00126380",
                                "duration",
                                LocalDate.of(2024, 1, 1),
                                LocalDate.of(2024, 12, 31),
                                null,
                                """
                                [{"axis":"ifrs-full:AttributableToOwnersOfParentAxis","member":"ifrs-full:OwnersOfParentMember","typed":false}]
                                """.trim(),
                                "ifrs-full:AttributableToOwnersOfParentAxis=ifrs-full:OwnersOfParentMember"
                        ),
                        new XbrlContextView(
                                14L,
                                "ctx-duration-noncont",
                                "d".repeat(64),
                                "00126380",
                                "duration",
                                LocalDate.of(2024, 1, 1),
                                LocalDate.of(2024, 12, 31),
                                null,
                                """
                                [{"axis":"ifrs-full:NoncontrollingInterestsAxis","member":"ifrs-full:NoncontrollingInterestsMember","typed":false}]
                                """.trim(),
                                "ifrs-full:NoncontrollingInterestsAxis=ifrs-full:NoncontrollingInterestsMember"
                        )
                ),
                List.of(
                        new XbrlFactView(
                                101L,
                                11L,
                                "ctx-duration",
                                "ifrs-full:Revenue",
                                "Revenue",
                                "매출액",
                                "income-statement",
                                "KRW",
                                "0",
                                "1014156314426",
                                new BigDecimal("1014156314426"),
                                false,
                                null,
                                1
                        ),
                        new XbrlFactView(
                                102L,
                                12L,
                                "ctx-instant-owner",
                                "ifrs-full:Equity",
                                "Equity",
                                "자본총계",
                                "balance-sheet",
                                "KRW",
                                "0",
                                "443115993835",
                                new BigDecimal("443115993835"),
                                false,
                                "ifrs-full:AttributableToOwnersOfParentAxis=ifrs-full:OwnersOfParentMember",
                                2
                        ),
                        new XbrlFactView(
                                103L,
                                13L,
                                "ctx-duration-owner",
                                "ifrs-full:ProfitLoss",
                                "ProfitLoss",
                                "지배순이익",
                                "income-statement",
                                "KRW",
                                "0",
                                "21751670090",
                                new BigDecimal("21751670090"),
                                false,
                                "ifrs-full:AttributableToOwnersOfParentAxis=ifrs-full:OwnersOfParentMember",
                                3
                        ),
                        new XbrlFactView(
                                104L,
                                14L,
                                "ctx-duration-noncont",
                                "ifrs-full:ProfitLoss",
                                "ProfitLoss",
                                "비지배순이익",
                                "income-statement",
                                "KRW",
                                "0",
                                "-150869104",
                                new BigDecimal("-150869104"),
                                false,
                                "ifrs-full:NoncontrollingInterestsAxis=ifrs-full:NoncontrollingInterestsMember",
                                4
                        )
                )
        );
    }
}
