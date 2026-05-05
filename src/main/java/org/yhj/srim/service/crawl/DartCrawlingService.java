package org.yhj.srim.service.crawl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.client.DartClient;
import org.yhj.srim.client.DartReportType;
import org.yhj.srim.client.dto.DartFilingRow;
import org.yhj.srim.client.dto.DartShareStatusRow;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.CrawlingError;
import org.yhj.srim.service.crawl.parser.DartFilingParser;
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
    private final DartShareStatusParser dartShareStatusParser;


    public List<DartFilingRow> crawlFilings(String corpCode, int year, DartReportType reportType) {
        String body = dartClient.fetchAnnualFilingListBody(corpCode, year);
        List<DartFilingRow> rows = new ArrayList<>(dartFilingParser.parse(body).stream()
                .filter(row -> matchesReportType(row, year, reportType))
                .toList());
        rows.sort(Comparator
                .comparing(DartFilingRow::getRceptDt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(DartFilingRow::getRceptNo, Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    public List<DartFilingRow> crawlAnnualFilings(String corpCode, int year) {
        return crawlFilings(corpCode, year, DartReportType.ANNUAL);
    }


    public DartFilingRow crawlLatestFiling(String corpCode, int year, DartReportType reportType) {
        List<DartFilingRow> rows = crawlFilings(corpCode, year, reportType);
        if (rows.isEmpty()) {
            throw new CustomException(
                    CrawlingError.DART_DISCLOSURE_NOT_FOUND,
                    "corpCode=" + corpCode + ", year=" + year + ", reprtCode=" + reportType.code()
            );
        }
        return rows.get(0);
    }

    public DartFilingRow crawlLatestAnnualFiling(String corpCode, int year) {
        return crawlLatestFiling(corpCode, year, DartReportType.ANNUAL);
    }

    private boolean matchesReportType(DartFilingRow row, int year, DartReportType reportType) {
        String reportNm = row.getReportNm();
        if (reportNm == null) {
            return false;
        }

        return switch (reportType) {
            case ANNUAL -> reportNm.contains("사업보고서");
            case HALF_YEAR -> reportNm.contains("반기보고서");
            case FIRST_QUARTER, THIRD_QUARTER -> reportNm.contains("분기보고서")
                    && reportNm.contains(String.format("%d.%02d", year, reportType.periodEnd(year).getMonthValue()));
        };
    }

    public List<DartShareStatusRow> crawlShareStatus(String corpCode, int year) {
        String body = dartClient.fetchShareStatusBody(corpCode, year);
        return dartShareStatusParser.parse(body);
    }

}
