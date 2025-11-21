package org.yhj.srim.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DartCorpCodeSyncService {
    private final JdbcTemplate jdbcTemplate;

    /**
     * classpath:sql/CORPCODE.xml 을 읽어서
     * 1) dart_corp_map 테이블에 적재하고
     * 2) stock_code.dart_corp_code 를 대량 UPDATE 한다.
     */
    @Transactional
    public void syncFromXml() throws Exception {
        log.info("=== DART corpCode 동기화 시작 ===");

        // 1) XML → dart_corp_map 적재
        int inserted = loadXmlToTempTable();
        log.info("dart_corp_map 적재 건수 = {}", inserted);

        // 2) stock_code 갱신
        int updated = jdbcTemplate.update("""
            UPDATE stock_code sc
            JOIN dart_corp_map d ON sc.ticker_krx = d.stock_code
            SET sc.dart_corp_code = d.corp_code
        """);
        log.info("stock_code.dart_corp_code 갱신 건수 = {}", updated);

        log.info("=== DART corpCode 동기화 완료 ===");
    }

    /**
     * classpath:sql/CORPCODE.xml 을 읽어서 dart_corp_map 테이블을 채운다.
     * @return INSERT 건수
     */
    private int loadXmlToTempTable() throws Exception {
        // 기존 데이터 비우기 (초기화용)
        jdbcTemplate.update("TRUNCATE TABLE dart_corp_map");

        // classpath 에서 파일 로드
        Resource resource = new ClassPathResource("sql/CORPCODE.xml");
        if (!resource.exists()) {
            throw new IllegalStateException("CORPCODE.xml 파일을 찾을 수 없습니다. (classpath:sql/CORPCODE.xml)");
        }

        List<Object[]> batch = new ArrayList<>();

        try (InputStream is = resource.getInputStream()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(is);
            doc.getDocumentElement().normalize();

            NodeList list = doc.getElementsByTagName("list");
            log.info("CORPCODE.xml list 노드 개수 = {}", list.getLength());

            for (int i = 0; i < list.getLength(); i++) {
                Element e = (Element) list.item(i);

                String corpCode  = getTagText(e, "corp_code");   // 00126380
                String corpName  = getTagText(e, "corp_name");   // 삼성전자
                String stockCode = getTagText(e, "stock_code");  // 005930 (상장사만)

                // 비상장( stock_code 비어있는 경우 )는 대상이 아니니 스킵
                if (stockCode == null || stockCode.isBlank()) {
                    continue;
                }

                // 혹시 모를 공백 제거
                corpCode  = corpCode != null  ? corpCode.trim()  : null;
                corpName  = corpName != null  ? corpName.trim()  : null;
                stockCode = stockCode != null ? stockCode.trim() : null;

                batch.add(new Object[]{ corpCode, corpName, stockCode });
            }
        }

        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO dart_corp_map (corp_code, corp_name, stock_code) VALUES (?,?,?)",
                    batch
            );
        }

        return batch.size();
    }

    private String getTagText(Element e, String tagName) {
        NodeList nl = e.getElementsByTagName(tagName);
        if (nl.getLength() == 0) return null;
        return nl.item(0).getTextContent();
    }
}
