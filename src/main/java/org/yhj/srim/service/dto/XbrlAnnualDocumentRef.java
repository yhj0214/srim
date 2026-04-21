package org.yhj.srim.service.dto;

public record XbrlAnnualDocumentRef(
        String corpCode,
        String rceptNo,
        int fiscalYear,
        String fsDiv
) {
}
