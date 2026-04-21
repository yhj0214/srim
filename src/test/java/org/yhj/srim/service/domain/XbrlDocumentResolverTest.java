package org.yhj.srim.service.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.client.DartReportType;
import org.yhj.srim.repository.XbrlContextRepository;
import org.yhj.srim.repository.XbrlDocumentRepository;
import org.yhj.srim.repository.XbrlFactRepository;
import org.yhj.srim.repository.entity.XbrlContext;
import org.yhj.srim.repository.entity.XbrlDocument;
import org.yhj.srim.repository.entity.XbrlFact;
import org.yhj.srim.service.dto.XbrlResolvedBundles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class XbrlDocumentResolverTest {

    @Autowired
    XbrlDocumentResolver xbrlDocumentResolver;

    @Autowired
    XbrlDocumentRepository xbrlDocumentRepository;

    @Autowired
    XbrlContextRepository xbrlContextRepository;

    @Autowired
    XbrlFactRepository xbrlFactRepository;

    @Test
    @DisplayName("같은 연도 문서가 여러 개면 최신 parsedAt 문서를 current로 선택하고 전년도 문서를 previous로 묶는다.")
    void resolveBundles_selectsLatestCurrentAndPreviousDocuments() {
        Long companyId = 100L;

        XbrlDocument currentOlder = saveDocument(companyId, 2024, "20250321001000", LocalDateTime.of(2025, 3, 21, 10, 0));
        saveContextAndRevenueFact(currentOlder, "ctx-old", "900");

        XbrlDocument currentLatest = saveDocument(companyId, 2024, "20250321002000", LocalDateTime.of(2025, 3, 21, 11, 0));
        saveContextAndRevenueFact(currentLatest, "ctx-latest", "1000");

        XbrlDocument previous = saveDocument(companyId, 2023, "20240321001000", LocalDateTime.of(2024, 3, 21, 10, 0));
        saveContextAndRevenueFact(previous, "ctx-prev", "800");

        XbrlResolvedBundles resolved = xbrlDocumentResolver.resolveBundles(
                companyId,
                2024,
                DartReportType.ANNUAL,
                "CFS"
        );

        assertThat(resolved.current()).isNotNull();
        assertThat(resolved.current().document().xbrlDocumentId()).isEqualTo(currentLatest.getXbrlDocumentId());
        assertThat(resolved.current().facts()).extracting(fact -> fact.valueRaw()).containsExactly("1000");

        assertThat(resolved.previous()).isNotNull();
        assertThat(resolved.previous().document().xbrlDocumentId()).isEqualTo(previous.getXbrlDocumentId());
        assertThat(resolved.previous().facts()).extracting(fact -> fact.valueRaw()).containsExactly("800");
    }

    @Test
    @DisplayName("전년도 문서가 없으면 previous는 null이다.")
    void resolveBundles_withoutPreviousDocument_returnsNullPrevious() {
        Long companyId = 101L;

        XbrlDocument current = saveDocument(companyId, 2024, "20250321003000", LocalDateTime.of(2025, 3, 21, 12, 0));
        saveContextAndRevenueFact(current, "ctx-current-only", "1100");

        XbrlResolvedBundles resolved = xbrlDocumentResolver.resolveBundles(
                companyId,
                2024,
                DartReportType.ANNUAL,
                "CFS"
        );

        assertThat(resolved.current()).isNotNull();
        assertThat(resolved.current().document().xbrlDocumentId()).isEqualTo(current.getXbrlDocumentId());
        assertThat(resolved.previous()).isNull();
    }

    private XbrlDocument saveDocument(Long companyId, int year, String rceptNo, LocalDateTime parsedAt) {
        return xbrlDocumentRepository.save(XbrlDocument.builder()
                .corpCode("00126380")
                .companyId(companyId)
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

    private void saveContextAndRevenueFact(XbrlDocument document, String contextRef, String revenue) {
        XbrlContext context = xbrlContextRepository.save(XbrlContext.builder()
                .document(document)
                .contextRef(contextRef)
                .contextRefHash(String.format("%-64s", contextRef).replace(' ', 'x'))
                .entityIdentifier("00126380")
                .periodType("duration")
                .periodStart(LocalDate.of(document.getBsnsYear(), 1, 1))
                .periodEnd(LocalDate.of(document.getBsnsYear(), 12, 31))
                .instantDate(null)
                .dimensionsJson("[]")
                .memberSignature(null)
                .build());

        xbrlFactRepository.save(XbrlFact.builder()
                .document(document)
                .context(context)
                .contextRef(contextRef)
                .conceptQname("ifrs-full:Revenue")
                .conceptLocalName("Revenue")
                .labelKo("매출액")
                .statementRole("income-statement")
                .unitRef("KRW")
                .decimals("0")
                .valueRaw(revenue)
                .valueNumeric(new BigDecimal(revenue))
                .isNil(false)
                .memberSignature(null)
                .orderHint(1)
                .build());
    }
}
