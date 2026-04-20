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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class XbrlBaseMetricExtractorTest {

    private final XbrlBaseMetricExtractor extractor = new XbrlBaseMetricExtractor(new XbrlFactSelector());

    @Test
    @DisplayName("핵심 base metric과 owner/noncont metric을 추출한다.")
    void extractBaseMetrics_returnsCoreMetricMap() {
        Map<String, BigDecimal> metrics = extractor.extractBaseMetrics(sampleBundle());

        assertThat(metrics)
                .containsEntry("SALES", new BigDecimal("1014156314426"))
                .containsEntry("NET_INC", new BigDecimal("21600800986"))
                .containsEntry("NET_INC_OWNER", new BigDecimal("21751670090"))
                .containsEntry("NET_INC_NONCONT", new BigDecimal("-150869104"))
                .containsEntry("TOTAL_EQUITY", new BigDecimal("443723178425"))
                .containsEntry("TOTAL_EQUITY_OWNER", new BigDecimal("443115993835"))
                .containsEntry("TOTAL_EQUITY_NONCONT", new BigDecimal("607184590"));
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
                                "[]",
                                null
                        ),
                        new XbrlContextView(
                                12L,
                                "ctx-instant",
                                "b".repeat(64),
                                "00126380",
                                "instant",
                                null,
                                null,
                                LocalDate.of(2024, 12, 31),
                                "[]",
                                null
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
                        ),
                        new XbrlContextView(
                                15L,
                                "ctx-instant-owner",
                                "e".repeat(64),
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
                                16L,
                                "ctx-instant-noncont",
                                "f".repeat(64),
                                "00126380",
                                "instant",
                                null,
                                null,
                                LocalDate.of(2024, 12, 31),
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
                                11L,
                                "ctx-duration",
                                "ifrs-full:ProfitLoss",
                                "ProfitLoss",
                                "당기순이익",
                                "income-statement",
                                "KRW",
                                "0",
                                "21600800986",
                                new BigDecimal("21600800986"),
                                false,
                                null,
                                2
                        ),
                        new XbrlFactView(
                                103L,
                                12L,
                                "ctx-instant",
                                "ifrs-full:Equity",
                                "Equity",
                                "자본총계",
                                "balance-sheet",
                                "KRW",
                                "0",
                                "443723178425",
                                new BigDecimal("443723178425"),
                                false,
                                null,
                                3
                        ),
                        new XbrlFactView(
                                104L,
                                13L,
                                "ctx-duration-owner",
                                "ifrs-full:ProfitLossAttributableToOwnersOfParent",
                                "ProfitLossAttributableToOwnersOfParent",
                                "지배순이익",
                                "income-statement",
                                "KRW",
                                "0",
                                "21751670090",
                                new BigDecimal("21751670090"),
                                false,
                                "ifrs-full:AttributableToOwnersOfParentAxis=ifrs-full:OwnersOfParentMember",
                                4
                        ),
                        new XbrlFactView(
                                105L,
                                14L,
                                "ctx-duration-noncont",
                                "ifrs-full:ProfitLossAttributableToNoncontrollingInterests",
                                "ProfitLossAttributableToNoncontrollingInterests",
                                "비지배순이익",
                                "income-statement",
                                "KRW",
                                "0",
                                "-150869104",
                                new BigDecimal("-150869104"),
                                false,
                                "ifrs-full:NoncontrollingInterestsAxis=ifrs-full:NoncontrollingInterestsMember",
                                5
                        ),
                        new XbrlFactView(
                                106L,
                                15L,
                                "ctx-instant-owner",
                                "ifrs-full:EquityAttributableToOwnersOfParent",
                                "EquityAttributableToOwnersOfParent",
                                "지배기업 소유주에게 귀속되는 자본",
                                "balance-sheet",
                                "KRW",
                                "0",
                                "443115993835",
                                new BigDecimal("443115993835"),
                                false,
                                "ifrs-full:AttributableToOwnersOfParentAxis=ifrs-full:OwnersOfParentMember",
                                6
                        ),
                        new XbrlFactView(
                                107L,
                                16L,
                                "ctx-instant-noncont",
                                "ifrs-full:NoncontrollingInterests",
                                "NoncontrollingInterests",
                                "비지배지분",
                                "balance-sheet",
                                "KRW",
                                "0",
                                "607184590",
                                new BigDecimal("607184590"),
                                false,
                                "ifrs-full:NoncontrollingInterestsAxis=ifrs-full:NoncontrollingInterestsMember",
                                7
                        )
                )
        );
    }
}
