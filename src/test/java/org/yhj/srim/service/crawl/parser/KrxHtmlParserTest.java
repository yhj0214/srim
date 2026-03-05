package org.yhj.srim.service.crawl.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yhj.srim.service.crawl.dto.StockCodeDraft;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KrxHtmlParserTest {

    private final KrxHtmlParser parser = new KrxHtmlParser();

    @Test
    @DisplayName("HTML 내용을 올바르게 파싱한다.")
    void parseHtml_success() {
        // given
        String html = """
                <html><body>
                  <table>
                    <tr>
                      <th>회사명</th><th>시장구분</th><th>종목코드</th><th>업종</th>
                      <th>주요제품</th><th>상장일</th><th>결산월</th><th>대표자명</th>
                      <th>홈페이지</th><th>지역</th>
                    </tr>
                    <tr>
                      <td>테스트회사</td><td>코스피</td><td>123456</td><td>제조업</td>
                      <td>제품</td><td>2024/01/02</td><td>12월</td><td>홍길동</td>
                      <td>http://example.com</td><td>서울</td>
                    </tr>
                  </table>
                </body></html>
                """;

        // when
        List<StockCodeDraft> result = parser.parse(html, "KOSPI");

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
    @DisplayName("supports는 HTML 문자열에 대해 true를 반환한다.")
    void supportsHtml() {
        // given
        String html = "<html><table></table></html>";
        String csv = "회사명,종목코드,업종\n테스트,123456,제조업";

        // when / then
        assertThat(parser.supports(html)).isTrue();
        assertThat(parser.supports(csv)).isFalse();
    }

    @Test
    @DisplayName("필수 값 누락 시 해당 행은 제외된다.")
    void skipWhenRequiredMissing() {
        // given
        String html = """
                <html><body>
                  <table>
                    <tr><th>회사명</th><th>시장구분</th><th>종목코드</th><th>업종</th></tr>
                    <tr><td></td><td>코스피</td><td>123456</td><td>제조업</td></tr>
                    <tr><td>테스트회사</td><td>코스피</td><td></td><td>제조업</td></tr>
                  </table>
                </body></html>
                """;

        // when
        List<StockCodeDraft> result = parser.parse(html, "KOSPI");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("시장 구분이 코스닥/코넥스/코스피로 변환된다.")
    void marketMapping() {
        // given
        String html = """
                <html><body>
                  <table>
                    <tr><th>회사명</th><th>시장구분</th><th>종목코드</th><th>업종</th></tr>
                    <tr><td>회사A</td><td>코스닥</td><td>123456</td><td>제조업</td></tr>
                    <tr><td>회사B</td><td>코넥스</td><td>654321</td><td>IT</td></tr>
                    <tr><td>회사C</td><td>코스피</td><td>654322</td><td>IK</td></tr>
                  </table>
                </body></html>
                """;

        // when
        List<StockCodeDraft> result = parser.parse(html, "KOSPI");

        // then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getMarket()).isEqualTo("KOSDAQ");
        assertThat(result.get(1).getMarket()).isEqualTo("KONEX");
        assertThat(result.get(2).getMarket()).isEqualTo("KOSPI");
    }

    @Test
    @DisplayName("날짜 포맷이 yyyy.MM.dd 형태여도 파싱된다.")
    void parseDateWithDots() {
        // given
        String html = """
                <html><body>
                  <table>
                    <tr><th>회사명</th><th>시장구분</th><th>종목코드</th><th>업종</th><th></th><th>상장일</th></tr>
                    <tr><td>테스트회사</td><td>코스피</td><td>123456</td><td>제조업</td><td></td><td>2024.02.03</td></tr>
                  </table>
                </body></html>
                """;

        // when
        List<StockCodeDraft> result = parser.parse(html, "KOSPI");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getListingDate()).isEqualTo(LocalDate.of(2024, 2, 3));
    }

    @Test
    @DisplayName("시장 구분이 없으면 기본 marketType을 사용한다.")
    void defaultMarketWhenMissing() {
        // given
        String html = """
                <html><body>
                  <table>
                    <tr><th>회사명</th><th>시장구분</th><th>종목코드</th><th>업종</th></tr>
                    <tr><td>테스트회사</td><td></td><td>123456</td><td>제조업</td></tr>
                  </table>
                </body></html>
                """;

        // when
        List<StockCodeDraft> result = parser.parse(html, "KOSDAQ");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMarket()).isEqualTo("KOSDAQ");
    }
}
