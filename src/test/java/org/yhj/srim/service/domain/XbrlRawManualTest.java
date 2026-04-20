package org.yhj.srim.service.domain;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.yhj.srim.client.DartReportType;
import org.yhj.srim.repository.CompanyRepository;
import org.yhj.srim.repository.StockCodeRepository;
import org.yhj.srim.repository.XbrlContextRepository;
import org.yhj.srim.repository.XbrlDocumentRepository;
import org.yhj.srim.repository.XbrlFactRepository;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.StockCode;
import org.yhj.srim.repository.entity.XbrlDocument;
import org.yhj.srim.service.crawl.XbrlFinancialStatementCrawlingService;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("manual")
class XbrlRawManualTest {

    @Autowired
    XbrlFinancialStatementCrawlingService xbrlFinancialStatementCrawlingService;

    @Autowired
    XbrlRawService xbrlRawService;

    @Autowired
    XbrlDocumentRepository xbrlDocumentRepository;

    @Autowired
    XbrlContextRepository xbrlContextRepository;

    @Autowired
    XbrlFactRepository xbrlFactRepository;

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    StockCodeRepository stockCodeRepository;

    @Autowired
    Environment environment;

    @Test
    @DisplayName("실제 DART XBRL 원문을 다운로드하고 raw document/context/fact를 저장한다.")
    void collectAndSaveXbrlRaw_realCall_smoke() {
        String apiKey = environment.getProperty("dart.api.key");
        Assumptions.assumeTrue(hasText(apiKey), "dart.api.key 프로퍼티가 없어 skip");

        // BGF 2024 사업보고서(CFS) 예시
        String corpCode = "00219097";
        String rceptNo = "20260310002820";
        int bsnsYear = 2024;
        String fsDiv = "CFS";
        Long companyId = ensureCompanyFixture(corpCode).getCompanyId();

        XbrlFinancialStatementCrawlingService.XbrlRawBatch batch =
                xbrlFinancialStatementCrawlingService.crawlFinancialStatementsXbrl(
                        corpCode,
                        rceptNo,
                        bsnsYear,
                        DartReportType.ANNUAL,
                        fsDiv
                );

        assertThat(batch.archiveBytes()).isNotEmpty();
        byte[] archiveBytes = batch.archiveBytes();
        System.out.println("archive size = " + archiveBytes.length);
        if (archiveBytes.length >= 2) {
            System.out.println("archive magic = " + (char) archiveBytes[0] + (char) archiveBytes[1]);
        }
        assertThat(batch.parseResult().contexts()).isNotEmpty();
        assertThat(batch.parseResult().facts()).isNotEmpty();

        Long documentId = xbrlRawService.saveFinancialStatementsXbrl(corpCode, companyId, batch);

        Optional<XbrlDocument> storedDocument = xbrlDocumentRepository.findById(documentId);
        assertThat(storedDocument).isPresent();
        assertThat(storedDocument.get().getLocalPath()).isNotBlank();
        assertThat(storedDocument.get().getParseVersion()).isNotBlank();

        long contextCount = xbrlContextRepository.count();
        long factCount = xbrlFactRepository.count();

        assertThat(contextCount).isPositive();
        assertThat(factCount).isPositive();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private Company ensureCompanyFixture(String corpCode) {
        final String fixtureMarket = "TEST";
        final String fixtureTicker = "999999";

        StockCode stockCode = stockCodeRepository.findByMarketAndTickerKrx(fixtureMarket, fixtureTicker)
                .map(existing -> {
                    existing.setDartCorpCode(corpCode);
                    if (!hasText(existing.getCompanyName())) {
                        existing.setCompanyName("XBRL Manual Test Fixture");
                    }
                    return stockCodeRepository.save(existing);
                })
                .orElseGet(() -> stockCodeRepository.save(StockCode.builder()
                        .market(fixtureMarket)
                        .tickerKrx(fixtureTicker)
                        .dartCorpCode(corpCode)
                        .companyName("XBRL Manual Test Fixture")
                        .industry("TEST")
                        .build()));

        return companyRepository.findByStockCode_StockId(stockCode.getStockId())
                .orElseGet(() -> companyRepository.save(Company.builder()
                        .stockCode(stockCode)
                        .sharesOutstanding(1L)
                        .faceValue(BigDecimal.valueOf(5000))
                        .currency("KRW")
                        .sector("TEST")
                        .notes("Manual XBRL raw ingestion test fixture")
                        .build()));
    }
}
