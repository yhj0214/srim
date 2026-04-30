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

    private final XbrlBaseMetricExtractor extractor = new XbrlBaseMetricExtractor(
            new XbrlFactSelector(),
            new XbrlOwnershipMetricFallbackResolver(
                    new XbrlNetIncomeAttributionFallbackResolver()
            )
    );

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

    @Test
    @DisplayName("dart OperatingIncomeLoss가 있으면 segment 값보다 단순 연결 총계 영업이익을 우선 추출한다.")
    void extractBaseMetrics_supportsDartOperatingIncomeLoss() {
        Map<String, BigDecimal> metrics = extractor.extractBaseMetrics(dartOperatingIncomeBundle());

        assertThat(metrics)
                .containsEntry("OP_INC", new BigDecimal("508080820628"));
    }

    @Test
    @DisplayName("AK홀딩스 문서에서는 custom ProfitLoss와 equity axis 일치 조건을 만족할 때 지배/비지배 순이익을 보완한다.")
    void extractBaseMetrics_supportsAkHoldingsCustomNetIncomeAttribution() {
        Map<String, BigDecimal> metrics = extractor.extractBaseMetrics(akHoldingsBundle());

        assertThat(metrics)
                .containsEntry("NET_INC", new BigDecimal("-124858459734"))
                .containsEntry("NET_INC_OWNER", new BigDecimal("-63729440728"))
                .containsEntry("NET_INC_NONCONT", new BigDecimal("-61129019006"));
    }

    @Test
    @DisplayName("AK홀딩스 2019 문서에서도 custom ProfitLoss와 equity axis 일치 조건으로 지배/비지배 순이익을 보완한다.")
    void extractBaseMetrics_supportsAkHoldings2019CustomNetIncomeAttribution() {
        Map<String, BigDecimal> metrics = extractor.extractBaseMetrics(akHoldings2019Bundle());

        assertThat(metrics)
                .containsEntry("NET_INC", new BigDecimal("57377832577"))
                .containsEntry("NET_INC_OWNER", new BigDecimal("28278294009"))
                .containsEntry("NET_INC_NONCONT", new BigDecimal("29099538568"));
    }

    @Test
    @DisplayName("지배·비지배 분해 fact가 없고 관련 흔적도 없으면 owner=total, noncont=0으로 보완한다.")
    void extractBaseMetrics_defaultsOwnerAndNoncontWhenBreakdownIsMissing() {
        Map<String, BigDecimal> metrics = extractor.extractBaseMetrics(missingOwnershipBreakdownBundle());

        assertThat(metrics)
                .containsEntry("NET_INC", new BigDecimal("2827173450"))
                .containsEntry("NET_INC_OWNER", new BigDecimal("2827173450"))
                .containsEntry("NET_INC_NONCONT", BigDecimal.ZERO)
                .containsEntry("TOTAL_EQUITY", new BigDecimal("88416055935"))
                .containsEntry("TOTAL_EQUITY_OWNER", new BigDecimal("88416055935"))
                .containsEntry("TOTAL_EQUITY_NONCONT", BigDecimal.ZERO);
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

    private XbrlRawBundle dartOperatingIncomeBundle() {
        return new XbrlRawBundle(
                new XbrlDocumentView(
                        3L,
                        "00113410",
                        67L,
                        "20260316001417",
                        "11011",
                        2025,
                        "CFS",
                        "연간",
                        "https://example.com/dart-operating-income.zip",
                        "/tmp/dart-operating-income.zip",
                        "https://taxonomy.example/dart.xsd",
                        "test-v1",
                        LocalDateTime.of(2026, 3, 16, 10, 0)
                ),
                List.of(
                        new XbrlContextView(
                                31L,
                                "CFY2025dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "i".repeat(64),
                                "00113410",
                                "duration",
                                LocalDate.of(2025, 1, 1),
                                LocalDate.of(2025, 12, 31),
                                null,
                                "[]",
                                "ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember"
                        ),
                        new XbrlContextView(
                                32L,
                                "CFY2025dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember_ifrs-full_SegmentsAxis_entity00113410_ParcelMemberOfSegmentsMemberOfDisclosureOfRevenueFromExternalCustomersAbstractTableOfMember",
                                "j".repeat(64),
                                "00113410",
                                "duration",
                                LocalDate.of(2025, 1, 1),
                                LocalDate.of(2025, 12, 31),
                                null,
                                "[]",
                                "ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember|ifrs-full:SegmentsAxis=entity00113410:ParcelMemberOfSegmentsMemberOfDisclosureOfRevenueFromExternalCustomersAbstractTableOfMember"
                        ),
                        new XbrlContextView(
                                33L,
                                "PFY2024dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "k".repeat(64),
                                "00113410",
                                "duration",
                                LocalDate.of(2024, 1, 1),
                                LocalDate.of(2024, 12, 31),
                                null,
                                "[]",
                                "ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember"
                        )
                ),
                List.of(
                        new XbrlFactView(
                                301L,
                                32L,
                                "CFY2025dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember_ifrs-full_SegmentsAxis_entity00113410_ParcelMemberOfSegmentsMemberOfDisclosureOfRevenueFromExternalCustomersAbstractTableOfMember",
                                "dart:OperatingIncomeLoss",
                                "OperatingIncomeLoss",
                                "영업이익",
                                "income-statement",
                                "KRW",
                                "0",
                                "204742456000",
                                new BigDecimal("204742456000"),
                                false,
                                null,
                                10
                        ),
                        new XbrlFactView(
                                302L,
                                33L,
                                "PFY2024dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "dart:OperatingIncomeLoss",
                                "OperatingIncomeLoss",
                                "영업이익",
                                "income-statement",
                                "KRW",
                                "0",
                                "530663067133",
                                new BigDecimal("530663067133"),
                                false,
                                null,
                                20
                        ),
                        new XbrlFactView(
                                303L,
                                31L,
                                "CFY2025dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "dart:OperatingIncomeLoss",
                                "OperatingIncomeLoss",
                                "영업이익",
                                "income-statement",
                                "KRW",
                                "0",
                                "508080820628",
                                new BigDecimal("508080820628"),
                                false,
                                null,
                                30
                        )
                )
        );
    }

    private XbrlRawBundle akHoldingsBundle() {
        return new XbrlRawBundle(
                new XbrlDocumentView(
                        4L,
                        "00125080",
                        20L,
                        "20230321001495",
                        "11011",
                        2023,
                        "CFS",
                        "연간",
                        "https://example.com/ak-holdings.zip",
                        "/tmp/ak-holdings.zip",
                        "https://taxonomy.example/custom.xsd",
                        "test-v1",
                        LocalDateTime.of(2023, 3, 21, 10, 0)
                ),
                List.of(
                        new XbrlContextView(
                                41L,
                                "PFY2022dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "l".repeat(64),
                                "00125080",
                                "duration",
                                LocalDate.of(2022, 1, 1),
                                LocalDate.of(2022, 12, 31),
                                null,
                                "[]",
                                "ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember"
                        ),
                        new XbrlContextView(
                                42L,
                                "PFY2022eFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember_ifrs-full_ComponentsOfEquityAxis_ifrs-full_EquityAttributableToOwnersOfParentMember",
                                "m".repeat(64),
                                "00125080",
                                "instant",
                                null,
                                null,
                                LocalDate.of(2022, 12, 31),
                                "[]",
                                "ifrs-full:ComponentsOfEquityAxis=ifrs-full:EquityAttributableToOwnersOfParentMember|ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember"
                        ),
                        new XbrlContextView(
                                43L,
                                "PFY2022eFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember_ifrs-full_ComponentsOfEquityAxis_ifrs-full_NoncontrollingInterestsMember",
                                "n".repeat(64),
                                "00125080",
                                "instant",
                                null,
                                null,
                                LocalDate.of(2022, 12, 31),
                                "[]",
                                "ifrs-full:ComponentsOfEquityAxis=ifrs-full:NoncontrollingInterestsMember|ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember"
                        )
                ),
                List.of(
                        new XbrlFactView(
                                401L,
                                41L,
                                "PFY2022dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "ifrs-full:ProfitLoss",
                                "ProfitLoss",
                                "당기순이익",
                                "income-statement",
                                "KRW",
                                "0",
                                "-124858459734",
                                new BigDecimal("-124858459734"),
                                false,
                                null,
                                1
                        ),
                        new XbrlFactView(
                                402L,
                                41L,
                                "PFY2022dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "entity00125080:udf_IS_201711101253162_ProfitLoss",
                                "udf_IS_201711101253162_ProfitLoss",
                                "커스텀 당기순이익",
                                "income-statement",
                                "KRW",
                                "0",
                                "-63729440728",
                                new BigDecimal("-63729440728"),
                                false,
                                null,
                                2
                        ),
                        new XbrlFactView(
                                403L,
                                41L,
                                "PFY2022dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "entity00125080:udf_IS_20171110125339361_ProfitLoss",
                                "udf_IS_20171110125339361_ProfitLoss",
                                "커스텀 당기순이익2",
                                "income-statement",
                                "KRW",
                                "0",
                                "-61129019006",
                                new BigDecimal("-61129019006"),
                                false,
                                null,
                                3
                        ),
                        new XbrlFactView(
                                404L,
                                42L,
                                "PFY2022eFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember_ifrs-full_ComponentsOfEquityAxis_ifrs-full_EquityAttributableToOwnersOfParentMember",
                                "entity00125080:udf_CE_owner",
                                "udf_CE_owner",
                                "지배 소유주지분 변동",
                                "changes-in-equity",
                                "KRW",
                                "0",
                                "-63729440728",
                                new BigDecimal("-63729440728"),
                                false,
                                "ifrs-full:ComponentsOfEquityAxis=ifrs-full:EquityAttributableToOwnersOfParentMember|ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember",
                                4
                        ),
                        new XbrlFactView(
                                405L,
                                43L,
                                "PFY2022eFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember_ifrs-full_ComponentsOfEquityAxis_ifrs-full_NoncontrollingInterestsMember",
                                "entity00125080:udf_CE_noncont",
                                "udf_CE_noncont",
                                "비지배지분 변동",
                                "changes-in-equity",
                                "KRW",
                                "0",
                                "-61129019006",
                                new BigDecimal("-61129019006"),
                                false,
                                "ifrs-full:ComponentsOfEquityAxis=ifrs-full:NoncontrollingInterestsMember|ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember",
                                5
                        )
                )
        );
    }

    private XbrlRawBundle akHoldings2019Bundle() {
        return new XbrlRawBundle(
                new XbrlDocumentView(
                        5L,
                        "00125080",
                        21L,
                        "20200330003219",
                        "11011",
                        2019,
                        "CFS",
                        "연간",
                        "https://example.com/ak-holdings-2019.zip",
                        "/tmp/ak-holdings-2019.zip",
                        "https://taxonomy.example/custom.xsd",
                        "test-v1",
                        LocalDateTime.of(2020, 3, 30, 10, 0)
                ),
                List.of(
                        new XbrlContextView(
                                51L,
                                "CFY2019dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "o".repeat(64),
                                "00125080",
                                "duration",
                                LocalDate.of(2019, 1, 1),
                                LocalDate.of(2019, 12, 31),
                                null,
                                "[]",
                                "ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember"
                        ),
                        new XbrlContextView(
                                52L,
                                "CFY2019eFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember_ifrs-full_ComponentsOfEquityAxis_ifrs-full_EquityAttributableToOwnersOfParentMember",
                                "p".repeat(64),
                                "00125080",
                                "instant",
                                null,
                                null,
                                LocalDate.of(2019, 12, 31),
                                "[]",
                                "ifrs-full:ComponentsOfEquityAxis=ifrs-full:EquityAttributableToOwnersOfParentMember|ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember"
                        ),
                        new XbrlContextView(
                                53L,
                                "CFY2019eFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember_ifrs-full_ComponentsOfEquityAxis_ifrs-full_NoncontrollingInterestsMember",
                                "q".repeat(64),
                                "00125080",
                                "instant",
                                null,
                                null,
                                LocalDate.of(2019, 12, 31),
                                "[]",
                                "ifrs-full:ComponentsOfEquityAxis=ifrs-full:NoncontrollingInterestsMember|ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember"
                        )
                ),
                List.of(
                        new XbrlFactView(
                                501L,
                                51L,
                                "CFY2019dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "ifrs-full:ProfitLoss",
                                "ProfitLoss",
                                "당기순이익",
                                "income-statement",
                                "KRW",
                                "0",
                                "57377832577",
                                new BigDecimal("57377832577"),
                                false,
                                null,
                                1
                        ),
                        new XbrlFactView(
                                502L,
                                51L,
                                "CFY2019dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "entity00125080:udf_IS_201711101253162_ProfitLoss",
                                "udf_IS_201711101253162_ProfitLoss",
                                "커스텀 당기순이익",
                                "income-statement",
                                "KRW",
                                "0",
                                "28278294009",
                                new BigDecimal("28278294009"),
                                false,
                                null,
                                2
                        ),
                        new XbrlFactView(
                                503L,
                                51L,
                                "CFY2019dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "entity00125080:udf_IS_20171110125339361_ProfitLoss",
                                "udf_IS_20171110125339361_ProfitLoss",
                                "커스텀 당기순이익2",
                                "income-statement",
                                "KRW",
                                "0",
                                "29099538568",
                                new BigDecimal("29099538568"),
                                false,
                                null,
                                3
                        ),
                        new XbrlFactView(
                                504L,
                                52L,
                                "CFY2019eFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember_ifrs-full_ComponentsOfEquityAxis_ifrs-full_EquityAttributableToOwnersOfParentMember",
                                "entity00125080:udf_CE_owner",
                                "udf_CE_owner",
                                "지배 소유주지분 변동",
                                "changes-in-equity",
                                "KRW",
                                "0",
                                "28278294009",
                                new BigDecimal("28278294009"),
                                false,
                                "ifrs-full:ComponentsOfEquityAxis=ifrs-full:EquityAttributableToOwnersOfParentMember|ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember",
                                4
                        ),
                        new XbrlFactView(
                                505L,
                                53L,
                                "CFY2019eFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember_ifrs-full_ComponentsOfEquityAxis_ifrs-full_NoncontrollingInterestsMember",
                                "entity00125080:udf_CE_noncont",
                                "udf_CE_noncont",
                                "비지배지분 변동",
                                "changes-in-equity",
                                "KRW",
                                "0",
                                "29099538568",
                                new BigDecimal("29099538568"),
                                false,
                                "ifrs-full:ComponentsOfEquityAxis=ifrs-full:NoncontrollingInterestsMember|ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember",
                                5
                        )
                )
        );
    }

    private XbrlRawBundle missingOwnershipBreakdownBundle() {
        return new XbrlRawBundle(
                new XbrlDocumentView(
                        6L,
                        "00129013",
                        22L,
                        "20260323001443",
                        "11011",
                        2025,
                        "CFS",
                        "연간",
                        "https://example.com/missing-ownership-breakdown.zip",
                        "/tmp/missing-ownership-breakdown.zip",
                        "https://taxonomy.example/ifrs-full.xsd",
                        "test-v1",
                        LocalDateTime.of(2026, 3, 23, 10, 0)
                ),
                List.of(
                        new XbrlContextView(
                                61L,
                                "CFY2025dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "r".repeat(64),
                                "00129013",
                                "duration",
                                LocalDate.of(2025, 1, 1),
                                LocalDate.of(2025, 12, 31),
                                null,
                                "[]",
                                "ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember"
                        ),
                        new XbrlContextView(
                                62L,
                                "CFY2025eFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "s".repeat(64),
                                "00129013",
                                "instant",
                                null,
                                null,
                                LocalDate.of(2025, 12, 31),
                                "[]",
                                "ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember"
                        ),
                        new XbrlContextView(
                                63L,
                                "CFY2025dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember_ifrs-full_ComponentsOfEquityAxis_ifrs-full_RetainedEarningsMember",
                                "t".repeat(64),
                                "00129013",
                                "duration",
                                LocalDate.of(2025, 1, 1),
                                LocalDate.of(2025, 12, 31),
                                null,
                                "[]",
                                "ifrs-full:ComponentsOfEquityAxis=ifrs-full:RetainedEarningsMember|ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember"
                        )
                ),
                List.of(
                        new XbrlFactView(
                                601L,
                                61L,
                                "CFY2025dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "ifrs-full:ProfitLoss",
                                "ProfitLoss",
                                "당기순이익",
                                "income-statement",
                                "KRW",
                                "0",
                                "2827173450",
                                new BigDecimal("2827173450"),
                                false,
                                null,
                                1
                        ),
                        new XbrlFactView(
                                602L,
                                63L,
                                "CFY2025dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember_ifrs-full_ComponentsOfEquityAxis_ifrs-full_RetainedEarningsMember",
                                "ifrs-full:ProfitLoss",
                                "ProfitLoss",
                                "당기순이익",
                                "changes-in-equity",
                                "KRW",
                                "0",
                                "2827173450",
                                new BigDecimal("2827173450"),
                                false,
                                "ifrs-full:ComponentsOfEquityAxis=ifrs-full:RetainedEarningsMember|ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember",
                                2
                        ),
                        new XbrlFactView(
                                603L,
                                62L,
                                "CFY2025eFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "ifrs-full:Equity",
                                "Equity",
                                "자본총계",
                                "balance-sheet",
                                "KRW",
                                "0",
                                "88416055935",
                                new BigDecimal("88416055935"),
                                false,
                                null,
                                3
                        )
                )
        );
    }
}
