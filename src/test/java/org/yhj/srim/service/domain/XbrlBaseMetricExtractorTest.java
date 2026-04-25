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
                .containsEntry("OP_INC", new BigDecimal("123450000"))
                .containsEntry("NET_INC", new BigDecimal("21600800986"))
                .containsEntry("NET_INC_OWNER", new BigDecimal("21751670090"))
                .containsEntry("NET_INC_NONCONT", new BigDecimal("-150869104"))
                .containsEntry("TOTAL_ASSETS", new BigDecimal("800000000000"))
                .containsEntry("TOTAL_LIABILITIES", new BigDecimal("356276821575"))
                .containsEntry("TOTAL_EQUITY", new BigDecimal("443723178425"))
                .containsEntry("TOTAL_EQUITY_OWNER", new BigDecimal("443115993835"))
                .containsEntry("TOTAL_EQUITY_NONCONT", new BigDecimal("607184590"))
                .containsEntry("CURRENT_ASSETS", new BigDecimal("250000000000"))
                .containsEntry("CURRENT_LIABILITIES", new BigDecimal("120000000000"));
    }

    @Test
    @DisplayName("구 ifrs prefix 문서에서도 핵심 base metric을 추출한다.")
    void extractBaseMetrics_supportsLegacyIfrsPrefix() {
        Map<String, BigDecimal> metrics = extractor.extractBaseMetrics(legacyPrefixBundle());

        assertThat(metrics)
                .containsEntry("SALES", new BigDecimal("100"))
                .containsEntry("OP_INC", new BigDecimal("15"))
                .containsEntry("NET_INC", new BigDecimal("20"))
                .containsEntry("NET_INC_OWNER", new BigDecimal("18"))
                .containsEntry("NET_INC_NONCONT", new BigDecimal("2"))
                .containsEntry("TOTAL_ASSETS", new BigDecimal("500"))
                .containsEntry("TOTAL_LIABILITIES", new BigDecimal("200"))
                .containsEntry("TOTAL_EQUITY", new BigDecimal("300"))
                .containsEntry("TOTAL_EQUITY_OWNER", new BigDecimal("290"))
                .containsEntry("CURRENT_ASSETS", new BigDecimal("120"))
                .containsEntry("CURRENT_LIABILITIES", new BigDecimal("80"));
    }

    @Test
    @DisplayName("연간 문서에서는 비교값보다 당기 CFY context를 우선 선택한다.")
    void extractBaseMetrics_prefersCurrentYearFactsOverComparatives() {
        Map<String, BigDecimal> metrics = extractor.extractBaseMetrics(comparativeBundle());

        assertThat(metrics)
                .containsEntry("NET_INC_OWNER", new BigDecimal("300"))
                .containsEntry("TOTAL_EQUITY_OWNER", new BigDecimal("3000"));
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
                                1010L,
                                11L,
                                "ctx-duration",
                                "ifrs-full:ProfitLossFromOperatingActivities",
                                "ProfitLossFromOperatingActivities",
                                "영업이익",
                                "income-statement",
                                "KRW",
                                "0",
                                "123450000",
                                new BigDecimal("123450000"),
                                false,
                                null,
                                1
                        ),
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
                                1020L,
                                12L,
                                "ctx-instant",
                                "ifrs-full:Assets",
                                "Assets",
                                "자산총계",
                                "balance-sheet",
                                "KRW",
                                "0",
                                "800000000000",
                                new BigDecimal("800000000000"),
                                false,
                                null,
                                2
                        ),
                        new XbrlFactView(
                                1021L,
                                12L,
                                "ctx-instant",
                                "ifrs-full:Liabilities",
                                "Liabilities",
                                "부채총계",
                                "balance-sheet",
                                "KRW",
                                "0",
                                "356276821575",
                                new BigDecimal("356276821575"),
                                false,
                                null,
                                3
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
                                4
                        ),
                        new XbrlFactView(
                                1022L,
                                12L,
                                "ctx-instant",
                                "ifrs-full:CurrentAssets",
                                "CurrentAssets",
                                "유동자산",
                                "balance-sheet",
                                "KRW",
                                "0",
                                "250000000000",
                                new BigDecimal("250000000000"),
                                false,
                                null,
                                5
                        ),
                        new XbrlFactView(
                                1023L,
                                12L,
                                "ctx-instant",
                                "ifrs-full:CurrentLiabilities",
                                "CurrentLiabilities",
                                "유동부채",
                                "balance-sheet",
                                "KRW",
                                "0",
                                "120000000000",
                                new BigDecimal("120000000000"),
                                false,
                                null,
                                6
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
                                7
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
                                8
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
                                9
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
                                10
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
                                11
                        )
                )
        );
    }

    private XbrlRawBundle comparativeBundle() {
        return new XbrlRawBundle(
                new XbrlDocumentView(
                        2L,
                        "00126380",
                        10L,
                        "20260310002820",
                        "11011",
                        2025,
                        "CFS",
                        "연간",
                        "https://example.com/xbrl-2025.zip",
                        "/tmp/example-2025.zip",
                        "test-taxonomy",
                        "test-v1",
                        LocalDateTime.of(2026, 3, 10, 10, 0)
                ),
                List.of(
                        new XbrlContextView(
                                21L,
                                "BPFY2023eFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "g".repeat(64),
                                "00126380",
                                "instant",
                                null,
                                null,
                                LocalDate.of(2023, 12, 31),
                                "[]",
                                "ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember"
                        ),
                        new XbrlContextView(
                                22L,
                                "PFY2024eFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "h".repeat(64),
                                "00126380",
                                "instant",
                                null,
                                null,
                                LocalDate.of(2024, 12, 31),
                                "[]",
                                "ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember"
                        ),
                        new XbrlContextView(
                                23L,
                                "CFY2025eFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "i".repeat(64),
                                "00126380",
                                "instant",
                                null,
                                null,
                                LocalDate.of(2025, 12, 31),
                                "[]",
                                "ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember"
                        ),
                        new XbrlContextView(
                                24L,
                                "PFY2024eFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "j".repeat(64),
                                "00126380",
                                "duration",
                                LocalDate.of(2024, 1, 1),
                                LocalDate.of(2024, 12, 31),
                                null,
                                "[]",
                                "ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember"
                        ),
                        new XbrlContextView(
                                25L,
                                "CFY2025eFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "k".repeat(64),
                                "00126380",
                                "duration",
                                LocalDate.of(2025, 1, 1),
                                LocalDate.of(2025, 12, 31),
                                null,
                                "[]",
                                "ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember"
                        )
                ),
                List.of(
                        new XbrlFactView(
                                201L,
                                21L,
                                "BPFY2023eFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "ifrs-full:EquityAttributableToOwnersOfParent",
                                "EquityAttributableToOwnersOfParent",
                                "지배기업 소유주지분",
                                "balance-sheet",
                                "KRW",
                                "0",
                                "1000",
                                new BigDecimal("1000"),
                                false,
                                null,
                                1
                        ),
                        new XbrlFactView(
                                202L,
                                22L,
                                "PFY2024eFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "ifrs-full:EquityAttributableToOwnersOfParent",
                                "EquityAttributableToOwnersOfParent",
                                "지배기업 소유주지분",
                                "balance-sheet",
                                "KRW",
                                "0",
                                "2000",
                                new BigDecimal("2000"),
                                false,
                                null,
                                2
                        ),
                        new XbrlFactView(
                                203L,
                                23L,
                                "CFY2025eFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "ifrs-full:EquityAttributableToOwnersOfParent",
                                "EquityAttributableToOwnersOfParent",
                                "지배기업 소유주지분",
                                "balance-sheet",
                                "KRW",
                                "0",
                                "3000",
                                new BigDecimal("3000"),
                                false,
                                null,
                                3
                        ),
                        new XbrlFactView(
                                204L,
                                24L,
                                "PFY2024eFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "ifrs-full:ProfitLossAttributableToOwnersOfParent",
                                "ProfitLossAttributableToOwnersOfParent",
                                "지배기업 소유주지분",
                                "income-statement",
                                "KRW",
                                "0",
                                "200",
                                new BigDecimal("200"),
                                false,
                                null,
                                4
                        ),
                        new XbrlFactView(
                                205L,
                                25L,
                                "CFY2025eFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "ifrs-full:ProfitLossAttributableToOwnersOfParent",
                                "ProfitLossAttributableToOwnersOfParent",
                                "지배기업 소유주지분",
                                "income-statement",
                                "KRW",
                                "0",
                                "300",
                                new BigDecimal("300"),
                                false,
                                null,
                                5
                        )
                )
        );
    }

    private XbrlRawBundle legacyPrefixBundle() {
        return new XbrlRawBundle(
                new XbrlDocumentView(
                        2L,
                        "00126380",
                        10L,
                        "20190401004781",
                        "11011",
                        2018,
                        "CFS",
                        "연간",
                        "https://example.com/legacy-xbrl.zip",
                        "/tmp/legacy.zip",
                        "https://taxonomy.example/ifrs.xsd",
                        "test-v1",
                        LocalDateTime.of(2019, 4, 1, 10, 0)
                ),
                List.of(
                        new XbrlContextView(21L, "ctx-duration", "g".repeat(64), "00126380", "duration",
                                LocalDate.of(2018, 1, 1), LocalDate.of(2018, 12, 31), null, "[]", null),
                        new XbrlContextView(22L, "ctx-instant", "h".repeat(64), "00126380", "instant",
                                null, null, LocalDate.of(2018, 12, 31), "[]", null)
                ),
                List.of(
                        new XbrlFactView(201L, 21L, "ctx-duration", "ifrs:Revenue", "Revenue",
                                "매출액", "income-statement", "KRW", "0", "100", new BigDecimal("100"),
                                false, null, 1),
                        new XbrlFactView(2010L, 21L, "ctx-duration", "ifrs:ProfitLossFromOperatingActivities",
                                "ProfitLossFromOperatingActivities", "영업이익", "income-statement", "KRW", "0",
                                "15", new BigDecimal("15"), false, null, 2),
                        new XbrlFactView(202L, 21L, "ctx-duration", "ifrs:ProfitLoss", "ProfitLoss",
                                "당기순이익", "income-statement", "KRW", "0", "20", new BigDecimal("20"),
                                false, null, 3),
                        new XbrlFactView(203L, 21L, "ctx-duration", "ifrs:ProfitLossAttributableToOwnersOfParent",
                                "ProfitLossAttributableToOwnersOfParent", "지배순이익", "income-statement", "KRW", "0",
                                "18", new BigDecimal("18"), false, null, 4),
                        new XbrlFactView(204L, 21L, "ctx-duration", "ifrs:ProfitLossAttributableToNoncontrollingInterests",
                                "ProfitLossAttributableToNoncontrollingInterests", "비지배순이익",
                                "income-statement", "KRW", "0", "2", new BigDecimal("2"), false, null, 5),
                        new XbrlFactView(2040L, 22L, "ctx-instant", "ifrs:Assets", "Assets",
                                "자산총계", "balance-sheet", "KRW", "0", "500", new BigDecimal("500"),
                                false, null, 6),
                        new XbrlFactView(2041L, 22L, "ctx-instant", "ifrs:Liabilities", "Liabilities",
                                "부채총계", "balance-sheet", "KRW", "0", "200", new BigDecimal("200"),
                                false, null, 7),
                        new XbrlFactView(205L, 22L, "ctx-instant", "ifrs:Equity", "Equity",
                                "자본총계", "balance-sheet", "KRW", "0", "300", new BigDecimal("300"),
                                false, null, 8),
                        new XbrlFactView(2050L, 22L, "ctx-instant", "ifrs:CurrentAssets", "CurrentAssets",
                                "유동자산", "balance-sheet", "KRW", "0", "120", new BigDecimal("120"),
                                false, null, 9),
                        new XbrlFactView(2051L, 22L, "ctx-instant", "ifrs:CurrentLiabilities", "CurrentLiabilities",
                                "유동부채", "balance-sheet", "KRW", "0", "80", new BigDecimal("80"),
                                false, null, 10),
                        new XbrlFactView(206L, 22L, "ctx-instant", "ifrs:EquityAttributableToOwnersOfParent",
                                "EquityAttributableToOwnersOfParent", "지배기업 소유주에게 귀속되는 자본",
                                "balance-sheet", "KRW", "0", "290", new BigDecimal("290"), false, null, 11)
                )
        );
    }
}
