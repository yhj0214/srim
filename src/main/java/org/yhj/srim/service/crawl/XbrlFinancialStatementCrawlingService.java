package org.yhj.srim.service.crawl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.client.DartClient;
import org.yhj.srim.client.DartReportType;
import org.yhj.srim.service.crawl.dto.XbrlParseResult;
import org.yhj.srim.service.crawl.parser.XbrlParser;

@Service
@RequiredArgsConstructor
@Slf4j
public class XbrlFinancialStatementCrawlingService {

    private final DartClient dartClient;
    private final XbrlParser xbrlParser;

    public XbrlRawBatch crawlFinancialStatementsXbrl(String corpCode, String rceptNo, int bsnsYear, DartReportType reportType, String fsDiv) {

        byte[] archiveBytes = dartClient.fetchFinancialStatementsXbrlArchive(rceptNo, reportType);
        XbrlParseResult parseResult = xbrlParser.parse(archiveBytes);

        log.info("XBRL raw 파싱 완료 corpCode={}, rceptNo={}, reportType={}, contexts={}, facts={}",
                corpCode, rceptNo, reportType.label(), parseResult.contexts().size(), parseResult.facts().size());

        return new XbrlRawBatch(
                corpCode,
                rceptNo,
                bsnsYear,
                reportType.code(),
                reportType.label(),
                fsDiv,
                dartClient.buildFinancialStatementsXbrlUrl(rceptNo, reportType),
                archiveBytes,
                parseResult
        );
    }

    public record XbrlRawBatch(
            String corpCode,
            String rceptNo,
            int bsnsYear,
            String reprtCode,
            String reportTypeLabel,
            String fsDiv,
            String sourceUrl,
            byte[] archiveBytes,
            XbrlParseResult parseResult
    ) {
    }
}
