package org.yhj.srim.client;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

@SpringBootTest
@Slf4j
class KisSpreadClientTest {

    @Autowired KisSpreadClient kisSpreadClient;

    @Test
    void fetchSpreadHtml_success() {
        // given
        LocalDate date = LocalDate.of(2025, 12, 03);

        // when
        String result = kisSpreadClient.fetchSpreadHtml(date);

        // then
        log.debug(result);
        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isBlank());
        Assertions.assertTrue(result.contains("검색결과") || result.contains("table"));
        log.debug("KIS HTML length={}", result.length());
    }
}
