package org.yhj.srim.service.crawl.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yhj.srim.client.dto.DartFilingRow;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DartFilingParserTest {

    private final DartFilingParser parser = new DartFilingParser(new ObjectMapper());

    @Test
    @DisplayName("OpenDART list.json 응답에서 연간 filing 메타를 파싱한다.")
    void parse_returnsFilingRows() {
        String json = """
                {
                  "status":"000",
                  "message":"정상",
                  "list":[
                    {
                      "corp_code":"00126380",
                      "stock_code":"005930",
                      "report_nm":"사업보고서",
                      "rcept_no":"20250311001234",
                      "rcept_dt":"20250311",
                      "rm":"정"
                    }
                  ]
                }
                """;

        List<DartFilingRow> rows = parser.parse(json);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCorpCode()).isEqualTo("00126380");
        assertThat(rows.get(0).getStockCode()).isEqualTo("005930");
        assertThat(rows.get(0).getReportNm()).isEqualTo("사업보고서");
        assertThat(rows.get(0).getRceptNo()).isEqualTo("20250311001234");
        assertThat(rows.get(0).getRceptDt()).isEqualTo("20250311");
        assertThat(rows.get(0).getRm()).isEqualTo("정");
    }
}
