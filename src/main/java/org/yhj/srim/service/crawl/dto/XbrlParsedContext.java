package org.yhj.srim.service.crawl.dto;

import java.time.LocalDate;
import java.util.List;

public record XbrlParsedContext(
        String contextRef,
        String entityIdentifier,
        String periodType,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate instantDate,
        List<XbrlDimension> dimensions,
        String memberSignature
) {
}
