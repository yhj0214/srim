package org.yhj.srim.service.dto;

import org.yhj.srim.repository.entity.XbrlFact;

import java.math.BigDecimal;

public record XbrlFactView(
        Long xbrlFactId,
        Long xbrlContextId,
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
    public static XbrlFactView of(XbrlFact fact) {
        return new XbrlFactView(
                fact.getXbrlFactId(),
                fact.getContext() == null ? null : fact.getContext().getXbrlContextId(),
                fact.getContextRef(),
                fact.getConceptQname(),
                fact.getConceptLocalName(),
                fact.getLabelKo(),
                fact.getStatementRole(),
                fact.getUnitRef(),
                fact.getDecimals(),
                fact.getValueRaw(),
                fact.getValueNumeric(),
                fact.isNil(),
                fact.getMemberSignature(),
                fact.getOrderHint()
        );
    }
}
