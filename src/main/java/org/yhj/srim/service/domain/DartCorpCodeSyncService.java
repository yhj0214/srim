package org.yhj.srim.service.domain;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.service.crawl.parser.DartCorpCodeXmlParser;

import java.io.InputStream;
import java.util.List;

import static org.yhj.srim.common.exception.code.CommonError.INTERNAL_ERROR;

@Service
@RequiredArgsConstructor
@Slf4j
public class DartCorpCodeSyncService {
    private final JdbcTemplate jdbcTemplate;
    private final ResourceLoader resourceLoader;
    private final DartCorpCodeXmlParser dartCorpCodeXmlParser;

    @Value("${dart.corp-code.xml-path:classpath:sql/CORPCODE.xml}")
    private String corpCodeXmlPath;

    /**
     * classpath:sql/CORPCODE.xml 을 읽어서
     * 1) dart_corp_map 테이블에 적재하고
     * 2) stock_code.dart_corp_code 를 대량 UPDATE 한다.
     */
    @Transactional
    public int syncFromXml(){
        log.info("=== DART corpCode 동기화 시작 ===");

        // 1) XML → dart_corp_map 적재
        int inserted = 0;
        try {
            // xml파일의 corp_code, corp_name, stock_code 저장
            inserted = loadXmlToTempTable();
        } catch (Exception e) {
            throw new CustomException(INTERNAL_ERROR, "DART corpCode XML 로드 실패");
        }
        log.info("dart_corp_map 적재 건수 = {}", inserted);

        // 2) stock_code 갱신
        int updated = jdbcTemplate.update("""
            UPDATE stock_code sc
            JOIN dart_corp_map d ON sc.ticker_krx = d.stock_code
            SET sc.dart_corp_code = d.corp_code
        """);
        log.info("stock_code.dart_corp_code 갱신 건수 = {}", updated);

        log.info("=== DART corpCode 동기화 완료 ===");

        return updated;
    }

    /**
     * classpath:sql/CORPCODE.xml 을 읽어서 dart_corp_map 테이블을 채운다.
     * @return INSERT 건수
     */
    private int loadXmlToTempTable() throws Exception {
        // 기존 데이터 비우기 (초기화용)
        jdbcTemplate.update("TRUNCATE TABLE dart_corp_map");

        // classpath 에서 파일 로드
        Resource resource = resourceLoader.getResource(corpCodeXmlPath);
        if (!resource.exists()) {
            throw new IllegalStateException("CORPCODE.xml 파일을 찾을 수 없습니다. (" + corpCodeXmlPath + ")");
        }

        List<Object[]> batch;
        try (InputStream is = resource.getInputStream()) {
            batch = dartCorpCodeXmlParser.parse(is);
        }

        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO dart_corp_map (corp_code, corp_name, stock_code) VALUES (?,?,?)",
                    batch
            );
        }

        return batch.size();
    }

}
