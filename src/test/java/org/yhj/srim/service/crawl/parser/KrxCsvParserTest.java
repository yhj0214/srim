package org.yhj.srim.service.crawl.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yhj.srim.service.crawl.dto.StockCodeDraft;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KrxCsvParserTest {

    private final KrxCsvParser parser = new KrxCsvParser();

    @Test
    @DisplayName("CSV 내용을 올바르게 파싱한다.")
    void parseCsv_success() {
        // given
        String csv = """
                회사명,종목코드,업종,주요제품,상장일,결산월,대표자명,홈페이지,지역
                테스트회사,123456,제조업,제품,2024/01/02,12월,홍길동,http://example.com,서울
                """;

        // when
        List<StockCodeDraft> result = parser.parse(csv, "KOSPI");

        // then
        assertThat(result).hasSize(1);
        StockCodeDraft draft = result.get(0);
        assertThat(draft.getCompanyName()).isEqualTo("테스트회사");
        assertThat(draft.getTickerKrx()).isEqualTo("123456");
        assertThat(draft.getIndustry()).isEqualTo("제조업");
        assertThat(draft.getListingDate()).isEqualTo(LocalDate.of(2024, 1, 2));
        assertThat(draft.getFiscalYearEndMonth()).isEqualTo(12);
        assertThat(draft.getHomepageUrl()).isEqualTo("http://example.com");
        assertThat(draft.getRegion()).isEqualTo("서울");
        assertThat(draft.getMarket()).isEqualTo("KOSPI");
    }

    @Test
    @DisplayName("supports는 CSV 문자열에 대해 true를 반환한다.")
    void supportsCsv() {
        // given
        String csv = "회사명,종목코드,업종\n테스트,123456,제조업";
        String html = "<html><table></table></html>";

        // when / then
        assertThat(parser.supports(csv)).isTrue();
        assertThat(parser.supports(html)).isFalse();
    }

    @Test
    @DisplayName("필수 값 누락 시 해당 행은 제외된다.")
    void skipWhenRequiredMissing() {
        // given
        String csv = """
                회사명,종목코드,업종
                ,123456,제조업
                테스트회사,,제조업
                """;

        // when
        List<StockCodeDraft> result = parser.parse(csv, "KOSPI");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("날짜 포맷이 yyyy.MM.dd 형태여도 파싱된다.")
    void parseDateWithDots() {
        // given
        String csv = """
                회사명,종목코드,업종,주요제품,상장일,결산월,대표자명,홈페이지,지역
                테스트회사,123456,제조업,제품,2024.02.03,12월,홍길동,http://example.com,서울
                """;

        // when
        List<StockCodeDraft> result = parser.parse(csv, "KOSPI");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getListingDate()).isEqualTo(LocalDate.of(2024, 2, 3));
    }

    @Test
    @DisplayName("CSV 따옴표로 감싼 값 안의 쉼표도 올바르게 파싱된다.")
    void parseQuotedComma() {
        // given
        String csv = """
                회사명,종목코드,업종,주요제품,상장일,결산월,대표자명,홈페이지,지역
                "테스트,회사",123456,제조업,제품,2024/01/02,12월,홍길동,http://example.com,서울
                """;

        // when
        List<StockCodeDraft> result = parser.parse(csv, "KOSPI");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCompanyName()).isEqualTo("테스트,회사");
    }

    @Test
    @DisplayName("헤더만 있고 데이터가 없으면 빈 리스트를 반환한다.")
    void headerOnly() {
        // given
        String csv = "회사명,종목코드,업종\n";

        // when
        List<StockCodeDraft> result = parser.parse(csv, "KOSPI");

        // then
        assertThat(result).isEmpty();
    }
}
