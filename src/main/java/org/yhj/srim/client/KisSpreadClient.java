package org.yhj.srim.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CrawlingError;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
@RequiredArgsConstructor
public class KisSpreadClient {

    private final WebClient kisWebClient;
    private static final DateTimeFormatter KIS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    public String fetchSpreadHtml(LocalDate date) {
        String startDt = date.format(KIS_DATE_FORMAT);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("startDt", startDt);

        log.info("KIS 스프레드 조회 호출 startDt={}", startDt);

        try {
            return kisWebClient.post()
                    .uri("/ratingsStatistics/statics_spread.do")
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            log.error("KIS 스프레드 조회 실패 date={}", startDt, e);
            throw new CustomException(CrawlingError.KIS_REQUEST_FAILED, "startDt=" + startDt);
        }
    }
}
