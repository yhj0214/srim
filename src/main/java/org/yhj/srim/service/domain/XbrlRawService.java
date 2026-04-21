package org.yhj.srim.service.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yhj.srim.client.DartReportType;
import org.yhj.srim.common.exception.CustomException;
import org.yhj.srim.common.exception.code.StockError;
import org.yhj.srim.repository.CompanyRepository;
import org.yhj.srim.repository.XbrlContextRepository;
import org.yhj.srim.repository.XbrlDocumentRepository;
import org.yhj.srim.repository.XbrlFactRepository;
import org.yhj.srim.repository.entity.Company;
import org.yhj.srim.repository.entity.XbrlContext;
import org.yhj.srim.repository.entity.XbrlDocument;
import org.yhj.srim.repository.entity.XbrlFact;
import org.yhj.srim.service.crawl.XbrlFinancialStatementCrawlingService;
import org.yhj.srim.service.crawl.dto.XbrlParsedContext;
import org.yhj.srim.service.crawl.dto.XbrlParsedFact;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class XbrlRawService {

    private final CompanyRepository companyRepository;
    private final XbrlDocumentRepository xbrlDocumentRepository;
    private final XbrlContextRepository xbrlContextRepository;
    private final XbrlFactRepository xbrlFactRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.xbrl.storage-dir:/tmp/srim/xbrl}")
    private String storageDir;

    @Value("${app.xbrl.parse-version:v1}")
    private String parseVersion;

    @Transactional(readOnly = true)
    public Optional<Long> findStoredDocumentId(String rceptNo, String reprtCode, String fsDiv) {
        return xbrlDocumentRepository.findByRceptNoAndReprtCodeAndFsDiv(rceptNo, reprtCode, fsDiv)
                .map(XbrlDocument::getXbrlDocumentId);
    }

    @Transactional
    public Long saveFinancialStatementsXbrl(String corpCode,
                                           Long companyId,
                                           XbrlFinancialStatementCrawlingService.XbrlRawBatch batch) {
        Optional<XbrlDocument> existing = xbrlDocumentRepository.findByRceptNoAndReprtCodeAndFsDiv(
                batch.rceptNo(), batch.reprtCode(), batch.fsDiv()
        );
        if (existing.isPresent()) {
            return existing.get().getXbrlDocumentId();
        }

        Path archivePath = writeArchive(corpCode, batch.bsnsYear(), batch.rceptNo(), batch.fsDiv(), batch.archiveBytes());
        Company company = companyId == null ? null : companyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(StockError.COMPANY_NOT_FOUND, "companyId=" + companyId));

        XbrlDocument document = xbrlDocumentRepository.save(XbrlDocument.builder()
                .corpCode(corpCode)
                .company(company)
                .rceptNo(batch.rceptNo())
                .reprtCode(batch.reprtCode())
                .bsnsYear(batch.bsnsYear())
                .fsDiv(batch.fsDiv())
                .reportTp(batch.reportTypeLabel())
                .sourceUrl(batch.sourceUrl())
                .localPath(archivePath.toString())
                .taxonomyVersion(batch.parseResult().taxonomyVersion())
                .parseVersion(parseVersion)
                .parsedAt(LocalDateTime.now())
                .build());

        Map<String, XbrlContext> contextByRef = saveContexts(document, batch.parseResult().contexts());
        saveFacts(document, contextByRef, batch.parseResult().facts());

        log.info("XBRL raw 저장 완료 companyId={}, rceptNo={}, contexts={}, facts={}",
                companyId, batch.rceptNo(), contextByRef.size(), batch.parseResult().facts().size());

        return document.getXbrlDocumentId();
    }

    private Path writeArchive(String corpCode, int bsnsYear, String rceptNo, String fsDiv, byte[] archiveBytes) {
        try {
            Path directory = Path.of(storageDir, corpCode, String.valueOf(bsnsYear));
            Files.createDirectories(directory);
            Path archivePath = directory.resolve(rceptNo + "_" + fsDiv + ".zip");
            Files.write(archivePath, archiveBytes);
            return archivePath;
        } catch (IOException e) {
            throw new IllegalStateException("XBRL 원문 저장에 실패했습니다.", e);
        }
    }

    private Map<String, XbrlContext> saveContexts(XbrlDocument document, List<XbrlParsedContext> parsedContexts) {
        Map<String, XbrlContext> contextByRef = new HashMap<>();

        for (XbrlParsedContext parsedContext : parsedContexts) {
            XbrlContext context = xbrlContextRepository.save(XbrlContext.builder()
                    .document(document)
                    .contextRef(parsedContext.contextRef())
                    .contextRefHash(sha256Hex(parsedContext.contextRef()))
                    .entityIdentifier(parsedContext.entityIdentifier())
                    .periodType(parsedContext.periodType())
                    .periodStart(parsedContext.periodStart())
                    .periodEnd(parsedContext.periodEnd())
                    .instantDate(parsedContext.instantDate())
                    .dimensionsJson(toJson(parsedContext.dimensions()))
                    .memberSignature(parsedContext.memberSignature())
                    .build());
            contextByRef.put(parsedContext.contextRef(), context);
        }

        return contextByRef;
    }

    private void saveFacts(XbrlDocument document,
                           Map<String, XbrlContext> contextByRef,
                           List<XbrlParsedFact> parsedFacts) {
        List<XbrlFact> facts = parsedFacts.stream()
                .map(parsedFact -> XbrlFact.builder()
                        .document(document)
                        .context(contextByRef.get(parsedFact.contextRef()))
                        .contextRef(parsedFact.contextRef())
                        .conceptQname(parsedFact.conceptQname())
                        .conceptLocalName(parsedFact.conceptLocalName())
                        .labelKo(parsedFact.labelKo())
                        .statementRole(parsedFact.statementRole())
                        .unitRef(parsedFact.unitRef())
                        .decimals(parsedFact.decimals())
                        .valueRaw(parsedFact.valueRaw())
                        .valueNumeric(parsedFact.valueNumeric())
                        .isNil(parsedFact.isNil())
                        .memberSignature(parsedFact.memberSignature())
                        .orderHint(parsedFact.orderHint())
                        .build())
                .toList();

        xbrlFactRepository.saveAll(facts);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("XBRL dimension JSON 직렬화에 실패했습니다.", e);
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("XBRL context hash 생성에 실패했습니다.", e);
        }
    }
}
