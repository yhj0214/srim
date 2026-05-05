package org.yhj.srim.service.application.dto;

import org.yhj.srim.client.DartReportType;

public record CollectXbrlRawCommand(
        Long companyId,
        String corpCode,
        String rceptNo,
        int bsnsYear,
        DartReportType reportType,
        String fsDiv
) {
}
