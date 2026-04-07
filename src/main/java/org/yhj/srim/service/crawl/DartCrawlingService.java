package org.yhj.srim.service.crawl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.client.DartClient;
import org.yhj.srim.client.DartReportType;
import org.yhj.srim.client.dto.DartFsRow;
import org.yhj.srim.client.dto.DartShareStatusRow;
import org.yhj.srim.service.crawl.parser.DartFinancialStatementParser;
import org.yhj.srim.service.crawl.parser.DartShareStatusParser;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DartCrawlingService {

    private final DartClient dartClient;
    private final DartFinancialStatementParser dartFinancialStatementParser;
    private final DartShareStatusParser dartShareStatusParser;

    public List<DartFsRow> crawlFinancial(String corpCode, int year, DartReportType reportType) {
        String body = dartClient.fetchFinancialStatementsBody(corpCode, year, reportType);
        List<DartFsRow> rows = dartFinancialStatementParser.parse(body);
        if (rows.isEmpty()) {
            log.warn("{}년도 {} 재무제표 데이터가 없습니다.", year, reportType.label());
        }
        return rows;
    }

    public List<FinancialStatementBatch> crawlFinancialStatements(String corpCode, int year, List<DartReportType> reportTypes) {
        List<FinancialStatementBatch> result = new ArrayList<>();

        for (DartReportType reportType : reportTypes) {
            List<DartFsRow> rows = crawlFinancial(corpCode, year, reportType);
            if (!rows.isEmpty()) {
                result.add(new FinancialStatementBatch(reportType, rows));
            }
        }

        return result;
    }

    public List<DartShareStatusRow> crawlShareStatus(String corpCode, int year) {
        String body = dartClient.fetchShareStatusBody(corpCode, year);
        return dartShareStatusParser.parse(body);
    }

    public record FinancialStatementBatch(DartReportType reportType, List<DartFsRow> rows) {
    }
}
