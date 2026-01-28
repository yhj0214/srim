package org.yhj.srim.client;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.yhj.srim.client.dto.KisSpreadRow;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Slf4j
class KisSpreadClientTest {

    @Autowired KisSpreadClient kisSpreadClient;

    @Test
    void fetchSpread_sucess(){
        // given
        LocalDate date = LocalDate.of(2025, 12, 03);

        // when
        String result = kisSpreadClient.fetchSpreadHtml(date);

        // then
        log.debug(result);
        Assertions.assertNotNull(result);
    }

    @Test
    void fetchSpreadRows_success(){
        // given
        LocalDate date = LocalDate.of(2025, 12, 03);

        // when
        List<KisSpreadRow> result = kisSpreadClient.fetchSpreadRows(date);

        // then
        for(KisSpreadRow row : result) log.debug(row.toString());

        Assertions.assertFalse(result.isEmpty());
    }

}