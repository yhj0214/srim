package org.yhj.srim.service.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.repository.XbrlContextRepository;
import org.yhj.srim.repository.XbrlDocumentRepository;
import org.yhj.srim.repository.XbrlFactRepository;
import org.yhj.srim.repository.entity.XbrlContext;
import org.yhj.srim.repository.entity.XbrlDocument;
import org.yhj.srim.repository.entity.XbrlFact;
import org.yhj.srim.service.dto.XbrlRawBundle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class XbrlRawReaderServiceTest {

    @Autowired
    XbrlRawReaderService xbrlRawReaderService;

    @Autowired
    XbrlDocumentRepository xbrlDocumentRepository;

    @Autowired
    XbrlContextRepository xbrlContextRepository;

    @Autowired
    XbrlFactRepository xbrlFactRepository;

    @Test
    @DisplayName("문서 ID로 XBRL raw bundle을 읽어온다.")
    void getDocumentBundle_readsDocumentContextsAndFacts() {
        XbrlDocument document = xbrlDocumentRepository.save(XbrlDocument.builder()
                .corpCode("00126380")
                .companyId(null)
                .rceptNo("20250321001234")
                .reprtCode("11011")
                .bsnsYear(2024)
                .fsDiv("CFS")
                .reportTp("연간")
                .sourceUrl("https://example.com/xbrl.zip")
                .localPath("/tmp/test-xbrl.zip")
                .taxonomyVersion("https://taxonomy.example/ifrs-full.xsd")
                .parseVersion("test-v1")
                .parsedAt(LocalDateTime.now())
                .build());

        XbrlContext durationContext = xbrlContextRepository.save(XbrlContext.builder()
                .document(document)
                .contextRef("ctx-duration")
                .contextRefHash("a".repeat(64))
                .entityIdentifier("00126380")
                .periodType("duration")
                .periodStart(LocalDate.of(2024, 1, 1))
                .periodEnd(LocalDate.of(2024, 12, 31))
                .instantDate(null)
                .dimensionsJson("""
                        [{"axis":"dart:ConsolidatedOrSeparateFinancialStatementsAxis","member":"dart:ConsolidatedFinancialStatementsMember","typed":false}]
                        """.trim())
                .memberSignature("dart:ConsolidatedOrSeparateFinancialStatementsAxis=dart:ConsolidatedFinancialStatementsMember")
                .build());

        XbrlContext instantContext = xbrlContextRepository.save(XbrlContext.builder()
                .document(document)
                .contextRef("ctx-instant")
                .contextRefHash("b".repeat(64))
                .entityIdentifier("00126380")
                .periodType("instant")
                .periodStart(null)
                .periodEnd(null)
                .instantDate(LocalDate.of(2024, 12, 31))
                .dimensionsJson("[]")
                .memberSignature(null)
                .build());

        xbrlFactRepository.save(XbrlFact.builder()
                .document(document)
                .context(durationContext)
                .contextRef("ctx-duration")
                .conceptQname("ifrs-full:Revenue")
                .conceptLocalName("Revenue")
                .labelKo("매출액")
                .statementRole("income-statement")
                .unitRef("KRW")
                .decimals("0")
                .valueRaw("1014156314426")
                .valueNumeric(new BigDecimal("1014156314426"))
                .isNil(false)
                .memberSignature(durationContext.getMemberSignature())
                .orderHint(2)
                .build());

        xbrlFactRepository.save(XbrlFact.builder()
                .document(document)
                .context(instantContext)
                .contextRef("ctx-instant")
                .conceptQname("ifrs-full:Equity")
                .conceptLocalName("Equity")
                .labelKo("자본총계")
                .statementRole("balance-sheet")
                .unitRef("KRW")
                .decimals("0")
                .valueRaw("443723178425")
                .valueNumeric(new BigDecimal("443723178425"))
                .isNil(false)
                .memberSignature(null)
                .orderHint(1)
                .build());

        XbrlRawBundle bundle = xbrlRawReaderService.getDocumentBundle(document.getXbrlDocumentId());

        assertThat(bundle.document().xbrlDocumentId()).isEqualTo(document.getXbrlDocumentId());
        assertThat(bundle.document().corpCode()).isEqualTo("00126380");
        assertThat(bundle.contexts()).hasSize(2);
        assertThat(bundle.facts()).hasSize(2);
        assertThat(bundle.contexts())
                .extracting(context -> context.contextRef())
                .containsExactly("ctx-duration", "ctx-instant");
        assertThat(bundle.facts())
                .extracting(fact -> fact.conceptQname())
                .containsExactly("ifrs-full:Equity", "ifrs-full:Revenue");
        assertThat(bundle.facts().get(0).xbrlContextId()).isEqualTo(instantContext.getXbrlContextId());
        assertThat(bundle.facts().get(1).xbrlContextId()).isEqualTo(durationContext.getXbrlContextId());
    }
}
