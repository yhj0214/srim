package org.yhj.srim.service.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.repository.CompanyRepository;
import org.yhj.srim.repository.FinMetricValueRepository;
import org.yhj.srim.repository.FinPeriodRepository;
import org.yhj.srim.repository.StockCodeRepository;
import org.yhj.srim.repository.StockShareStatusRepository;
import org.yhj.srim.repository.XbrlContextRepository;
import org.yhj.srim.repository.XbrlDocumentRepository;
import org.yhj.srim.repository.XbrlFactRepository;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.FinMetricValue;
import org.yhj.srim.repository.entity.FinPeriod;
import org.yhj.srim.repository.entity.ShareClassType;
import org.yhj.srim.repository.entity.StockCode;
import org.yhj.srim.repository.entity.StockShareStatus;
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
class FinancialServiceXbrlRawBundleTest {

    @Autowired
    FinancialService financialService;

    @Autowired
    StockCodeRepository stockCodeRepository;

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    FinPeriodRepository finPeriodRepository;

    @Autowired
    FinMetricValueRepository finMetricValueRepository;

    @Autowired
    StockShareStatusRepository stockShareStatusRepository;

    @Autowired
    XbrlDocumentRepository xbrlDocumentRepository;

    @Autowired
    XbrlContextRepository xbrlContextRepository;

    @Autowired
    XbrlFactRepository xbrlFactRepository;

    @Test
    @DisplayName("FinancialService는 연간 XBRL 문서를 별도 경로로 조립해 raw bundle을 반환한다.")
    void loadXbrlRawBundle_returnsAnnualBundleFromSeparatePath() {
        Company company = saveCompany("TST001", "900001", "00126380");
        saveAnnualPeriod(company, 2024);
        saveAnnualPeriod(company, 2023);

        XbrlDocument current = saveDocument(company, 2024, "20250321002000", LocalDateTime.of(2025, 3, 21, 11, 0));
        saveAnnualFacts(current, "1014156314426", "21600800986", "443723178425");

        XbrlDocument previous = saveDocument(company, 2023, "20240321001000", LocalDateTime.of(2024, 3, 21, 10, 0));
        saveAnnualFacts(previous, "936525061005", "16472354950", "419651809690");

        FsRawBundle rawBundle = financialService.loadXbrlRawBundle(company.getCompanyId(), 2024, "CFS");

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
    @DisplayName("연간 기간이 없으면 XBRL raw bundle은 빈 맵으로 반환한다.")
    void loadXbrlRawBundle_withoutAnnualPeriod_returnsEmptyBundle() {
        Company company = saveCompany("TST002", "900002", "00126381");

        FsRawBundle rawBundle = financialService.loadXbrlRawBundle(company.getCompanyId(), 2024, "CFS");

        assertThat(rawBundle.curr()).isEmpty();
        assertThat(rawBundle.prev()).isEmpty();
    }

    @Test
    @DisplayName("연간 XBRL base metric을 fin_metric_value에 XBRL source로 저장한다.")
    void replaceAnnualBaseMetricsFromXbrl_persistsBaseMetrics() {
        Company company = saveCompany("TST003", "900003", "00126382");
        FinPeriod period = saveAnnualPeriod(company, 2024);
        saveAnnualPeriod(company, 2023);

        XbrlDocument current = saveDocument(company, 2024, "20250321002001", LocalDateTime.of(2025, 3, 21, 11, 30));
        saveAnnualFacts(current, "2000", "300", "500");

        int savedCount = financialService.replaceAnnualBaseMetricsFromXbrl(company.getCompanyId(), 2024, "CFS");

        assertThat(savedCount).isEqualTo(3);

        FinMetricValue sales = finMetricValueRepository
                .findByCompanyIdAndPeriodAndMetricCode(company.getCompanyId(), period, "SALES")
                .orElseThrow();
        FinMetricValue netIncome = finMetricValueRepository
                .findByCompanyIdAndPeriodAndMetricCode(company.getCompanyId(), period, "NET_INC")
                .orElseThrow();
        FinMetricValue equity = finMetricValueRepository
                .findByCompanyIdAndPeriodAndMetricCode(company.getCompanyId(), period, "TOTAL_EQUITY")
                .orElseThrow();

        assertThat(sales.getValueNum()).isEqualByComparingTo("2000");
        assertThat(netIncome.getValueNum()).isEqualByComparingTo("300");
        assertThat(equity.getValueNum()).isEqualByComparingTo("500");
        assertThat(sales.getSource()).isEqualTo("XBRL");
        assertThat(netIncome.getSource()).isEqualTo("XBRL");
        assertThat(equity.getSource()).isEqualTo("XBRL");
    }

    @Test
    @DisplayName("연간 XBRL derived metric을 fin_metric_value에 XBRL source로 저장한다.")
    void replaceAnnualDerivedMetricsFromXbrl_persistsDerivedMetrics() {
        Company company = saveCompany("TST004", "900004", "00126383");
        FinPeriod period = saveAnnualPeriod(company, 2024);
        saveAnnualPeriod(company, 2023);

        XbrlDocument current = saveDocument(company, 2024, "20250321002002", LocalDateTime.of(2025, 3, 21, 12, 0));
        saveAnnualFacts(current, "2000", "300", "500");

        XbrlDocument previous = saveDocument(company, 2023, "20240321001002", LocalDateTime.of(2024, 3, 21, 10, 0));
        saveAnnualFacts(previous, "1500", "200", "400");

        int savedCount = financialService.replaceAnnualDerivedMetricsFromXbrl(company.getCompanyId(), 2024, "CFS");

        assertThat(savedCount).isEqualTo(2);

        FinMetricValue netMargin = finMetricValueRepository
                .findByCompanyIdAndPeriodAndMetricCode(company.getCompanyId(), period, "NET_MARGIN")
                .orElseThrow();
        FinMetricValue roe = finMetricValueRepository
                .findByCompanyIdAndPeriodAndMetricCode(company.getCompanyId(), period, "ROE")
                .orElseThrow();

        assertThat(netMargin.getValueNum()).isEqualByComparingTo("15.000000");
        assertThat(roe.getValueNum()).isEqualByComparingTo("66.666667");
        assertThat(netMargin.getSource()).isEqualTo("XBRL");
        assertThat(roe.getSource()).isEqualTo("XBRL");
    }

    @Test
    @DisplayName("연간 XBRL per-share metric을 fin_metric_value에 XBRL source로 저장한다.")
    void replaceAnnualPerShareMetricsFromXbrl_persistsPerShareMetrics() {
        Company company = saveCompany("TST005", "900005", "00126384");
        FinPeriod period = saveAnnualPeriod(company, 2024);
        saveAnnualPeriod(company, 2023);
        saveShareStatus(company, 2024, 100L);

        XbrlDocument current = saveDocument(company, 2024, "20250321002003", LocalDateTime.of(2025, 3, 21, 12, 30));
        saveAnnualFacts(current, "2000", "300", "500");
        saveOwnerFacts(current, "200", "450", "50");

        int savedCount = financialService.replaceAnnualPerShareMetricsFromXbrl(company.getCompanyId(), 2024, "CFS");

        assertThat(savedCount).isEqualTo(1);

        FinMetricValue eps = finMetricValueRepository
                .findByCompanyIdAndPeriodAndMetricCode(company.getCompanyId(), period, "EPS")
                .orElseThrow();

        assertThat(eps.getValueNum()).isEqualByComparingTo("2.00");
        assertThat(eps.getSource()).isEqualTo("XBRL");
    }

    private Company saveCompany(String companyName, String ticker, String corpCode) {
        StockCode stockCode = stockCodeRepository.save(StockCode.builder()
                .companyName(companyName)
                .tickerKrx(ticker)
                .market("TEST")
                .dartCorpCode(corpCode)
                .build());

        return companyRepository.save(Company.builder()
                .stockCode(stockCode)
                .currency("KRW")
                .build());
    }

    private FinPeriod saveAnnualPeriod(Company company, int fiscalYear) {
        return finPeriodRepository.save(FinPeriod.builder()
                .company(company)
                .periodType("YEAR")
                .fiscalYear(fiscalYear)
                .fiscalQuarter(null)
                .periodStart(LocalDate.of(fiscalYear, 1, 1))
                .periodEnd(LocalDate.of(fiscalYear, 12, 31))
                .label(fiscalYear + "/12")
                .isEstimate(false)
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
                .contextRefHash(hashFor("ctx-duration-" + document.getBsnsYear()))
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
                .contextRefHash(hashFor("ctx-instant-" + document.getBsnsYear()))
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

    private void saveOwnerFacts(XbrlDocument document, String netIncomeOwner, String totalEquityOwner, String totalEquityNoncont) {
        XbrlContext durationContext = xbrlContextRepository.findAll().stream()
                .filter(context -> context.getDocument().getXbrlDocumentId().equals(document.getXbrlDocumentId()))
                .filter(context -> "duration".equals(context.getPeriodType()))
                .findFirst()
                .orElseThrow();

        XbrlContext instantContext = xbrlContextRepository.findAll().stream()
                .filter(context -> context.getDocument().getXbrlDocumentId().equals(document.getXbrlDocumentId()))
                .filter(context -> "instant".equals(context.getPeriodType()))
                .findFirst()
                .orElseThrow();

        xbrlFactRepository.save(XbrlFact.builder()
                .document(document)
                .context(durationContext)
                .contextRef(durationContext.getContextRef())
                .conceptQname("ifrs-full:ProfitLossAttributableToOwnersOfParent")
                .conceptLocalName("ProfitLossAttributableToOwnersOfParent")
                .labelKo("지배기업 소유주지분 순이익")
                .statementRole("income-statement")
                .unitRef("KRW")
                .decimals("0")
                .valueRaw(netIncomeOwner)
                .valueNumeric(new BigDecimal(netIncomeOwner))
                .isNil(false)
                .memberSignature(null)
                .orderHint(4)
                .build());

        xbrlFactRepository.save(XbrlFact.builder()
                .document(document)
                .context(instantContext)
                .contextRef(instantContext.getContextRef())
                .conceptQname("ifrs-full:EquityAttributableToOwnersOfParent")
                .conceptLocalName("EquityAttributableToOwnersOfParent")
                .labelKo("지배기업 소유주지분")
                .statementRole("balance-sheet")
                .unitRef("KRW")
                .decimals("0")
                .valueRaw(totalEquityOwner)
                .valueNumeric(new BigDecimal(totalEquityOwner))
                .isNil(false)
                .memberSignature(null)
                .orderHint(5)
                .build());

        xbrlFactRepository.save(XbrlFact.builder()
                .document(document)
                .context(instantContext)
                .contextRef(instantContext.getContextRef())
                .conceptQname("ifrs-full:NoncontrollingInterests")
                .conceptLocalName("NoncontrollingInterests")
                .labelKo("비지배지분")
                .statementRole("balance-sheet")
                .unitRef("KRW")
                .decimals("0")
                .valueRaw(totalEquityNoncont)
                .valueNumeric(new BigDecimal(totalEquityNoncont))
                .isNil(false)
                .memberSignature(null)
                .orderHint(6)
                .build());
    }

    private void saveShareStatus(Company company, int fiscalYear, long issuedShares) {
        stockShareStatusRepository.save(StockShareStatus.create(
                company,
                fiscalYear,
                LocalDate.of(fiscalYear, 12, 31),
                "보통주",
                ShareClassType.COMMON,
                issuedShares,
                issuedShares,
                0L,
                issuedShares
        ));
    }

    private String hashFor(String value) {
        return String.format("%-64s", value).replace(' ', 'x');
    }
}
