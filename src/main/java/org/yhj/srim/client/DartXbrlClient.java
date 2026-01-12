package org.yhj.srim.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class DartXbrlClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${dart.api.key}")
    private String dartApiKey;

    public byte[] downloadXbrlZip(String rceptNo, String reprtCode) {
        byte[] bytes = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/fnlttXbrl.xml")
                        .queryParam("crtfc_key", dartApiKey)
                        .queryParam("rcept_no", rceptNo)
                        .queryParam("reprt_code", reprtCode)
                        .build())
                .retrieve()
                .bodyToMono(byte[].class)
                .block();

        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("XBRL response empty. rceptNo=" + rceptNo);
        }

        if (!(bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K')) {
            String head = new String(bytes, 0, Math.min(bytes.length, 500), java.nio.charset.StandardCharsets.UTF_8);
            throw new IllegalStateException("XBRL is not zip. head=" + head);
        }

        return bytes;
    }
}