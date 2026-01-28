package org.yhj.srim.client;

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.yhj.srim.client.dto.DartFsRow;
import org.yhj.srim.client.dto.DartShareStatusRow;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Slf4j
@ActiveProfiles("test")
class DartClientTest {

    @Autowired DartClient dartClient;

    @Autowired
    Environment env;

    @Test
    void configCheck(){
        log.info("activeProfiles={}", Arrays.toString(env.getActiveProfiles()));
        log.info("dart.api.key={}", env.getProperty("dart.api.key"));
        log.info("dart.api.connect-timeout-ms={}", env.getProperty("dart.api.connect-timeout-ms"));
        log.info("dart.api.read-timeout-ms={}", env.getProperty("dart.api.read-timeout-ms"));

    }

    @Test
    void fetchAnnualFinancialStatementsSuccess(){
        String apiKey = env.getProperty("dart.api.key");
        if(!hasText(apiKey)) {
            System.out.println("API_KEY 프로퍼티가 없어 테스트를 스킵");
            return;
        }

        // when
        List<DartFsRow> rows = dartClient.fetchAnnualFinancialStatements("00126380", 2021);

        // then
        String meta = rows.get(0).toString();
        String[] temp = meta.split(",");
        for(int i = 0; i < temp.length; i++) {
            System.out.println(temp[i]);
        }

        log.info("[DART FS] firstRow={}", rows.get(0));

        for (DartFsRow row : rows) {
            log.debug("[DART FS] {}", row);
        }

        Assertions.assertThat(rows).isNotEmpty();
    }

    @Test
    void fetchShareStatusSuccess(){
        String apiKey = env.getProperty("dart.api.key");
        if(!hasText(apiKey)) {
            log.warn("dart.api.key 프로퍼티가 없어 테스트를 스킵합니다.");
            return;
        }

        // given
        String corpCode = "00126380";
        int year = 2021;

        List<DartShareStatusRow> rows = dartClient.fetchShareStatus(corpCode, year);


        log.info("[DART SHARE] corpCode={}, year={}, rows.size={}",
                corpCode, year, (rows == null ? -1 : rows.size()));

        if (rows == null || rows.isEmpty()) {
            Assertions.fail("DART SHARE 응답이 비어있습니다. (corpCode=" + corpCode + ", year=" + year + ")");
            return;
        }

        String[] meta = rows.get(0).toString().split(", ");
        System.out.println("메타 정보 출력");
        for(String s : meta){
            System.out.println(s);
        }

        for(DartShareStatusRow row : rows) {
            log.debug("[DART SHARE] {}", row);
        }

    }

    @Test
    void fetchShareStatus_realCall_smoke(){

        String apiKey = env.getProperty("dart.api.key");
        Assumptions.assumeTrue(hasText(apiKey),
                "dart.api.key 프로퍼티가 없어 skip");

        // given
        String corpCode = "00126380";
        int year = 2021;

        // when
        List<DartShareStatusRow> rows = dartClient.fetchShareStatus(corpCode, year);

        // then
        Assertions.assertThat(rows).isNotEmpty();

        if(!rows.isEmpty()) {
            String[] temp = rows.get(0).toString().split(", ");
            for(String s : temp){
                System.out.println(s);
            }
        }

    }

    private boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }


}