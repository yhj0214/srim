package org.yhj.srim.service.dto;

import org.yhj.srim.repository.entity.XbrlContext;

import java.time.LocalDate;

public record XbrlContextView(
        Long xbrlContextId,
        String contextRef,
        String contextRefHash,
        String entityIdentifier,
        String periodType,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate instantDate,
        String dimensionsJson,
        String memberSignature
) {
    public static XbrlContextView of(XbrlContext context) {
        return new XbrlContextView(
                context.getXbrlContextId(),
                context.getContextRef(),
                context.getContextRefHash(),
                context.getEntityIdentifier(),
                context.getPeriodType(),
                context.getPeriodStart(),
                context.getPeriodEnd(),
                context.getInstantDate(),
                context.getDimensionsJson(),
                context.getMemberSignature()
        );
    }
}
