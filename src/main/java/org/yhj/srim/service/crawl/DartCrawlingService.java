package org.yhj.srim.service.crawl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.client.DartClient;
import org.yhj.srim.client.dto.DartFsRow;
import org.yhj.srim.client.dto.DartShareStatusRow;
import org.yhj.srim.service.crawl.parser.DartFinancialStatementParser;
import org.yhj.srim.service.crawl.parser.DartShareStatusParser;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DartCrawlingService {

    private final DartClient dartClient;
    private final DartFinancialStatementParser dartFinancialStatementParser;
    private final DartShareStatusParser dartShareStatusParser;

    public List<DartFsRow> crawlAnnualFinancial(String corpCode, int year) {
        String body = dartClient.fetchAnnualFinancialStatementsBody(corpCode, year);
        List<DartFsRow> rows = dartFinancialStatementParser.parse(body);
        if (rows.isEmpty()) {
            log.warn("{}년도에 크롤링된 데이터가 없습니다.", year);
        }
        return rows;
    }

    public List<DartShareStatusRow> crawlShareStatus(String corpCode, int year) {
        String body = dartClient.fetchShareStatusBody(corpCode, year);
        return dartShareStatusParser.parse(body);
    }
}
