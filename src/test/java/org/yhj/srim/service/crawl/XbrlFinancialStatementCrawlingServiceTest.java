package org.yhj.srim.service.crawl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yhj.srim.client.DartClient;
import org.yhj.srim.client.DartReportType;
import org.yhj.srim.service.crawl.dto.XbrlParseResult;
import org.yhj.srim.service.crawl.parser.XbrlParser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XbrlFinancialStatementCrawlingServiceTest {

    @InjectMocks
    XbrlFinancialStatementCrawlingService service;

    @Mock
    DartClient dartClient;

    @Mock
    XbrlParser xbrlParser;

    @Test
    @DisplayName("XBRL 원문 다운로드 후 파싱 결과를 배치로 반환한다.")
    void crawlFinancialStatementsXbrl_returnsBatch() {
        byte[] archiveBytes = new byte[]{1, 2, 3};
        XbrlParseResult parseResult = new XbrlParseResult(List.of(), List.of(), "ifrs-full-2024");

        when(dartClient.fetchFinancialStatementsXbrlArchive("20240321000001", DartReportType.ANNUAL))
                .thenReturn(archiveBytes);
        when(dartClient.buildFinancialStatementsXbrlUrl("20240321000001", DartReportType.ANNUAL))
                .thenReturn("https://opendart.example/xbrl");
        when(xbrlParser.parse(archiveBytes)).thenReturn(parseResult);

        XbrlFinancialStatementCrawlingService.XbrlRawBatch batch = service.crawlFinancialStatementsXbrl(
                "00126380",
                "20240321000001",
                2024,
                DartReportType.ANNUAL,
                "CFS"
        );

        assertThat(batch.corpCode()).isEqualTo("00126380");
        assertThat(batch.rceptNo()).isEqualTo("20240321000001");
        assertThat(batch.sourceUrl()).isEqualTo("https://opendart.example/xbrl");
        assertThat(batch.parseResult()).isSameAs(parseResult);

        verify(dartClient).fetchFinancialStatementsXbrlArchive("20240321000001", DartReportType.ANNUAL);
        verify(xbrlParser).parse(archiveBytes);
    }
}
