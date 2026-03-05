package org.yhj.srim.service.crawl.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CrawlingError;

import java.io.IOException;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class JsoupKrxHttpClient implements KrxHttpClient {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    @Override
    public String get(String url) {
        try {
            Connection.Response response = executeRequest(url);

            String contentType = response.contentType();
            String charset = response.charset();
            if (charset == null && contentType != null) {
                charset = extractCharset(contentType);
            }

            if (charset == null || charset.isBlank()) {
                charset = "EUC-KR";
            }

            try {
                return new String(response.bodyAsBytes(), java.nio.charset.Charset.forName(charset));
            } catch (Exception e) {
                log.warn("KRX 응답 인코딩 실패 charset={}, fallback=EUC-KR", charset);
                return new String(response.bodyAsBytes(), java.nio.charset.Charset.forName("EUC-KR"));
            }
        } catch (IOException e) {
            log.error("KRX 크롤링 실패", e);
            throw new CustomException(CrawlingError.KRX_REQUEST_FAILED);
        }
    }

    Connection.Response executeRequest(String url) throws IOException {
        return Jsoup.connect(url)
                .method(Connection.Method.GET)
                .userAgent(USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml")
                .header("Accept-Language", "ko-KR,ko;q=0.9")
                .header("Referer", "https://kind.krx.co.kr/")
                .ignoreContentType(true)
                .timeout(30000)
                .maxBodySize(0)
                .execute();
    }

    private String extractCharset(String contentType) {
        String lower = contentType.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf("charset=");
        if (idx < 0) return null;
        String value = lower.substring(idx + "charset=".length()).trim();
        int semi = value.indexOf(';');
        if (semi >= 0) {
            value = value.substring(0, semi).trim();
        }
        if (value.isEmpty()) return null;
        return value;
    }
}
