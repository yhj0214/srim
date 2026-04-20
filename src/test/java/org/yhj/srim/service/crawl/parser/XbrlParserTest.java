package org.yhj.srim.service.crawl.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yhj.srim.service.crawl.dto.XbrlParseResult;
import org.yhj.srim.service.crawl.dto.XbrlParsedContext;
import org.yhj.srim.service.crawl.dto.XbrlParsedFact;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class XbrlParserTest {

    private final XbrlParser xbrlParser = new XbrlParser();

    @Test
    @DisplayName("XBRL zip에서 context와 fact를 추출한다.")
    void parse_extractsContextsAndFacts() throws Exception {
        byte[] archiveBytes = buildArchive("""
                <?xml version="1.0" encoding="UTF-8"?>
                <xbrli:xbrl xmlns:xbrli="http://www.xbrl.org/2003/instance"
                            xmlns:xbrldi="http://xbrl.org/2006/xbrldi"
                            xmlns:ifrs-full="http://xbrl.ifrs.org/taxonomy/2024-03-27/ifrs-full">
                    <xbrli:context id="ctx_duration">
                        <xbrli:entity>
                            <xbrli:identifier scheme="http://dart.fss.or.kr">00126380</xbrli:identifier>
                        </xbrli:entity>
                        <xbrli:period>
                            <xbrli:startDate>2024-01-01</xbrli:startDate>
                            <xbrli:endDate>2024-12-31</xbrli:endDate>
                        </xbrli:period>
                        <xbrli:scenario>
                            <xbrldi:explicitMember dimension="dart:ConsolidatedOrSeparateFinancialStatementsAxis">
                                dart:ConsolidatedFinancialStatementsMember
                            </xbrldi:explicitMember>
                        </xbrli:scenario>
                    </xbrli:context>
                    <xbrli:context id="ctx_instant">
                        <xbrli:entity>
                            <xbrli:identifier scheme="http://dart.fss.or.kr">00126380</xbrli:identifier>
                        </xbrli:entity>
                        <xbrli:period>
                            <xbrli:instant>2024-12-31</xbrli:instant>
                        </xbrli:period>
                    </xbrli:context>
                    <ifrs-full:Revenue contextRef="ctx_duration" unitRef="KRW" decimals="0">1014156314426</ifrs-full:Revenue>
                    <ifrs-full:Equity contextRef="ctx_instant" unitRef="KRW" decimals="0">443723178425</ifrs-full:Equity>
                </xbrli:xbrl>
                """);

        XbrlParseResult result = xbrlParser.parse(archiveBytes);

        assertThat(result.contexts()).hasSize(2);
        assertThat(result.facts()).hasSize(2);

        XbrlParsedContext durationContext = result.contexts().stream()
                .filter(context -> "ctx_duration".equals(context.contextRef()))
                .findFirst()
                .orElseThrow();
        assertThat(durationContext.periodType()).isEqualTo("duration");
        assertThat(durationContext.memberSignature())
                .isEqualTo("dart:ConsolidatedOrSeparateFinancialStatementsAxis=dart:ConsolidatedFinancialStatementsMember");

        XbrlParsedFact revenueFact = result.facts().stream()
                .filter(fact -> "Revenue".equals(fact.conceptLocalName()))
                .findFirst()
                .orElseThrow();
        assertThat(revenueFact.contextRef()).isEqualTo("ctx_duration");
        assertThat(revenueFact.valueRaw()).isEqualTo("1014156314426");
        assertThat(revenueFact.valueNumeric()).isNotNull();
    }

    private byte[] buildArchive(String xml) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            zipOutputStream.putNextEntry(new ZipEntry("sample-instance.xml"));
            zipOutputStream.write(xml.getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
        }
        return outputStream.toByteArray();
    }
}
