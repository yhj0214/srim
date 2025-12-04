package org.yhj.srim.client;

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.client.dto.DaliyPrice;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Transactional
@SpringBootTest
@Slf4j
class NaverClientTest {

    @Autowired
    private NaverClient naverClient;

    @Test
    void fetchDailyPrices_sccuess기간내_데이터정상조회() {
        // given
        String tickerKrx = "005930"; // 삼성전자
        LocalDate start = LocalDate.now().minusDays(10);
        LocalDate end = LocalDate.now();

        // when
        List<DaliyPrice> prices = naverClient.fetchDailyPrices(tickerKrx, start, end);
        log.debug(prices.toString());

        // then
        assertThat(prices).isNotEmpty();
        assertThat(prices)
                .allSatisfy(price -> {
                    LocalDate d = price.getDate();
                    assertThat(d).isBetween(start, end);
                });
    }
}