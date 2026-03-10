package org.yhj.srim.service.crawl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yhj.srim.client.KisSpreadClient;
import org.yhj.srim.client.dto.KisSpreadRow;
import org.yhj.srim.service.crawl.parser.KisSpreadParser;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class KisSpreadCrawlingService {

    private final KisSpreadClient kisSpreadClient;
    private final KisSpreadParser kisSpreadParser;

    public List<KisSpreadRow> fetchSpreadRows(LocalDate date) {
        String html = kisSpreadClient.fetchSpreadHtml(date);
        return kisSpreadParser.parseSpreadTable(html);
    }
}
