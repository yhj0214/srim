package org.yhj.srim.service.crawl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.client.DartClient;
import org.yhj.srim.client.DartReportType;
import org.yhj.srim.client.dto.DartFilingRow;
import org.yhj.srim.client.dto.DartFsRow;
import org.yhj.srim.client.dto.DartShareStatusRow;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CrawlingError;
import org.yhj.srim.service.crawl.parser.DartFilingParser;
import org.yhj.srim.service.crawl.parser.DartFinancialStatementParser;
import org.yhj.srim.service.crawl.parser.DartShareStatusParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DartCrawlingService {

    private final DartClient dartClient;
    private final DartFilingParser dartFilingParser;
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

    public List<DartFilingRow> crawlAnnualFilings(String corpCode, int year) {
        String body = dartClient.fetchAnnualFilingListBody(corpCode, year);
        List<DartFilingRow> rows = new ArrayList<>(dartFilingParser.parse(body).stream()
                .filter(this::isAnnualReport)
                .toList());
        rows.sort(Comparator
                .comparing(DartFilingRow::getRceptDt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(DartFilingRow::getRceptNo, Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    public DartFilingRow crawlLatestAnnualFiling(String corpCode, int year) {
        List<DartFilingRow> rows = crawlAnnualFilings(corpCode, year);
        if (rows.isEmpty()) {
            throw new CustomException(
                    CrawlingError.DART_DISCLOSURE_NOT_FOUND,
                    "corpCode=" + corpCode + ", year=" + year
            );
        }
        return rows.get(0);
    }

    private boolean isAnnualReport(DartFilingRow row) {
        String reportNm = row.getReportNm();
        return reportNm != null && reportNm.contains("사업보고서");
    }

    public List<DartShareStatusRow> crawlShareStatus(String corpCode, int year) {
        String body = dartClient.fetchShareStatusBody(corpCode, year);
        return dartShareStatusParser.parse(body);
    }

    public record FinancialStatementBatch(DartReportType reportType, List<DartFsRow> rows) {
    }
}
