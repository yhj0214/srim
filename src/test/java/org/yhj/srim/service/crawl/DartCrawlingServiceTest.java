package org.yhj.srim.service.crawl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yhj.srim.client.DartClient;
import org.yhj.srim.client.dto.DartFilingRow;
import org.yhj.srim.service.crawl.parser.DartFilingParser;
import org.yhj.srim.service.crawl.parser.DartFinancialStatementParser;
import org.yhj.srim.service.crawl.parser.DartShareStatusParser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DartCrawlingServiceTest {

    @InjectMocks
    private DartCrawlingService dartCrawlingService;

    @Mock
    private DartClient dartClient;

    @Mock
    private DartFilingParser dartFilingParser;

    @Mock
    private DartFinancialStatementParser dartFinancialStatementParser;

    @Mock
    private DartShareStatusParser dartShareStatusParser;

    @Test
    @DisplayName("최신 연간 filing은 접수일과 접수번호 내림차순으로 선택한다.")
    void crawlLatestAnnualFiling_returnsLatestRow() {
        DartFilingRow older = new DartFilingRow();
        older.setRceptDt("20250310");
        older.setRceptNo("20250310000001");

        DartFilingRow newer = new DartFilingRow();
        newer.setRceptDt("20250311");
        newer.setRceptNo("20250311000001");

        when(dartClient.fetchAnnualFilingListBody("00126380", 2024)).thenReturn("{}");
        when(dartFilingParser.parse("{}")).thenReturn(List.of(older, newer));

        DartFilingRow latest = dartCrawlingService.crawlLatestAnnualFiling("00126380", 2024);

        assertThat(latest.getRceptNo()).isEqualTo("20250311000001");
    }
}
