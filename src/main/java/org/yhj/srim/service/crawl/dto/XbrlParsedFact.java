package org.yhj.srim.service.crawl.dto;

import java.math.BigDecimal;

public record XbrlParsedFact(
        String contextRef,
        String conceptQname,
        String conceptLocalName,
        String labelKo,
        String statementRole,
        String unitRef,
        String decimals,
        String valueRaw,
        BigDecimal valueNumeric,
        boolean isNil,
        String memberSignature,
        Integer orderHint
) {
}
