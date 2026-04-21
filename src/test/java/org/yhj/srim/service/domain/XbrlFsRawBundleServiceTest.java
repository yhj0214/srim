package org.yhj.srim.service.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.client.DartReportType;
import org.yhj.srim.repository.CompanyRepository;
import org.yhj.srim.repository.StockCodeRepository;
import org.yhj.srim.repository.XbrlContextRepository;
import org.yhj.srim.repository.XbrlDocumentRepository;
import org.yhj.srim.repository.XbrlFactRepository;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.StockCode;
import org.yhj.srim.repository.entity.XbrlContext;
import org.yhj.srim.repository.entity.XbrlDocument;
import org.yhj.srim.repository.entity.XbrlFact;
import org.yhj.srim.service.dto.FsRawBundle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class XbrlFsRawBundleServiceTest {

    @Autowired
    XbrlFsRawBundleService xbrlFsRawBundleService;

    @Autowired
    StockCodeRepository stockCodeRepository;

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    XbrlDocumentRepository xbrlDocumentRepository;

    @Autowired
    XbrlContextRepository xbrlContextRepository;

    @Autowired
    XbrlFactRepository xbrlFactRepository;

    @Test
    @DisplayName("회사/연도 기준으로 XBRL raw bundle을 조립해 FsRawBundle로 반환한다.")
    void buildRawBundle_returnsCurrentAndPreviousMetricMaps() {
        Company company = saveCompany("910004", "00126384");
        Long companyId = company.getCompanyId();

        XbrlDocument current = saveDocument(company, 2024, "20250321002000", LocalDateTime.of(2025, 3, 21, 11, 0));
        saveAnnualFacts(current, "1014156314426", "21600800986", "443723178425");

        XbrlDocument previous = saveDocument(company, 2023, "20240321001000", LocalDateTime.of(2024, 3, 21, 10, 0));
        saveAnnualFacts(previous, "936525061005", "16472354950", "419651809690");

        FsRawBundle rawBundle = xbrlFsRawBundleService.buildRawBundle(
                companyId,
                2024,
                DartReportType.ANNUAL,
                "CFS"
        );

        assertThat(rawBundle.curr())
                .containsEntry("SALES", new BigDecimal("1014156314426"))
                .containsEntry("NET_INC", new BigDecimal("21600800986"))
                .containsEntry("TOTAL_EQUITY", new BigDecimal("443723178425"));

        assertThat(rawBundle.prev())
                .containsEntry("SALES", new BigDecimal("936525061005"))
                .containsEntry("NET_INC", new BigDecimal("16472354950"))
                .containsEntry("TOTAL_EQUITY", new BigDecimal("419651809690"));
    }

    @Test
    @DisplayName("전년도 문서가 없으면 prev는 빈 맵으로 반환한다.")
    void buildRawBundle_withoutPreviousDocument_returnsEmptyPrevMap() {
        Company company = saveCompany("910005", "00126385");
        Long companyId = company.getCompanyId();

        XbrlDocument current = saveDocument(company, 2024, "20250321003000", LocalDateTime.of(2025, 3, 21, 12, 0));
        saveAnnualFacts(current, "1200", "300", "500");

        FsRawBundle rawBundle = xbrlFsRawBundleService.buildRawBundle(
                companyId,
                2024,
                DartReportType.ANNUAL,
                "CFS"
        );

        assertThat(rawBundle.curr())
                .containsEntry("SALES", new BigDecimal("1200"))
                .containsEntry("NET_INC", new BigDecimal("300"))
                .containsEntry("TOTAL_EQUITY", new BigDecimal("500"));
        assertThat(rawBundle.prev()).isEmpty();
    }

    private Company saveCompany(String ticker, String corpCode) {
        StockCode stockCode = stockCodeRepository.save(StockCode.builder()
                .companyName("XBRL Bundle Test " + ticker)
                .tickerKrx(ticker)
                .market("TEST")
                .dartCorpCode(corpCode)
                .build());

        return companyRepository.save(Company.builder()
                .stockCode(stockCode)
                .currency("KRW")
                .build());
    }

    private XbrlDocument saveDocument(Company company, int year, String rceptNo, LocalDateTime parsedAt) {
        return xbrlDocumentRepository.save(XbrlDocument.builder()
                .corpCode("00126380")
                .company(company)
                .rceptNo(rceptNo)
                .reprtCode("11011")
                .bsnsYear(year)
                .fsDiv("CFS")
                .reportTp("연간")
                .sourceUrl("https://example.com/" + rceptNo + ".zip")
                .localPath("/tmp/" + rceptNo + ".zip")
                .taxonomyVersion("https://taxonomy.example/ifrs-full.xsd")
                .parseVersion("test-v1")
                .parsedAt(parsedAt)
                .build());
    }

    private void saveAnnualFacts(XbrlDocument document, String sales, String netIncome, String totalEquity) {
        XbrlContext durationContext = xbrlContextRepository.save(XbrlContext.builder()
                .document(document)
                .contextRef("ctx-duration-" + document.getBsnsYear())
                .contextRefHash(String.format("%-64s", "ctx-duration-" + document.getBsnsYear()).replace(' ', 'x'))
                .entityIdentifier("00126380")
                .periodType("duration")
                .periodStart(LocalDate.of(document.getBsnsYear(), 1, 1))
                .periodEnd(LocalDate.of(document.getBsnsYear(), 12, 31))
                .instantDate(null)
                .dimensionsJson("[]")
                .memberSignature(null)
                .build());

        XbrlContext instantContext = xbrlContextRepository.save(XbrlContext.builder()
                .document(document)
                .contextRef("ctx-instant-" + document.getBsnsYear())
                .contextRefHash(String.format("%-64s", "ctx-instant-" + document.getBsnsYear()).replace(' ', 'x'))
                .entityIdentifier("00126380")
                .periodType("instant")
                .periodStart(null)
                .periodEnd(null)
                .instantDate(LocalDate.of(document.getBsnsYear(), 12, 31))
                .dimensionsJson("[]")
                .memberSignature(null)
                .build());

        xbrlFactRepository.save(XbrlFact.builder()
                .document(document)
                .context(durationContext)
                .contextRef(durationContext.getContextRef())
                .conceptQname("ifrs-full:Revenue")
                .conceptLocalName("Revenue")
                .labelKo("매출액")
                .statementRole("income-statement")
                .unitRef("KRW")
                .decimals("0")
                .valueRaw(sales)
                .valueNumeric(new BigDecimal(sales))
                .isNil(false)
                .memberSignature(null)
                .orderHint(1)
                .build());

        xbrlFactRepository.save(XbrlFact.builder()
                .document(document)
                .context(durationContext)
                .contextRef(durationContext.getContextRef())
                .conceptQname("ifrs-full:ProfitLoss")
                .conceptLocalName("ProfitLoss")
                .labelKo("당기순이익")
                .statementRole("income-statement")
                .unitRef("KRW")
                .decimals("0")
                .valueRaw(netIncome)
                .valueNumeric(new BigDecimal(netIncome))
                .isNil(false)
                .memberSignature(null)
                .orderHint(2)
                .build());

        xbrlFactRepository.save(XbrlFact.builder()
                .document(document)
                .context(instantContext)
                .contextRef(instantContext.getContextRef())
                .conceptQname("ifrs-full:Equity")
                .conceptLocalName("Equity")
                .labelKo("자본총계")
                .statementRole("balance-sheet")
                .unitRef("KRW")
                .decimals("0")
                .valueRaw(totalEquity)
                .valueNumeric(new BigDecimal(totalEquity))
                .isNil(false)
                .memberSignature(null)
                .orderHint(3)
                .build());
    }
}
