package org.yhj.srim.service.crawl.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jsoup.Connection;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CrawlingError;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsoupKrxHttpClientTest {

    @Test
    @DisplayName("IO 예외 발생 시 CustomException으로 변환한다.")
    void wrapsIOException() {
        // given
        JsoupKrxHttpClient client = new JsoupKrxHttpClient() {
            @Override
            Connection.Response executeRequest(String url) throws IOException {
                throw new IOException("요청 실패");
            }
        };

        // when & then
        assertThatThrownBy(() -> client.get("https://kind.krx.co.kr/"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(CrawlingError.KRX_REQUEST_FAILED);
    }
}
