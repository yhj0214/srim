package org.yhj.srim.service.dto;

import org.yhj.srim.repository.entity.XbrlDocument;

import java.time.LocalDateTime;

public record XbrlDocumentView(
        Long xbrlDocumentId,
        String corpCode,
        Long companyId,
        String rceptNo,
        String reprtCode,
        Integer bsnsYear,
        String fsDiv,
        String reportTp,
        String sourceUrl,
        String localPath,
        String taxonomyVersion,
        String parseVersion,
        LocalDateTime parsedAt
) {
    public static XbrlDocumentView of(XbrlDocument document) {
        return new XbrlDocumentView(
                document.getXbrlDocumentId(),
                document.getCorpCode(),
                document.getCompanyId(),
                document.getRceptNo(),
                document.getReprtCode(),
                document.getBsnsYear(),
                document.getFsDiv(),
                document.getReportTp(),
                document.getSourceUrl(),
                document.getLocalPath(),
                document.getTaxonomyVersion(),
                document.getParseVersion(),
                document.getParsedAt()
        );
    }
}
