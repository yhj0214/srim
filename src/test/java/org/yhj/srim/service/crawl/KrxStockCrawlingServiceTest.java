package org.yhj.srim.service.crawl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CrawlingError;
import org.yhj.srim.service.crawl.client.KrxHttpClient;
import org.yhj.srim.service.crawl.dto.StockCodeDraft;
import org.yhj.srim.service.crawl.parser.KrxParser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KrxStockCrawlingServiceTest {

    @Mock
    KrxHttpClient krxHttpClient;

    @Mock
    KrxParser htmlParser;

    @Mock
    KrxParser csvParser;

    @InjectMocks
    KrxStockCrawlingService service;

    @Test
    @DisplayName("HTML 내용일 때 HTML 파서가 선택된다.")
    void selectsHtmlParser() {
        // given
        String html = "<html><table></table></html>";
        when(krxHttpClient.get(anyString())).thenReturn(html);
        when(htmlParser.supports(html)).thenReturn(true);
        when(csvParser.supports(html)).thenReturn(false);
        when(htmlParser.parse(html, "KOSPI")).thenReturn(List.of());

        // when
        List<StockCodeDraft> result = service.fetchStockList("KOSPI");

        // then
        assertThat(result).isEmpty();
        verify(htmlParser).parse(html, "KOSPI");
        verify(csvParser, never()).parse(anyString(), anyString());
    }

    @Test
    @DisplayName("CSV 내용일 때 CSV 파서가 선택된다.")
    void selectsCsvParser() {
        // given
        String csv = "회사명,종목코드,업종\n테스트,123456,제조업";
        when(krxHttpClient.get(anyString())).thenReturn(csv);
        when(htmlParser.supports(csv)).thenReturn(false);
        when(csvParser.supports(csv)).thenReturn(true);
        when(csvParser.parse(csv, "KOSPI")).thenReturn(List.of());

        // when
        List<StockCodeDraft> result = service.fetchStockList("KOSPI");

        // then
        assertThat(result).isEmpty();
        verify(csvParser).parse(csv, "KOSPI");
        verify(htmlParser, never()).parse(anyString(), anyString());
    }

    @Test
    @DisplayName("지원 가능한 파서가 없으면 예외를 던진다.")
    void throwsWhenNoParser() {
        // given
        String content = "unknown";
        when(krxHttpClient.get(anyString())).thenReturn(content);
        when(htmlParser.supports(content)).thenReturn(false);
        when(csvParser.supports(content)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> service.fetchStockList("KOSPI"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(CrawlingError.KRX_REQUEST_FAILED);
    }

    @Test
    @DisplayName("복수 파서가 지원할 경우 리스트 순서의 파서를 선택한다.")
    void selectsFirstSupportingParser() {
        // given
        String content = "<html><table></table></html>";
        when(krxHttpClient.get(anyString())).thenReturn(content);
        when(htmlParser.supports(content)).thenReturn(true);
        when(csvParser.supports(content)).thenReturn(true);
        when(htmlParser.parse(content, "KOSPI")).thenReturn(List.of());

        // when
        List<StockCodeDraft> result = service.fetchStockList("KOSPI");

        // then
        assertThat(result).isEmpty();
        verify(htmlParser).parse(content, "KOSPI");
        verify(csvParser, never()).parse(anyString(), anyString());
    }

    @Test
    @DisplayName("클라이언트 예외는 그대로 전파된다.")
    void propagatesClientException() {
        // given
        CustomException ex = new CustomException(CrawlingError.KRX_REQUEST_FAILED);
        when(krxHttpClient.get(anyString())).thenThrow(ex);

        // when & then
        assertThatThrownBy(() -> service.fetchStockList("KOSPI"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(CrawlingError.KRX_REQUEST_FAILED);
    }

    @Test
    @DisplayName("파서가 빈 리스트를 반환해도 정상 동작한다.")
    void returnsEmptyWhenParserReturnsEmpty() {
        // given
        String content = "회사명,종목코드,업종\n테스트,123456,제조업";
        when(krxHttpClient.get(anyString())).thenReturn(content);
        when(htmlParser.supports(content)).thenReturn(false);
        when(csvParser.supports(content)).thenReturn(true);
        when(csvParser.parse(content, "KOSPI")).thenReturn(List.of());

        // when
        List<StockCodeDraft> result = service.fetchStockList("KOSPI");

        // then
        assertThat(result).isEmpty();
    }
}
