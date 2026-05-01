package org.yhj.srim.service.domain.extraction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yhj.srim.service.domain.resolver.XbrlCompanyMetricOverrideResolver;
import org.yhj.srim.service.domain.resolver.XbrlDefaultMetricFallbackResolver;
import org.yhj.srim.service.domain.rule.AkHoldingsMetricOverrideRule;
import org.yhj.srim.service.domain.rule.DefaultMetricFallbackRule;
import org.yhj.srim.service.dto.FsRawBundle;
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

class XbrlFsRawBundleAdapterTest {

    private final XbrlFsRawBundleAdapter adapter =
            new XbrlFsRawBundleAdapter(new XbrlBaseMetricExtractor(
                    new XbrlFactSelector(),
                    new XbrlDefaultMetricFallbackResolver(
                            new DefaultMetricFallbackRule()
                    ),
                    new XbrlCompanyMetricOverrideResolver(
                            List.of(new AkHoldingsMetricOverrideRule(new XbrlFactSelector()))
                    )
            ));

    @Test
    @DisplayName("현재/전기 XBRL bundle을 FsRawBundle로 변환한다.")
    void adapt_buildsCurrentAndPreviousMetricMaps() {
        FsRawBundle rawBundle = adapter.adapt(
                sampleBundle(2024, "1014156314426", "21600800986", "443723178425"),
                sampleBundle(2023, "936525061005", "16472354950", "419651809690")
        );

        assertThat(rawBundle.curr())
                .containsEntry("SALES", new BigDecimal("1014156314426"))
                .containsEntry("NET_INC", new BigDecimal("21600800986"))
                .containsEntry("TOTAL_EQUITY", new BigDecimal("443723178425"));

        assertThat(rawBundle.prev())
                .containsEntry("SALES", new BigDecimal("936525061005"))
                .containsEntry("NET_INC", new BigDecimal("16472354950"))
                .containsEntry("TOTAL_EQUITY", new BigDecimal("419651809690"));
    }

    @Test
    @DisplayName("전기 bundle이 없으면 prev는 빈 맵으로 반환한다.")
    void adapt_withoutPreviousBundle_returnsEmptyPrevMap() {
        FsRawBundle rawBundle = adapter.adapt(
                sampleBundle(2024, "1014156314426", "21600800986", "443723178425")
        );

        assertThat(rawBundle.curr()).isNotEmpty();
        assertThat(rawBundle.prev()).isEmpty();
    }

    @Test
    @DisplayName("최신 사업보고서 bundle에서도 target year를 바꾸면 PFY/BPFY 비교값을 해당 연도 대표값으로 추출한다.")
    void extractMetricsForTargetYear_usesComparativeFacts() {
        XbrlRawBundle bundle = comparativeBundle(2025, "1500", "1200", "900");

        Map<String, BigDecimal> currentYearMetrics = adapter.extractMetricsForTargetYear(bundle, 2024);
        Map<String, BigDecimal> previousYearMetrics = adapter.extractMetricsForTargetYear(bundle, 2023);

        assertThat(currentYearMetrics).containsEntry("SALES", new BigDecimal("1200"));
        assertThat(previousYearMetrics).containsEntry("SALES", new BigDecimal("900"));
    }

    private XbrlRawBundle sampleBundle(int year,
                                       String sales,
                                       String netIncome,
                                       String totalEquity) {
        return new XbrlRawBundle(
                new XbrlDocumentView(
                        (long) year,
                        "00126380",
                        10L,
                        "20250321001234",
                        "11011",
                        year,
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
                                year * 10L + 1,
                                "ctx-duration-" + year,
                                "a".repeat(64),
                                "00126380",
                                "duration",
                                LocalDate.of(year, 1, 1),
                                LocalDate.of(year, 12, 31),
                                null,
                                "[]",
                                null
                        ),
                        new XbrlContextView(
                                year * 10L + 2,
                                "ctx-instant-" + year,
                                "b".repeat(64),
                                "00126380",
                                "instant",
                                null,
                                null,
                                LocalDate.of(year, 12, 31),
                                "[]",
                                null
                        )
                ),
                List.of(
                        new XbrlFactView(
                                year * 100L + 1,
                                year * 10L + 1,
                                "ctx-duration-" + year,
                                "ifrs-full:Revenue",
                                "Revenue",
                                "매출액",
                                "income-statement",
                                "KRW",
                                "0",
                                sales,
                                new BigDecimal(sales),
                                false,
                                null,
                                1
                        ),
                        new XbrlFactView(
                                year * 100L + 2,
                                year * 10L + 1,
                                "ctx-duration-" + year,
                                "ifrs-full:ProfitLoss",
                                "ProfitLoss",
                                "당기순이익",
                                "income-statement",
                                "KRW",
                                "0",
                                netIncome,
                                new BigDecimal(netIncome),
                                false,
                                null,
                                2
                        ),
                        new XbrlFactView(
                                year * 100L + 3,
                                year * 10L + 2,
                                "ctx-instant-" + year,
                                "ifrs-full:Equity",
                                "Equity",
                                "자본총계",
                                "balance-sheet",
                                "KRW",
                                "0",
                                totalEquity,
                                new BigDecimal(totalEquity),
                                false,
                                null,
                                3
                        )
                )
        );
    }

    private XbrlRawBundle comparativeBundle(int year,
                                            String salesCurrent,
                                            String salesPrevious,
                                            String salesBeforePrevious) {
        return new XbrlRawBundle(
                new XbrlDocumentView(
                        (long) year,
                        "00126380",
                        10L,
                        "20260321001234",
                        "11011",
                        year,
                        "CFS",
                        "연간",
                        "https://example.com/xbrl.zip",
                        "/tmp/example.zip",
                        "https://taxonomy.example/ifrs-full.xsd",
                        "test-v1",
                        LocalDateTime.of(2026, 3, 21, 10, 0)
                ),
                List.of(
                        new XbrlContextView(
                                year * 10L + 1,
                                "CFY" + year + "dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "c".repeat(64),
                                "00126380",
                                "duration",
                                LocalDate.of(year, 1, 1),
                                LocalDate.of(year, 12, 31),
                                null,
                                "[]",
                                "ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember"
                        ),
                        new XbrlContextView(
                                year * 10L + 2,
                                "PFY" + (year - 1) + "dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "d".repeat(64),
                                "00126380",
                                "duration",
                                LocalDate.of(year - 1, 1, 1),
                                LocalDate.of(year - 1, 12, 31),
                                null,
                                "[]",
                                "ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember"
                        ),
                        new XbrlContextView(
                                year * 10L + 3,
                                "BPFY" + (year - 2) + "dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "e".repeat(64),
                                "00126380",
                                "duration",
                                LocalDate.of(year - 2, 1, 1),
                                LocalDate.of(year - 2, 12, 31),
                                null,
                                "[]",
                                "ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis=ifrs-full:ConsolidatedMember"
                        )
                ),
                List.of(
                        new XbrlFactView(
                                year * 100L + 1,
                                year * 10L + 1,
                                "CFY" + year + "dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "ifrs-full:Revenue",
                                "Revenue",
                                "매출액",
                                "income-statement",
                                "KRW",
                                "0",
                                salesCurrent,
                                new BigDecimal(salesCurrent),
                                false,
                                null,
                                1
                        ),
                        new XbrlFactView(
                                year * 100L + 2,
                                year * 10L + 2,
                                "PFY" + (year - 1) + "dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "ifrs-full:Revenue",
                                "Revenue",
                                "매출액",
                                "income-statement",
                                "KRW",
                                "0",
                                salesPrevious,
                                new BigDecimal(salesPrevious),
                                false,
                                null,
                                2
                        ),
                        new XbrlFactView(
                                year * 100L + 3,
                                year * 10L + 3,
                                "BPFY" + (year - 2) + "dFY_ifrs-full_ConsolidatedAndSeparateFinancialStatementsAxis_ifrs-full_ConsolidatedMember",
                                "ifrs-full:Revenue",
                                "Revenue",
                                "매출액",
                                "income-statement",
                                "KRW",
                                "0",
                                salesBeforePrevious,
                                new BigDecimal(salesBeforePrevious),
                                false,
                                null,
                                3
                        )
                )
        );
    }
}
